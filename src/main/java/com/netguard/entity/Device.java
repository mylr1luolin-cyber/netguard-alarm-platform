package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.netguard.common.enums.DeviceStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网络设备实体。
 */
@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编码，如 SW-CORE-01，业务唯一 */
    private String deviceCode;

    private String deviceName;

    /** 设备 IP（IPv4 点分十进制） */
    private String ipAddress;

    /** 设备类型外键 */
    private Long deviceTypeId;

    /** 监控端口，TCP 端口探测使用；为空则退化为 ICMP 探测 */
    private Integer monitorPort;

    /** 机柜/位置描述 */
    private String location;

    /** 1 核心设备（告警为紧急级别）、0 普通设备 */
    private Integer coreFlag;

    /** 当前在线状态 */
    private DeviceStatusEnum status;

    /** 最近一次巡检时间 */
    private LocalDateTime lastCheckTime;

    /** 最近一次探测到的在线时间 */
    private LocalDateTime lastOnlineTime;

    /** 累计离线次数 */
    private Integer offlineCount;

    /** 设备图片地址 */
    private String imageUrl;

    private String remark;

    /** 创建人账号 */
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
