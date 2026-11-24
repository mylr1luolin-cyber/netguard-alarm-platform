package com.netguard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.netguard.common.event.RuleChangedEvent;
import com.netguard.exception.BusinessException;
import com.netguard.config.AppProperties;
import com.netguard.dto.RuleForm;
import com.netguard.entity.AlarmRule;
import com.netguard.mapper.AlarmRuleMapper;
import com.netguard.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 告警规则服务：维护唯一的巡检/通知规则，并在保存后通知调度器重新排程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleService {

    /** 允许的巡检间隔（分钟） */
    private static final List<Integer> ALLOWED_INTERVALS = Arrays.asList(5, 10, 30);

    private final AlarmRuleMapper ruleMapper;
    private final AppProperties appProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 获取当前生效规则，不存在时按配置文件默认值初始化一条。
     */
    @Transactional(rollbackFor = Exception.class)
    public AlarmRule getRule() {
        List<AlarmRule> rules = ruleMapper.selectList(
                Wrappers.<AlarmRule>lambdaQuery().orderByAsc(AlarmRule::getId).last("LIMIT 1"));
        if (!rules.isEmpty()) {
            return rules.get(0);
        }
        AppProperties.Monitor monitor = appProperties.getMonitor();
        AlarmRule rule = new AlarmRule();
        rule.setRuleName("默认告警规则");
        rule.setInspectInterval(monitor.getDefaultInterval());
        rule.setFailThreshold(monitor.getDefaultFailThreshold());
        rule.setTimeoutMs(monitor.getDefaultTimeout());
        rule.setUrgentEnabled(1);
        rule.setNotifyPageEnabled(1);
        rule.setNotifyMailEnabled(0);
        rule.setActiveFlag(1);
        rule.setRemark("系统初始化创建，可按需调整");
        rule.setUpdateBy("system");
        ruleMapper.insert(rule);
        log.info("初始化默认告警规则：间隔 {} 分钟、失败 {} 次判定离线", rule.getInspectInterval(), rule.getFailThreshold());
        return rule;
    }

    /**
     * 保存规则配置并发布变更事件，触发巡检任务重排。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(RuleForm form) {
        if (!ALLOWED_INTERVALS.contains(form.getInspectInterval())) {
            throw new BusinessException("巡检间隔只能是 5、10、30 分钟");
        }
        if (form.getNotifyMailEnabled() != null && form.getNotifyMailEnabled() == 1
                && (form.getMailReceivers() == null || form.getMailReceivers().trim().isEmpty())) {
            throw new BusinessException("开启邮件通知时必须填写收件人邮箱");
        }
        AlarmRule rule = ruleMapper.selectById(form.getId());
        if (rule == null) {
            throw new BusinessException("规则不存在或已被删除");
        }
        rule.setRuleName(form.getRuleName().trim());
        rule.setInspectInterval(form.getInspectInterval());
        rule.setFailThreshold(form.getFailThreshold());
        rule.setTimeoutMs(form.getTimeoutMs());
        rule.setUrgentEnabled(form.getUrgentEnabled());
        rule.setNotifyPageEnabled(form.getNotifyPageEnabled());
        rule.setNotifyMailEnabled(form.getNotifyMailEnabled());
        rule.setMailReceivers(form.getMailReceivers() == null ? null : form.getMailReceivers().trim());
        rule.setActiveFlag(form.getActiveFlag());
        rule.setRemark(form.getRemark());
        rule.setUpdateBy(SecurityUtils.currentUsername());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("告警规则已更新：间隔 {} 分钟，自动巡检 {}", rule.getInspectInterval(),
                rule.getActiveFlag() != null && rule.getActiveFlag() == 1 ? "开启" : "关闭");
        // 通知调度器按新间隔重新排程
        eventPublisher.publishEvent(new RuleChangedEvent(this, rule.getInspectInterval(),
                rule.getActiveFlag() != null && rule.getActiveFlag() == 1));
    }

    /**
     * 快捷开关自动巡检。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleActive(boolean active) {
        AlarmRule rule = getRule();
        rule.setActiveFlag(active ? 1 : 0);
        rule.setUpdateBy(SecurityUtils.currentUsername());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        eventPublisher.publishEvent(new RuleChangedEvent(this, rule.getInspectInterval(), active));
    }
}
