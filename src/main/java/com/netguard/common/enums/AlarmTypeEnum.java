package com.netguard.common.enums;

import lombok.Getter;

/**
 * 告警类型：设备离线、设备恢复。
 */
@Getter
public enum AlarmTypeEnum {

    OFFLINE("设备离线", "danger"),
    RECOVER("设备恢复", "success");

    private final String desc;
    private final String badge;

    AlarmTypeEnum(String desc, String badge) {
        this.desc = desc;
        this.badge = badge;
    }
}
