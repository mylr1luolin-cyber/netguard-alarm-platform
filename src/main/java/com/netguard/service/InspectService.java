package com.netguard.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.netguard.common.constant.CacheKeys;
import com.netguard.common.enums.AlarmLevelEnum;
import com.netguard.common.enums.DeviceStatusEnum;
import com.netguard.common.enums.InspectResultEnum;
import com.netguard.common.enums.InspectTriggerEnum;
import com.netguard.entity.AlarmRule;
import com.netguard.entity.Device;
import com.netguard.entity.DeviceType;
import com.netguard.entity.InspectDetail;
import com.netguard.entity.InspectLog;
import com.netguard.mapper.DeviceTypeMapper;
import com.netguard.mapper.InspectDetailMapper;
import com.netguard.mapper.InspectLogMapper;
import com.netguard.security.SecurityUtils;
import com.netguard.utils.ProbeResult;
import com.netguard.utils.ProbeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检服务：对全部设备执行 TCP/ICMP 探测，记录批次与明细，并根据状态变化产生告警。
 *
 * <p>一次巡检的工作流程：
 * <ol>
 *   <li>读取当前告警规则（间隔、失败阈值、超时时间）；</li>
 *   <li>遍历设备，优先探测配置的监控端口（TCP），未配置端口时退回 ICMP；</li>
 *   <li>把探测结果写入 {@code inspect_detail}，状态写入 {@code device} 与 Redis；</li>
 *   <li>连续失败达到阈值时生成离线告警，设备恢复在线时生成恢复通知。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectService {

    private final InspectLogMapper inspectLogMapper;
    private final InspectDetailMapper inspectDetailMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final DeviceService deviceService;
    private final AlarmRuleService alarmRuleService;
    private final AlarmService alarmService;
    private final CacheService cacheService;

    /**
     * 执行一次全量巡检（自动或手动）。
     *
     * @param trigger  触发方式
     * @param operator 操作人，自动巡检为 system
     * @return 本次巡检批次记录
     */
    @Transactional(rollbackFor = Exception.class)
    public InspectLog runInspect(InspectTriggerEnum trigger, String operator) {
        AlarmRule rule = alarmRuleService.getRule();
        int timeout = rule.getTimeoutMs() == null ? 2000 : rule.getTimeoutMs();
        int threshold = rule.getFailThreshold() == null ? 2 : Math.max(1, rule.getFailThreshold());

        LocalDateTime start = LocalDateTime.now();
        long begin = System.currentTimeMillis();
        List<Device> devices = deviceService.listAll();
        Map<Long, String> typeNames = loadTypeNames();

        int online = 0;
        int offline = 0;
        int changed = 0;
        List<InspectDetail> details = new ArrayList<>(devices.size());

        for (Device device : devices) {
            DeviceStatusEnum previous = device.getStatus();
            ProbeResult result = ProbeUtils.probe(device.getIpAddress(), device.getMonitorPort(), timeout);
            LocalDateTime probeAt = LocalDateTime.now();

            deviceService.applyProbeResult(device, result.isSuccess(), probeAt);
            if (result.isSuccess()) {
                online++;
            } else {
                offline++;
            }
            if (previous != device.getStatus()) {
                changed++;
            }

            InspectDetail detail = new InspectDetail();
            detail.setDeviceId(device.getId());
            detail.setDeviceName(device.getDeviceName());
            detail.setDeviceIp(device.getIpAddress());
            detail.setProbeType(result.getProbeType());
            detail.setResult(result.isSuccess() ? InspectResultEnum.SUCCESS : InspectResultEnum.FAILED);
            detail.setLatencyMs(result.getLatencyMs());
            detail.setMessage(result.getMessage());
            detail.setCheckTime(probeAt);
            details.add(detail);

            triggerAlarmIfNeeded(device, previous, result.isSuccess(), threshold, typeNames, rule);
        }

        InspectLog logEntity = new InspectLog();
        logEntity.setTriggerType(trigger);
        logEntity.setTotalCount(devices.size());
        logEntity.setOnlineCount(online);
        logEntity.setOfflineCount(offline);
        logEntity.setChangeCount(changed);
        logEntity.setStartTime(start);
        logEntity.setEndTime(LocalDateTime.now());
        logEntity.setCostMs(System.currentTimeMillis() - begin);
        logEntity.setOperator(operator);
        logEntity.setRemark(String.format("间隔 %d 分钟，失败阈值 %d 次，超时 %d ms",
                rule.getInspectInterval(), threshold, timeout));
        inspectLogMapper.insert(logEntity);

        for (InspectDetail detail : details) {
            detail.setLogId(logEntity.getId());
            inspectDetailMapper.insert(detail);
        }
        cacheService.delete(CacheKeys.DASHBOARD_SUMMARY);
        log.info("巡检完成：批次 {}，共 {} 台，在线 {}，离线 {}，状态变化 {}，耗时 {} ms",
                logEntity.getId(), devices.size(), online, offline, changed, logEntity.getCostMs());
        return logEntity;
    }

    /**
     * 手动触发巡检，操作人取当前登录用户。
     */
    public InspectLog runManual() {
        return runInspect(InspectTriggerEnum.MANUAL, SecurityUtils.currentUsername());
    }

    /**
     * 按失败阈值决定是否告警：连续失败次数刚好达到阈值时产生一次离线告警；
     * 设备由离线恢复在线时产生恢复通知。
     */
    private void triggerAlarmIfNeeded(Device device, DeviceStatusEnum previous, boolean success,
                                      int threshold, Map<Long, String> typeNames, AlarmRule rule) {
        String typeName = typeNames.getOrDefault(device.getDeviceTypeId(), "未知类型");
        if (success) {
            if (previous == DeviceStatusEnum.OFFLINE) {
                alarmService.createRecoverAlarm(device, typeName, rule);
            }
            return;
        }
        int offlineCount = device.getOfflineCount() == null ? 0 : device.getOfflineCount();
        if (offlineCount == threshold) {
            alarmService.createOfflineAlarm(device, typeName, rule);
        }
    }

    /**
     * 单台设备即时探测，供设备列表“立即探测”按钮使用。
     *
     * @param deviceId 设备主键
     * @return 探测结果
     */
    public ProbeResult checkOne(Long deviceId) {
        Device device = deviceService.getById(deviceId);
        AlarmRule rule = alarmRuleService.getRule();
        int timeout = rule.getTimeoutMs() == null ? 2000 : rule.getTimeoutMs();
        int threshold = rule.getFailThreshold() == null ? 2 : Math.max(1, rule.getFailThreshold());

        DeviceStatusEnum previous = device.getStatus();
        ProbeResult result = ProbeUtils.probe(device.getIpAddress(), device.getMonitorPort(), timeout);
        deviceService.applyProbeResult(device, result.isSuccess(), LocalDateTime.now());
        triggerAlarmIfNeeded(device, previous, result.isSuccess(), threshold, loadTypeNames(), rule);
        cacheService.delete(CacheKeys.DASHBOARD_SUMMARY);
        log.info("手动探测设备 {}（{}）：{}", device.getDeviceName(), device.getIpAddress(), result.getMessage());
        return result;
    }

    private Map<Long, String> loadTypeNames() {
        List<DeviceType> types = deviceTypeMapper.selectList(null);
        Map<Long, String> map = new HashMap<>(types.size());
        for (DeviceType type : types) {
            map.put(type.getId(), type.getTypeName());
        }
        return map;
    }

    /**
     * 巡检批次分页列表。
     */
    public IPage<InspectLog> pageLogs(int page, int size) {
        Page<InspectLog> p = new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 50)));
        return inspectLogMapper.selectPage(p, Wrappers.<InspectLog>lambdaQuery()
                .orderByDesc(InspectLog::getId));
    }

    /**
     * 最近一次巡检批次。
     */
    public InspectLog latestLog() {
        List<InspectLog> list = inspectLogMapper.selectList(Wrappers.<InspectLog>lambdaQuery()
                .orderByDesc(InspectLog::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 巡检批次详情实体。
     */
    public InspectLog getLog(Long id) {
        InspectLog logEntity = inspectLogMapper.selectById(id);
        if (logEntity == null) {
            throw new com.netguard.exception.BusinessException("巡检批次不存在");
        }
        return logEntity;
    }

    /**
     * 批次明细。
     */
    public List<InspectDetail> listDetails(Long logId) {
        return inspectDetailMapper.selectList(Wrappers.<InspectDetail>lambdaQuery()
                .eq(InspectDetail::getLogId, logId)
                .orderByAsc(InspectDetail::getId));
    }

    /**
     * 列出当前所有离线设备，供首页告警面板展示。
     */
    public List<Device> listOfflineDevices() {
        return deviceService.listAll().stream()
                .filter(d -> d.getStatus() == DeviceStatusEnum.OFFLINE)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 统计当前告警级别分布用：核心设备离线视为紧急。
     */
    public AlarmLevelEnum levelOf(Device device) {
        return device.getCoreFlag() != null && device.getCoreFlag() == 1
                ? AlarmLevelEnum.URGENT : AlarmLevelEnum.NORMAL;
    }
}
