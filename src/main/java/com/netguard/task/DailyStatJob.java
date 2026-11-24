package com.netguard.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.netguard.common.enums.AlarmLevelEnum;
import com.netguard.common.enums.AlarmStatusEnum;
import com.netguard.common.enums.AlarmTypeEnum;
import com.netguard.entity.AlarmRecord;
import com.netguard.entity.DailyStat;
import com.netguard.mapper.AlarmRecordMapper;
import com.netguard.mapper.DailyStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 每日告警统计任务。
 *
 * <p>每天 00:10 汇总前一天的告警数据写入 {@code daily_stat}，供月度趋势图快速查询，
 * 避免图表每次都做全表聚合。同时支持手动回补历史日期，便于演示与数据修复。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStatJob {

    private final AlarmRecordMapper alarmRecordMapper;
    private final DailyStatMapper dailyStatMapper;

    /**
     * 每天 00:10 汇总昨日数据。
     */
    @Scheduled(cron = "0 10 0 * * ?")
    public void runDaily() {
        statDate(LocalDate.now().minusDays(1));
    }

    /**
     * 统计指定日期的告警并写入（存在则更新）。
     *
     * @param date 统计日期
     */
    public void statDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        long total = count(start, end, AlarmTypeEnum.OFFLINE, null, null);
        long urgent = count(start, end, AlarmTypeEnum.OFFLINE, AlarmLevelEnum.URGENT, null);
        long normal = count(start, end, AlarmTypeEnum.OFFLINE, AlarmLevelEnum.NORMAL, null);
        long recovered = count(start, end, AlarmTypeEnum.OFFLINE, null, AlarmStatusEnum.RECOVERED);
        long avgDuration = avgDuration(start, end);

        DailyStat stat = dailyStatMapper.selectOne(Wrappers.<DailyStat>lambdaQuery()
                .eq(DailyStat::getStatDate, date));
        boolean insert = stat == null;
        if (insert) {
            stat = new DailyStat();
            stat.setStatDate(date);
        }
        stat.setTotalCount((int) total);
        stat.setUrgentCount((int) urgent);
        stat.setNormalCount((int) normal);
        stat.setRecoveredCount((int) recovered);
        stat.setAvgDurationMinutes(avgDuration);
        if (insert) {
            dailyStatMapper.insert(stat);
        } else {
            dailyStatMapper.updateById(stat);
        }
        log.info("每日统计完成：{} 告警 {} 条（紧急 {}、普通 {}、已恢复 {}）", date, total, urgent, normal, recovered);
    }

    /**
     * 回补最近 N 天的统计数据（含今天）。
     */
    public void backfill(int days) {
        for (int i = 0; i < Math.max(1, days); i++) {
            statDate(LocalDate.now().minusDays(i));
        }
    }

    /**
     * 查询最近 N 天的统计，按日期升序，供折线图使用。
     */
    public List<DailyStat> recent(int days) {
        LocalDate from = LocalDate.now().minusDays(Math.max(1, days) - 1L);
        return dailyStatMapper.selectList(Wrappers.<DailyStat>lambdaQuery()
                .ge(DailyStat::getStatDate, from)
                .orderByAsc(DailyStat::getStatDate));
    }

    private long count(LocalDateTime start, LocalDateTime end, AlarmTypeEnum type,
                       AlarmLevelEnum level, AlarmStatusEnum status) {
        Long value = alarmRecordMapper.selectCount(Wrappers.<AlarmRecord>lambdaQuery()
                .eq(AlarmRecord::getAlarmType, type)
                .eq(level != null, AlarmRecord::getAlarmLevel, level)
                .eq(status != null, AlarmRecord::getAlarmStatus, status)
                .ge(AlarmRecord::getAlarmTime, start)
                .lt(AlarmRecord::getAlarmTime, end));
        return value == null ? 0L : value;
    }

    private long avgDuration(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<AlarmRecord> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(duration_minutes) AS avg_val")
                .isNotNull("duration_minutes")
                .ge("alarm_time", start)
                .lt("alarm_time", end);
        List<Map<String, Object>> rows = alarmRecordMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty() || rows.get(0).get("avg_val") == null) {
            return 0L;
        }
        return Math.round(((Number) rows.get(0).get("avg_val")).doubleValue());
    }
}
