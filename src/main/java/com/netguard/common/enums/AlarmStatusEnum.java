package com.netguard.common.enums;

import lombok.Getter;

/**
 * 告警处理状态。
 *
 * <p>生命周期：PENDING（待确认）→ CONFIRMED（已确认）→ RECOVERED（已恢复）→ CLOSED（已归档）。</p>
 */
@Getter
public enum AlarmStatusEnum {

    PENDING("待确认", "danger"),
    CONFIRMED("已确认", "warning"),
    RECOVERED("已恢复", "success"),
    CLOSED("已归档", "secondary");

    private final String desc;
    private final String badge;

    AlarmStatusEnum(String desc, String badge) {
        this.desc = desc;
        this.badge = badge;
    }
}
