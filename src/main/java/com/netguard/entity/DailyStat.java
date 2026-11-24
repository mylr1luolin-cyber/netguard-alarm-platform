package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日告警汇总统计实体，供月度趋势图表快速查询（避免每次全表聚合）。
 */
@Data
@TableName("daily_stat")
public class DailyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Integer totalCount;

    private Integer urgentCount;

    private Integer normalCount;

    private Integer recoveredCount;

    /** 当日平均故障持续分钟数 */
    private Long avgDurationMinutes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
