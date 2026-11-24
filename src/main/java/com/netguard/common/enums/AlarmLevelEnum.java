package com.netguard.common.enums;

import lombok.Getter;

/**
 * 告警级别。
 *
 * <p>核心设备（交换机、防火墙等）离线记为紧急，其余记为普通。</p>
 */
@Getter
public enum AlarmLevelEnum {

    URGENT("紧急", "danger"),
    NORMAL("普通", "warning");

    private final String desc;
    private final String badge;

    AlarmLevelEnum(String desc, String badge) {
        this.desc = desc;
        this.badge = badge;
    }
}
