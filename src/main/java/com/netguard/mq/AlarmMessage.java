package com.netguard.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警消息体，由巡检任务发送到 RabbitMQ，由通知消费者异步处理。
 *
 * <p>消息只携带渲染通知所需的最小信息，消费者即使拿不到数据库连接也能发出通知。</p>
 */
@Data
public class AlarmMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警记录 ID */
    private Long alarmId;
    private Long deviceId;
    private String deviceName;
    private String deviceIp;
    private String deviceTypeName;
    /** OFFLINE / RECOVER */
    private String alarmType;
    /** URGENT / NORMAL */
    private String alarmLevel;
    private LocalDateTime alarmTime;
    /** 告警正文，例如“设备 核心交换机-01(10.0.0.1) 于 10:20 探测失败，已判定离线” */
    private String content;
    /** 是否发送页面通知 */
    private boolean notifyPage;
    /** 是否发送邮件通知 */
    private boolean notifyMail;
    /** 邮件接收人，多个用英文逗号分隔 */
    private String mailReceivers;
}
