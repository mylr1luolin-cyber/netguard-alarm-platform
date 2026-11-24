package com.netguard.common.enums;

import lombok.Getter;

/**
 * 通知发送结果。
 */
@Getter
public enum NotifyStatusEnum {

    SUCCESS("成功", "success"),
    FAILED("失败", "danger"),
    SKIPPED("未发送", "secondary");

    private final String desc;
    private final String badge;

    NotifyStatusEnum(String desc, String badge) {
        this.desc = desc;
        this.badge = badge;
    }
}
