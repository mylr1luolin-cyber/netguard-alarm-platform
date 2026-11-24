package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.netguard.common.enums.InspectResultEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检明细实体：批次内每台设备一条探测结果。
 */
@Data
@TableName("inspect_detail")
public class InspectDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long logId;

    private Long deviceId;

    private String deviceName;

    private String deviceIp;

    /** 实际探测方式：TCP / ICMP */
    private String probeType;

    /** SUCCESS 通过 / FAILED 失败 */
    private InspectResultEnum result;

    /** 探测耗时（毫秒） */
    private Long latencyMs;

    /** 探测说明，如“端口 22 连接成功”“连接超时” */
    private String message;

    private LocalDateTime checkTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
