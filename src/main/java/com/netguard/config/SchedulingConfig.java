package com.netguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 巡检调度线程池配置。
 *
 * <p>由于巡检间隔可在“告警规则”页面动态调整，@Scheduled 的固定 cron 无法满足需求，
 * 这里显式提供 {@link ThreadPoolTaskScheduler}，由 DynamicInspectScheduler 在规则变更时
 * 取消旧任务并重新提交延迟任务。</p>
 */
@Configuration
public class SchedulingConfig {

    @Bean("inspectTaskScheduler")
    public ThreadPoolTaskScheduler inspectTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("inspect-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        return scheduler;
    }
}
