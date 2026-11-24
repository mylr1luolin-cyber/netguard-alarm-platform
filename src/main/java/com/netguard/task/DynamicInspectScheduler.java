package com.netguard.task;

import com.netguard.common.enums.InspectTriggerEnum;
import com.netguard.common.event.RuleChangedEvent;
import com.netguard.entity.AlarmRule;
import com.netguard.exception.BusinessException;
import com.netguard.service.AlarmRuleService;
import com.netguard.service.InspectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 动态巡检调度器。
 *
 * <p>课程要求巡检间隔可在 5/10/30 分钟之间动态切换，而 {@code @Scheduled} 的 fixedDelay
 * 只能在启动时确定，因此这里注入 {@link ThreadPoolTaskScheduler} 手动排程：
 * 规则变更时取消旧任务并按新间隔重新提交，避免出现两个巡检任务并行执行的重复告警。</p>
 */
@Slf4j
@Component
public class DynamicInspectScheduler {

    private final ThreadPoolTaskScheduler scheduler;
    private final InspectService inspectService;
    private final AlarmRuleService alarmRuleService;

    /** 当前已提交的周期任务 */
    private ScheduledFuture<?> future;
    /** 当前生效的间隔（分钟），-1 表示尚未初始化 */
    private volatile int intervalMinutes = -1;
    /** 防止上一轮巡检未结束时重复进入 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DynamicInspectScheduler(@Qualifier("inspectTaskScheduler") ThreadPoolTaskScheduler scheduler,
                                   InspectService inspectService,
                                   AlarmRuleService alarmRuleService) {
        this.scheduler = scheduler;
        this.inspectService = inspectService;
        this.alarmRuleService = alarmRuleService;
    }

    /**
     * 应用启动完成后读取规则并开始调度，同时安排一次首轮巡检，便于立即看到数据。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            AlarmRule rule = alarmRuleService.getRule();
            boolean active = rule.getActiveFlag() != null && rule.getActiveFlag() == 1;
            reschedule(rule.getInspectInterval(), active);
            // 启动 20 秒后先跑一次，让监控大屏有初始状态
            scheduler.schedule(this::safeInspect, java.time.Instant.now().plusSeconds(20));
            log.info("动态巡检调度器已启动：间隔 {} 分钟，自动巡检 {}", rule.getInspectInterval(), active ? "开启" : "关闭");
        } catch (Exception e) {
            log.error("巡检调度器初始化失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 规则保存后重新排程。
     */
    @EventListener(RuleChangedEvent.class)
    public void onRuleChanged(RuleChangedEvent event) {
        reschedule(event.getInspectInterval(), event.isActive());
    }

    /**
     * 按新的间隔与启用状态重新排程。
     */
    public synchronized void reschedule(int minutes, boolean active) {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        intervalMinutes = minutes;
        if (!active) {
            log.info("自动巡检已关闭，等待管理员重新开启");
            return;
        }
        int safeMinutes = Math.max(1, minutes);
        future = scheduler.scheduleWithFixedDelay(this::safeInspect, Duration.ofMinutes(safeMinutes));
        log.info("自动巡检已排程：每 {} 分钟执行一次", safeMinutes);
    }

    /**
     * 包装巡检任务，捕获异常避免周期任务被取消，并防止重入。
     */
    private void safeInspect() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一轮巡检尚未结束，本次跳过");
            return;
        }
        try {
            inspectService.runInspect(InspectTriggerEnum.AUTO, "system");
        } catch (BusinessException e) {
            log.warn("自动巡检业务异常：{}", e.getMessage());
        } catch (Exception e) {
            log.error("自动巡检执行失败：{}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }
}
