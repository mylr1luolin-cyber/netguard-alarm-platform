package com.netguard.common.constant;

/**
 * Redis 缓存键集中管理，避免键名散落在业务代码中。
 */
public final class CacheKeys {

    /** 设备实时状态 Hash：field = 设备 ID，value = JSON{status, checkTime} */
    public static final String DEVICE_STATUS = "netguard:device:status";

    /** 首页看板汇总数据缓存 */
    public static final String DASHBOARD_SUMMARY = "netguard:dashboard:summary";

    /** 待确认告警数量缓存 */
    public static final String ALARM_PENDING_COUNT = "netguard:alarm:pending";

    /** 设备类型字典缓存 */
    public static final String DEVICE_TYPES = "netguard:device:types";

    /** 每日巡检统计缓存 */
    public static final String INSPECT_TODAY = "netguard:inspect:today";

    private CacheKeys() {
    }
}
