package com.netguard.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.netguard.common.constant.CacheKeys;
import com.netguard.common.enums.DeviceStatusEnum;
import com.netguard.entity.InspectLog;
import com.netguard.mapper.DeviceMapper;
import com.netguard.query.AlarmQuery;
import com.netguard.query.DeviceQuery;
import com.netguard.utils.JsonUtils;
import com.netguard.vo.AlarmRecordVO;
import com.netguard.vo.CountItemVO;
import com.netguard.vo.DashboardVO;
import com.netguard.vo.DeviceFaultRankVO;
import com.netguard.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 监控看板服务：聚合设备、告警、巡检数据，并做短时缓存与图表数据组装。
 *
 * <p>看板每 30 秒刷新一次，直接聚合数据库压力可控，这里仍加一层 Redis 缓存（可降级内存），
 * 演示 Redis 在高频只读场景下的作用，同时避免页面自动刷新把数据库打满。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final DeviceService deviceService;
    private final AlarmService alarmService;
    private final InspectService inspectService;
    private final DeviceMapper deviceMapper;
    private final CacheService cacheService;

    /**
     * 看板汇总数据，优先读缓存。
     */
    public DashboardVO summary() {
        String cached = cacheService.get(CacheKeys.DASHBOARD_SUMMARY);
        if (cached != null) {
            DashboardVO vo = JsonUtils.fromJson(cached, DashboardVO.class);
            if (vo != null) {
                return vo;
            }
        }
        DashboardVO vo = build();
        cacheService.set(CacheKeys.DASHBOARD_SUMMARY, JsonUtils.toJson(vo), CACHE_TTL);
        return vo;
    }

    /**
     * 重新聚合看板数据。
     */
    public DashboardVO build() {
        DashboardVO vo = new DashboardVO();
        long total = deviceService.page(newQuery()).getTotal();
        long online = deviceService.countByStatus(DeviceStatusEnum.ONLINE);
        long offline = deviceService.countByStatus(DeviceStatusEnum.OFFLINE);
        long unknown = deviceService.countByStatus(DeviceStatusEnum.UNKNOWN);

        vo.setDeviceTotal(total);
        vo.setOnlineCount(online);
        vo.setOfflineCount(offline);
        vo.setUnknownCount(unknown);
        vo.setOnlineRate(total == 0 ? 0D : Math.round(online * 1000.0 / total) / 10.0);

        vo.setPendingAlarms(alarmService.pendingCount());
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        vo.setTodayAlarms(alarmService.countOffline(todayStart, todayStart.plusDays(1)));

        LocalDateTime[] month = alarmService.currentMonthRange();
        vo.setMonthAlarms(alarmService.countOffline(month[0], month[1]));
        vo.setMonthUrgentAlarms(alarmService.faultRank(month[0], month[1], 100).stream()
                .mapToLong(rank -> rank.getUrgentCount() == null ? 0 : rank.getUrgentCount())
                .sum());
        vo.setAvgDurationMinutes(alarmService.avgDurationMinutes(month[0], month[1]));

        AlarmQuery alarmQuery = new AlarmQuery();
        alarmQuery.setPage(1);
        alarmQuery.setSize(8);
        IPage<AlarmRecordVO> recent = alarmService.page(alarmQuery);
        vo.setRecentAlarms(recent.getRecords());

        DeviceQuery offlineQuery = new DeviceQuery();
        offlineQuery.setPage(1);
        offlineQuery.setSize(6);
        offlineQuery.setStatus(DeviceStatusEnum.OFFLINE.name());
        IPage<DeviceVO> offlinePage = deviceService.page(offlineQuery);
        vo.setOfflineDevices(offlinePage.getRecords());

        InspectLog latest = inspectService.latestLog();
        vo.setLastInspectTime(latest == null || latest.getEndTime() == null
                ? "尚未执行巡检" : latest.getEndTime().format(TIME_FORMAT));
        return vo;
    }

    /**
     * 设备类型分布（饼图）。
     */
    public List<CountItemVO> deviceTypeDistribution() {
        List<CountItemVO> list = deviceMapper.countGroupByType();
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 在线状态分布（环形图）。
     *
     * <p>SQL 按 {@code device.status} 分组，返回的是枚举名，这里转换为中文展示名。</p>
     */
    public List<CountItemVO> statusDistribution() {
        List<CountItemVO> list = deviceMapper.countGroupByStatus();
        if (list == null) {
            return Collections.emptyList();
        }
        list.forEach(item -> item.setName(statusLabel(item.getName())));
        return list;
    }

    /**
     * 设备状态枚举名转中文展示名，未知值原样返回。
     *
     * @param name 枚举名（如 ONLINE）
     * @return 中文展示名（如 在线）
     */
    private String statusLabel(String name) {
        if (name == null) {
            return "未知";
        }
        for (com.netguard.common.enums.DeviceStatusEnum item : com.netguard.common.enums.DeviceStatusEnum.values()) {
            if (item.name().equalsIgnoreCase(name)) {
                return item.getDesc();
            }
        }
        return name;
    }

    /**
     * 最近 30 天离线告警趋势，缺失日期补 0，保证折线连续。
     */
    public List<CountItemVO> monthlyTrend(int days) {
        LocalDate from = LocalDate.now().minusDays(Math.max(1, days) - 1L);
        List<CountItemVO> raw = alarmService.countByDay(from.atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
        java.util.Map<String, Integer> dayMap = new java.util.HashMap<>();
        raw.forEach(item -> dayMap.put(item.getName(), item.getValue()));
        java.util.List<CountItemVO> result = new java.util.ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate day = from.plusDays(i);
            String key = day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            result.add(new CountItemVO(day.format(DateTimeFormatter.ofPattern("MM-dd")),
                    dayMap.getOrDefault(key, 0), null));
        }
        return result;
    }

    /**
     * 本月告警级别分布。
     */
    public List<CountItemVO> levelDistribution() {
        LocalDateTime[] month = alarmService.currentMonthRange();
        List<CountItemVO> list = alarmService.countByLevel(month[0], month[1]);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 本月设备故障次数排行。
     */
    public List<DeviceFaultRankVO> topFaultDevices(int limit) {
        LocalDateTime[] month = alarmService.currentMonthRange();
        return alarmService.faultRank(month[0], month[1], limit);
    }

    private DeviceQuery newQuery() {
        DeviceQuery query = new DeviceQuery();
        query.setPage(1);
        query.setSize(1);
        return query;
    }
}
