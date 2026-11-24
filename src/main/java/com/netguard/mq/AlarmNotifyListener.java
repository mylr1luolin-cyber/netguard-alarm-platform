package com.netguard.mq;

import com.netguard.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 告警通知消费者：监听业务队列，异步完成页面消息入库与邮件发送。
 *
 * <p>由独立的线程池消费，与巡检任务解耦，即使邮件服务响应缓慢也不会阻塞下一轮巡检。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mq.enabled", havingValue = "true", matchIfMissing = true)
public class AlarmNotifyListener {

    private final NotifyService notifyService;

    @RabbitListener(queues = "netguard.alarm.queue")
    public void onAlarmMessage(AlarmMessage message) {
        log.info("消费者收到告警消息：alarmId={}, type={}", message.getAlarmId(), message.getAlarmType());
        notifyService.notifyAlarm(message);
    }
}
