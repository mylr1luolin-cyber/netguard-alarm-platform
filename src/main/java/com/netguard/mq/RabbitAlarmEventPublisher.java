package com.netguard.mq;

import com.netguard.config.RabbitConfig;
import com.netguard.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 RabbitMQ 的告警事件发布器。
 *
 * <p>发送异常（如 Broker 宕机）时立即降级为本地同步通知，保证告警不丢。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitAlarmEventPublisher implements AlarmEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final NotifyService notifyService;

    @Override
    public void publish(AlarmMessage message) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.ALARM_EXCHANGE, RabbitConfig.ALARM_ROUTING_KEY, message);
            log.info("告警消息已投递到 RabbitMQ：alarmId={}, device={}", message.getAlarmId(), message.getDeviceName());
        } catch (AmqpException e) {
            log.error("RabbitMQ 投递失败，降级为本地同步通知：{}", e.getMessage());
            notifyService.notifyAlarm(message);
        }
    }
}
