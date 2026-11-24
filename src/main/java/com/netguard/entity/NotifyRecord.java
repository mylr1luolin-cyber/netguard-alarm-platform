package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.netguard.common.enums.NotifyChannelEnum;
import com.netguard.common.enums.NotifyStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发送记录实体（页面消息 + 邮件）。
 */
@Data
@TableName("notify_record")
public class NotifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alarmId;

    /** PAGE / MAIL */
    private NotifyChannelEnum channel;

    /** 接收人（页面通知为角色描述，邮件为邮箱地址） */
    private String receiver;

    private String title;

    private String content;

    /** SUCCESS / FAILED / SKIPPED */
    private NotifyStatusEnum status;

    private String errorMsg;

    private LocalDateTime sendTime;

    /** 页面消息是否已读：1 已读、0 未读 */
    private Integer readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
