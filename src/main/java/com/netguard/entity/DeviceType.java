package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备类型字典实体（交换机、路由器、服务器、防火墙等）。
 */
@Data
@TableName("device_type")
public class DeviceType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型名称，如“核心交换机” */
    private String typeName;

    /** 类型编码，如 SWITCH */
    private String typeCode;

    /** 展示图标（bootstrap-icons 类名） */
    private String icon;

    /** 是否为关键设备类型，决定默认告警级别 */
    private Integer coreFlag;

    /** 排序号，数值越小越靠前 */
    private Integer sortOrder;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
