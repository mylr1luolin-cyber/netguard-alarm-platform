package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.netguard.common.enums.AlarmLevelEnum;
import com.netguard.common.enums.AlarmStatusEnum;
import com.netguard.common.enums.AlarmTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录实体。
 *
 * <p>设备由在线转为离线时生成 OFFLINE 类型告警；恢复在线后新增 RECOVER 告警，
 * 同时把原离线告警置为 RECOVERED。</p>
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    /** 冗余设备名称，避免设备删除后告警无法展示 */
    private String deviceName;

    private String deviceIp;

    /** OFFLINE / RECOVER */
    private AlarmTypeEnum alarmType;

    /** URGENT / NORMAL */
    private AlarmLevelEnum alarmLevel;

    /** PENDING / CONFIRMED / RECOVERED / CLOSED */
    private AlarmStatusEnum alarmStatus;

    /** 告警产生时间 */
    private LocalDateTime alarmTime;

    /** 确认人 ID 与姓名 */
    private Long confirmUserId;
    private String confirmUserName;
    private LocalDateTime confirmTime;

    /** 处理情况说明 */
    private String handleRemark;

    /** 恢复时间 */
    private LocalDateTime recoverTime;

    /** 归档时间 */
    private LocalDateTime closeTime;

    /** 故障持续时长（分钟） */
    private Long durationMinutes;

    /** 是否已下发通知：1 是、0 否 */
    private Integer notifySent;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
