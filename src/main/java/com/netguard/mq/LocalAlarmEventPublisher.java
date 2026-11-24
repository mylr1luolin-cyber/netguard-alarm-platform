package com.netguard.mq;

import com.netguard.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地同步告警事件发布器：未部署 RabbitMQ 时使用，保证功能可演示。
 *
 * <p>通过 app.mq.enabled=false 启用，生产环境建议关闭并改用 RabbitMQ 异步解耦。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mq.enabled", havingValue = "false")
public class LocalAlarmEventPublisher implements AlarmEventPublisher {

    private final NotifyService notifyService;

    @Override
    public void publish(AlarmMessage message) {
        log.info("本地模式下发告警通知：alarmId={}, device={}", message.getAlarmId(), message.getDeviceName());
        notifyService.notifyAlarm(message);
    }
}
