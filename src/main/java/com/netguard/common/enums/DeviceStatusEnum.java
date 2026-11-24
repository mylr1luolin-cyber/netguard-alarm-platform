package com.netguard.common.enums;

import lombok.Getter;

/**
 * 设备在线状态。
 */
@Getter
public enum DeviceStatusEnum {

    ONLINE("在线", "success"),
    OFFLINE("离线", "danger"),
    UNKNOWN("未知", "secondary");

    private final String desc;
    /** 对应前端徽标样式 */
    private final String badge;

    DeviceStatusEnum(String desc, String badge) {
        this.desc = desc;
        this.badge = badge;
    }
}
