package com.netguard.common.enums;

import lombok.Getter;

/**
 * 系统角色枚举。
 */
@Getter
public enum RoleEnum {

    /** 管理员：设备与用户管理、告警规则配置、查看全部告警 */
    ADMIN("管理员"),
    /** 运维人员：查看设备状态、确认告警、处理故障 */
    OPS("运维人员");

    private final String desc;

    RoleEnum(String desc) {
        this.desc = desc;
    }
}
