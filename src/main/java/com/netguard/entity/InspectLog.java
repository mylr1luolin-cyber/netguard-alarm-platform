package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.netguard.common.enums.InspectTriggerEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检批次记录实体：一次巡检（定时或手动）对应一条主记录。
 */
@Data
@TableName("inspect_log")
public class InspectLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** AUTO 定时 / MANUAL 手动 */
    private InspectTriggerEnum triggerType;

    /** 本批次巡检设备总数 */
    private Integer totalCount;

    private Integer onlineCount;

    private Integer offlineCount;

    /** 本批次发生状态变化的设备数 */
    private Integer changeCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 执行人，自动巡检为 system */
    private String operator;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
