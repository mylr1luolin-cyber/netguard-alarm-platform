package com.netguard.common.enums;

import lombok.Getter;

/**
 * 巡检触发方式。
 */
@Getter
public enum InspectTriggerEnum {

    AUTO("定时巡检"),
    MANUAL("手动巡检");

    private final String desc;

    InspectTriggerEnum(String desc) {
        this.desc = desc;
    }
}
