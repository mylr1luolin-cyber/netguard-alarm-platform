package com.netguard.vo;

import com.netguard.entity.Device;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备列表/详情视图对象，在设备实体基础上补充类型信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceVO extends Device {

    /** 设备类型名称 */
    private String typeName;
    /** 设备类型编码 */
    private String typeCode;
    /** 类型图标 */
    private String typeIcon;
    /** 该设备累计告警次数 */
    private Integer alarmCount;
}
