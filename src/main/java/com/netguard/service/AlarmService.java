package com.netguard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.netguard.common.constant.CacheKeys;
import com.netguard.common.enums.AlarmLevelEnum;
import com.netguard.common.enums.AlarmStatusEnum;
import com.netguard.common.enums.AlarmTypeEnum;
import com.netguard.common.enums.DeviceStatusEnum;
import com.netguard.dto.AlarmConfirmForm;
import com.netguard.entity.AlarmRecord;
import com.netguard.entity.AlarmRule;
import com.netguard.entity.Device;
import com.netguard.entity.DeviceType;
import com.netguard.exception.BusinessException;
import com.netguard.mapper.AlarmRecordMapper;
import com.netguard.mapper.DeviceMapper;
import com.netguard.mapper.DeviceTypeMapper;
import com.netguard.mq.AlarmEventPublisher;
import com.netguard.mq.AlarmMessage;
import com.netguard.query.AlarmQuery;
import com.netguard.security.SecurityUtils;
import com.netguard.utils.DurationUtils;
import com.netguard.vo.AlarmRecordVO;
import com.netguard.vo.CountItemVO;
import com.netguard.vo.DeviceFaultRankVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 告警服务：告警的产生、发送、确认、恢复与统计查询。
 *
 * <p>告警状态流转：PENDING（待确认）→ CONFIRMED（已确认）→ RECOVERED（已恢复）；
 * 设备恢复在线时自动把未关闭的离线告警置为 RECOVERED 并单独生成一条 RECOVER 记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRecordMapper alarmRecordMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final AlarmEventPublisher eventPublisher;
    private final CacheService cacheService;

    /**
     * 设备离线时生成告警并发起异步通知。
     *
     * @param device         离线设备
     * @param deviceTypeName 设备类型名称（用于邮件内容）
     * @param rule           当前告警规则
     * @return 新建的告警记录
     */
    @Transactional(rollbackFor = Exception.class)
    public AlarmRecord createOfflineAlarm(Device device, String deviceTypeName, AlarmRule rule) {
        boolean urgent = device.getCoreFlag() != null && device.getCoreFlag() == 1
                && (rule.getUrgentEnabled() == null || rule.getUrgentEnabled() == 1);
        LocalDateTime now = LocalDateTime.now();

        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(device.getId());
        record.setDeviceName(device.getDeviceName());
        record.setDeviceIp(device.getIpAddress());
        record.setAlarmType(AlarmTypeEnum.OFFLINE);
        record.setAlarmLevel(urgent ? AlarmLevelEnum.URGENT : AlarmLevelEnum.NORMAL);
        record.setAlarmStatus(AlarmStatusEnum.PENDING);
        record.setAlarmTime(now);
        record.setNotifySent(0);
        record.setRemark("自动巡检判定设备离线");
        alarmRecordMapper.insert(record);

        String content = String.format("设备 %s（%s）于 %s 探测失败，已判定离线，请及时处理。",
                device.getDeviceName(), device.getIpAddress(),
                now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        publish(record, deviceTypeName, rule, content);
        cacheService.delete(CacheKeys.DASHBOARD_SUMMARY);
        cacheService.delete(CacheKeys.ALARM_PENDING_COUNT);
        log.warn("生成离线告警：设备={}({}) 级别={}", device.getDeviceName(), device.getIpAddress(), record.getAlarmLevel());
        return record;
    }

    /**
     * 设备恢复在线：关闭未完成的离线告警，并生成恢复通知。
     *
     * @param device         已恢复的设备
     * @param deviceTypeName 设备类型名称
     * @param rule           当前告警规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void createRecoverAlarm(Device device, String deviceTypeName, AlarmRule rule) {
        List<AlarmRecord> opens = alarmRecordMapper.selectList(Wrappers.<AlarmRecord>lambdaQuery()
                .eq(AlarmRecord::getDeviceId, device.getId())
                .eq(AlarmRecord::getAlarmType, AlarmTypeEnum.OFFLINE)
                .in(AlarmRecord::getAlarmStatus, Arrays.asList(AlarmStatusEnum.PENDING, AlarmStatusEnum.CONFIRMED))
                .orderByDesc(AlarmRecord::getAlarmTime));
        if (opens.isEmpty()) {
            log.info("设备 {} 恢复在线，但未找到未关闭的离线告警，跳过恢复通知", device.getDeviceName());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AlarmRecord offline = opens.get(0);
        long minutes = offline.getAlarmTime() == null ? 0L : Math.max(0L, Duration.between(offline.getAlarmTime(), now).toMinutes());

        AlarmRecord close = new AlarmRecord();
        close.setId(offline.getId());
        close.setAlarmStatus(AlarmStatusEnum.RECOVERED);
        close.setRecoverTime(now);
        close.setDurationMinutes(minutes);
        alarmRecordMapper.updateById(close);

        AlarmRecord recover = new AlarmRecord();
        recover.setDeviceId(device.getId());
        recover.setDeviceName(device.getDeviceName());
        recover.setDeviceIp(device.getIpAddress());
        recover.setAlarmType(AlarmTypeEnum.RECOVER);
        recover.setAlarmLevel(offline.getAlarmLevel());
        recover.setAlarmStatus(AlarmStatusEnum.CLOSED);
        recover.setAlarmTime(now);
        recover.setRecoverTime(now);
        recover.setDurationMinutes(minutes);
        recover.setNotifySent(0);
        recover.setRemark("设备恢复在线，故障持续 " + DurationUtils.format(minutes));
        alarmRecordMapper.insert(recover);

        String content = String.format("设备 %s（%s）已恢复在线，本次故障持续 %s。",
                device.getDeviceName(), device.getIpAddress(), DurationUtils.format(minutes));
        publish(recover, deviceTypeName, rule, content);
        cacheService.delete(CacheKeys.DASHBOARD_SUMMARY);
        log.info("设备 {} 恢复在线，故障持续 {} 分钟", device.getDeviceName(), minutes);
    }

    /**
     * 运维人员确认告警并填写处理情况。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(AlarmConfirmForm form) {
        AlarmRecord record = alarmRecordMapper.selectById(form.getId());
        if (record == null) {
            throw new BusinessException("告警记录不存在");
        }
        if (record.getAlarmStatus() != AlarmStatusEnum.PENDING) {
            throw new BusinessException("该告警已处理，无需重复确认");
        }
        AlarmRecord update = new AlarmRecord();
        update.setId(record.getId());
        update.setAlarmStatus(AlarmStatusEnum.CONFIRMED);
        update.setConfirmUserId(SecurityUtils.currentUserId());
        update.setConfirmUserName(SecurityUtils.currentRealName());
        update.setConfirmTime(LocalDateTime.now());
        update.setHandleRemark(form.getHandleRemark());
        alarmRecordMapper.updateById(update);
        cacheService.delete(CacheKeys.DASHBOARD_SUMMARY);
        cacheService.delete(CacheKeys.ALARM_PENDING_COUNT);
        log.info("告警 {} 已被 {} 确认，处理说明：{}", record.getId(), update.getConfirmUserName(), form.getHandleRemark());
    }

    /**
     * 分页查询告警列表。
     */
    public IPage<AlarmRecordVO> page(AlarmQuery query) {
        Page<AlarmRecordVO> page = new Page<>(query.getCurrent(), query.getLimit());
        return alarmRecordMapper.selectAlarmPage(page, query);
    }

    /**
     * 告警详情。
     */
    public AlarmRecordVO detail(Long id) {
        AlarmRecord record = alarmRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("告警记录不存在");
        }
        AlarmRecordVO vo = new AlarmRecordVO();
        org.springframework.beans.BeanUtils.copyProperties(record, vo);
        vo.setDurationText(DurationUtils.format(record.getDurationMinutes()));
        Device device = record.getDeviceId() == null ? null : deviceMapper.selectById(record.getDeviceId());
        if (device != null) {
            DeviceType type = deviceTypeMapper.selectById(device.getDeviceTypeId());
            if (type != null) {
                vo.setDeviceTypeName(type.getTypeName());
            }
        }
        return vo;
    }

    /**
     * 最近告警，供首页时间线展示。
     */
    public List<AlarmRecord> recent(int limit) {
        Page<AlarmRecord> page = new Page<>(1, Math.max(1, Math.min(limit, 20)));
        return alarmRecordMapper.selectPage(page, Wrappers.<AlarmRecord>lambdaQuery()
                .orderByDesc(AlarmRecord::getAlarmTime)).getRecords();
    }

    /**
     * 待确认告警数。
     */
    public long pendingCount() {
        Long count = alarmRecordMapper.selectCount(Wrappers.<AlarmRecord>lambdaQuery()
                .eq(AlarmRecord::getAlarmStatus, AlarmStatusEnum.PENDING));
        return count == null ? 0L : count;
    }

    /**
     * 今日告警数（含恢复通知，按统计口径仅算离线告警）。
     */
    public long countOffline(LocalDateTime start, LocalDateTime end) {
        Long count = alarmRecordMapper.selectCount(Wrappers.<AlarmRecord>lambdaQuery()
                .eq(AlarmRecord::getAlarmType, AlarmTypeEnum.OFFLINE)
                .ge(AlarmRecord::getAlarmTime, start)
                .lt(AlarmRecord::getAlarmTime, end));
        return count == null ? 0L : count;
    }

    /**
     * 按天统计离线告警数量（月度趋势图）。
     */
    public List<CountItemVO> countByDay(LocalDateTime start, LocalDateTime end) {
        List<CountItemVO> list = alarmRecordMapper.countByDay(start, end);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 按级别统计。
     *
     * <p>SQL 返回的是 {@code URGENT}/{@code NORMAL} 枚举名，这里统一转换为中文展示名，
     * 避免图表图例出现英文原始值。</p>
     */
    public List<CountItemVO> countByLevel(LocalDateTime start, LocalDateTime end) {
        List<CountItemVO> list = alarmRecordMapper.countByLevel(start, end);
        if (list == null) {
            return Collections.emptyList();
        }
        list.forEach(item -> item.setName(levelLabel(item.getName())));
        return list;
    }

    /**
     * 告警级别枚举名转中文展示名，未知值原样返回。
     *
     * @param name 枚举名（如 URGENT）
     * @return 中文展示名（如 紧急）
     */
    private String levelLabel(String name) {
        if (name == null) {
            return "普通";
        }
        for (AlarmLevelEnum item : AlarmLevelEnum.values()) {
            if (item.name().equalsIgnoreCase(name)) {
                return item.getDesc();
            }
        }
        return name;
    }

    /**
     * 设备故障次数排行。
     */
    public List<DeviceFaultRankVO> faultRank(LocalDateTime start, LocalDateTime end, int limit) {
        List<DeviceFaultRankVO> list = deviceMapper.selectFaultRank(start, end, limit);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 平均故障恢复时长（分钟），无数据返回 0。
     */
    public long avgDurationMinutes(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<AlarmRecord> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(duration_minutes) AS avg_val")
                .isNotNull("duration_minutes")
                .ge("alarm_time", start)
                .lt("alarm_time", end);
        List<Map<String, Object>> rows = alarmRecordMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).get("avg_val");
        if (value == null) {
            return 0L;
        }
        return Math.round(((Number) value).doubleValue());
    }

    /**
     * 月度统计区间：本月 1 日 0 点至下月 1 日 0 点。
     */
    public LocalDateTime[] currentMonthRange() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        return new LocalDateTime[]{first.atStartOfDay(), first.plusMonths(1).atStartOfDay()};
    }

    /**
     * 统一构造并发布通知消息。
     */
    private void publish(AlarmRecord record, String deviceTypeName, AlarmRule rule, String content) {
        AlarmMessage message = new AlarmMessage();
        message.setAlarmId(record.getId());
        message.setDeviceId(record.getDeviceId());
        message.setDeviceName(record.getDeviceName());
        message.setDeviceIp(record.getDeviceIp());
        message.setDeviceTypeName(deviceTypeName);
        message.setAlarmType(record.getAlarmType().name());
        message.setAlarmLevel(record.getAlarmLevel().name());
        message.setAlarmTime(record.getAlarmTime());
        message.setContent(content);
        boolean pageEnabled = rule.getNotifyPageEnabled() == null || rule.getNotifyPageEnabled() == 1;
        message.setNotifyPage(pageEnabled);
        message.setNotifyMail(rule.getNotifyMailEnabled() != null && rule.getNotifyMailEnabled() == 1);
        message.setMailReceivers(rule.getMailReceivers());
        eventPublisher.publish(message);
    }

    /**
     * 设备状态描述，供页面拼接文案。
     */
    public static String statusText(DeviceStatusEnum status) {
        return status == null ? "未知" : status.getDesc();
    }
}
