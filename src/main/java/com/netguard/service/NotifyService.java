package com.netguard.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.netguard.common.enums.NotifyChannelEnum;
import com.netguard.common.enums.NotifyStatusEnum;
import com.netguard.entity.AlarmRecord;
import com.netguard.entity.NotifyRecord;
import com.netguard.entity.SysUser;
import com.netguard.mapper.AlarmRecordMapper;
import com.netguard.mapper.NotifyRecordMapper;
import com.netguard.mapper.SysUserMapper;
import com.netguard.mq.AlarmMessage;
import com.netguard.query.NotifyQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知服务：把告警消息落成页面消息与邮件，并维护已读状态。
 *
 * <p>由 RabbitMQ 消费者异步调用，保证邮件发送慢不会阻塞巡检线程；
 * 未开启 RabbitMQ 时由本地发布器同步调用，行为保持一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotifyRecordMapper notifyRecordMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final MailService mailService;

    /**
     * 处理一条告警通知：写页面消息、发邮件、回写告警的通知标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void notifyAlarm(AlarmMessage message) {
        if (message == null || message.getAlarmId() == null) {
            log.warn("收到无效告警消息，已忽略");
            return;
        }
        boolean anySuccess = false;
        String title = buildTitle(message);

        if (message.isNotifyPage()) {
            NotifyRecord page = new NotifyRecord();
            page.setAlarmId(message.getAlarmId());
            page.setChannel(NotifyChannelEnum.PAGE);
            page.setReceiver("全体运维人员");
            page.setTitle(title);
            page.setContent(message.getContent());
            page.setStatus(NotifyStatusEnum.SUCCESS);
            page.setSendTime(LocalDateTime.now());
            page.setReadFlag(0);
            notifyRecordMapper.insert(page);
            anySuccess = true;
        }

        if (message.isNotifyMail()) {
            List<String> recipients = resolveRecipients(message.getMailReceivers());
            NotifyRecord mail = new NotifyRecord();
            mail.setAlarmId(message.getAlarmId());
            mail.setChannel(NotifyChannelEnum.MAIL);
            mail.setReceiver(String.join(",", recipients));
            mail.setTitle(title);
            mail.setContent(message.getContent());
            mail.setSendTime(LocalDateTime.now());
            if (recipients.isEmpty()) {
                mail.setStatus(NotifyStatusEnum.SKIPPED);
                mail.setErrorMsg("未配置收件人邮箱");
            } else {
                boolean sent = mailService.sendHtml(recipients, title, buildMailHtml(message));
                mail.setStatus(sent ? NotifyStatusEnum.SUCCESS : NotifyStatusEnum.FAILED);
                mail.setErrorMsg(sent ? null : "邮件通道未开启或发送失败，请检查 SMTP 配置");
                anySuccess = anySuccess || sent;
            }
            notifyRecordMapper.insert(mail);
        }

        if (anySuccess) {
            AlarmRecord update = new AlarmRecord();
            update.setId(message.getAlarmId());
            update.setNotifySent(1);
            alarmRecordMapper.updateById(update);
        }
    }

    /**
     * 分页查询通知记录。
     */
    public IPage<NotifyRecord> page(NotifyQuery query) {
        Page<NotifyRecord> page = new Page<>(query.getCurrent(), query.getLimit());
        NotifyChannelEnum channel = parseChannel(query.getChannel());
        return notifyRecordMapper.selectPage(page, Wrappers.<NotifyRecord>lambdaQuery()
                .eq(channel != null, NotifyRecord::getChannel, channel)
                .eq(query.getReadFlag() != null, NotifyRecord::getReadFlag, query.getReadFlag())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(NotifyRecord::getTitle, query.getKeyword())
                        .or()
                        .like(NotifyRecord::getContent, query.getKeyword()))
                .orderByDesc(NotifyRecord::getId));
    }

    /**
     * 未读页面消息数量（含邮件失败提醒）。
     */
    public long unreadCount() {
        Long count = notifyRecordMapper.selectCount(Wrappers.<NotifyRecord>lambdaQuery()
                .eq(NotifyRecord::getReadFlag, 0));
        return count == null ? 0L : count;
    }

    /**
     * 最近的页面消息，供铃铛下拉展示。
     */
    public List<NotifyRecord> recent(int limit) {
        Page<NotifyRecord> page = new Page<>(1, Math.max(1, Math.min(limit, 20)));
        return notifyRecordMapper.selectPage(page, Wrappers.<NotifyRecord>lambdaQuery()
                .eq(NotifyRecord::getChannel, NotifyChannelEnum.PAGE)
                .orderByDesc(NotifyRecord::getId)).getRecords();
    }

    /**
     * 单条标记已读。
     */
    public void markRead(Long id) {
        notifyRecordMapper.update(null, new LambdaUpdateWrapper<NotifyRecord>()
                .eq(NotifyRecord::getId, id)
                .set(NotifyRecord::getReadFlag, 1));
    }

    /**
     * 全部标记已读。
     */
    public void markAllRead() {
        notifyRecordMapper.update(null, new LambdaUpdateWrapper<NotifyRecord>()
                .eq(NotifyRecord::getReadFlag, 0)
                .set(NotifyRecord::getReadFlag, 1));
    }

    /**
     * 安全解析渠道枚举，非法值返回 null（视为不过滤）。
     */
    private NotifyChannelEnum parseChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return null;
        }
        try {
            return NotifyChannelEnum.valueOf(channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String buildTitle(AlarmMessage message) {
        boolean urgent = "URGENT".equalsIgnoreCase(message.getAlarmLevel());
        boolean recover = "RECOVER".equalsIgnoreCase(message.getAlarmType());
        if (recover) {
            return "设备恢复通知：" + message.getDeviceName();
        }
        return (urgent ? "【紧急】" : "【普通】") + "设备离线告警：" + message.getDeviceName();
    }

    /**
     * 解析收件人：规则中配置优先；未配置时回退到全部启用用户的邮箱。
     */
    private List<String> resolveRecipients(String configured) {
        List<String> result = new ArrayList<>();
        if (StringUtils.hasText(configured)) {
            result.addAll(Arrays.stream(configured.split("[,;\\s]+"))
                    .map(String::trim)
                    .filter(s -> s.contains("@"))
                    .distinct()
                    .collect(Collectors.toList()));
            return result;
        }
        List<SysUser> users = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, 1)
                .isNotNull(SysUser::getEmail));
        for (SysUser user : users) {
            if (StringUtils.hasText(user.getEmail()) && user.getEmail().contains("@")) {
                result.add(user.getEmail().trim());
            }
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 生成 HTML 告警邮件正文。
     */
    private String buildMailHtml(AlarmMessage message) {
        boolean recover = "RECOVER".equalsIgnoreCase(message.getAlarmType());
        String color = recover ? "#16a34a" : ("URGENT".equalsIgnoreCase(message.getAlarmLevel()) ? "#dc2626" : "#ea580c");
        String levelText = "URGENT".equalsIgnoreCase(message.getAlarmLevel()) ? "紧急" : "普通";
        return "<div style=\"font-family:'Microsoft YaHei',Arial,sans-serif;max-width:640px;margin:0 auto;"
                + "border:1px solid #e5e7eb;border-radius:8px;overflow:hidden\">"
                + "<div style=\"background:" + color + ";color:#fff;padding:16px 24px;font-size:18px;font-weight:600\">"
                + (recover ? "设备恢复通知" : "网络设备故障告警") + "</div>"
                + "<div style=\"padding:24px;color:#374151;line-height:1.8;font-size:14px\">"
                + "<p>您好，监控平台检测到以下设备状态变化，请及时处理：</p>"
                + "<table style=\"width:100%;border-collapse:collapse;font-size:14px\">"
                + row("设备名称", message.getDeviceName())
                + row("设备 IP", message.getDeviceIp())
                + row("设备类型", message.getDeviceTypeName())
                + row("告警级别", levelText)
                + row("发生时间", message.getAlarmTime() == null ? "-" : message.getAlarmTime().toString().replace('T', ' '))
                + row("详情说明", message.getContent())
                + "</table>"
                + "<p style=\"color:#6b7280;font-size:12px;margin-top:24px\">本邮件由 NetGuard 网络故障告警监控平台自动发送，请勿直接回复。</p>"
                + "</div></div>";
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:8px 12px;background:#f9fafb;border:1px solid #e5e7eb;width:110px;color:#6b7280\">"
                + label + "</td><td style=\"padding:8px 12px;border:1px solid #e5e7eb\">"
                + (value == null ? "-" : value) + "</td></tr>";
    }
}
