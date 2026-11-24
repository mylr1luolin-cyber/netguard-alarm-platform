package com.netguard.vo;

import lombok.Data;

/**
 * 设备故障次数排行项。
 */
@Data
public class DeviceFaultRankVO {

    private Long deviceId;

    private String deviceName;

    private String deviceIp;

    /** 离线告警总次数 */
    private Integer faultCount;

    /** 其中紧急级别次数 */
    private Integer urgentCount;

    /** 累计故障分钟数 */
    private Long totalMinutes;
}
