package com.netguard.vo;

import lombok.Data;

import java.util.List;

/**
 * 监控大屏/首页看板聚合数据。
 */
@Data
public class DashboardVO {

    /** 设备总数 */
    private long deviceTotal;
    /** 在线设备数 */
    private long onlineCount;
    /** 离线设备数 */
    private long offlineCount;
    /** 未知状态设备数（尚未巡检过） */
    private long unknownCount;
    /** 在线率（百分比，保留一位小数） */
    private double onlineRate;

    /** 待确认告警数 */
    private long pendingAlarms;
    /** 今日新增告警数 */
    private long todayAlarms;
    /** 本月紧急告警数 */
    private long monthUrgentAlarms;
    /** 本月告警总数 */
    private long monthAlarms;
    /** 平均故障恢复时长（分钟） */
    private long avgDurationMinutes;

    /** 最近 8 条告警 */
    private List<AlarmRecordVO> recentAlarms;
    /** 当前离线设备 */
    private List<DeviceVO> offlineDevices;
    /** 本次巡检时间描述 */
    private String lastInspectTime;
}
