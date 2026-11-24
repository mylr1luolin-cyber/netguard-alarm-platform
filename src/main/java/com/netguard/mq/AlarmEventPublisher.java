package com.netguard.mq;

/**
 * 告警事件发布器：生产端只依赖该接口，具体是走 RabbitMQ 还是本地同步处理由配置决定。
 */
public interface AlarmEventPublisher {

    /**
     * 发布一条告警事件，交由通知服务异步处理。
     */
    void publish(AlarmMessage message);
}
