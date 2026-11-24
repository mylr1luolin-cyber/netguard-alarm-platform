package com.netguard.common.enums;

import lombok.Getter;

/**
 * 巡检结果。
 */
@Getter
public enum InspectResultEnum {

    SUCCESS("通过", "success"),
    FAILED("失败", "danger");

    private final String desc;
    private final String badge;

    InspectResultEnum(String desc, String badge) {
        this.desc = desc;
        this.badge = badge;
    }
}
