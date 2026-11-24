package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警规则配置实体。
 *
 * <p>规则包含巡检间隔、连续失败阈值、邮件接收人以及两类通知开关，
 * 是全平台唯一的一份配置（单行表思想），管理员可随时调整。</p>
 */
@Data
@TableName("alarm_rule")
public class AlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 巡检间隔（分钟），取值 5 / 10 / 30 */
    private Integer inspectInterval;

    /** 连续探测失败多少次才判定离线并告警 */
    private Integer failThreshold;

    /** 单次探测超时时间（毫秒） */
    private Integer timeoutMs;

    /** 是否对核心设备产生紧急告警：1 是 */
    private Integer urgentEnabled;

    /** 是否启用页面通知：1 是 */
    private Integer notifyPageEnabled;

    /** 是否启用邮件通知：1 是 */
    private Integer notifyMailEnabled;

    /** 邮件接收人，多个用英文逗号分隔；为空则使用全部启用用户的邮箱 */
    private String mailReceivers;

    /** 是否启用：1 启用、0 停用（停用后不再自动巡检） */
    private Integer activeFlag;

    private String remark;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
