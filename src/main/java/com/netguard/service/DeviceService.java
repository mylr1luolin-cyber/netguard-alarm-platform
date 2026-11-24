package com.netguard.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.netguard.common.constant.CacheKeys;
import com.netguard.common.enums.DeviceStatusEnum;
import com.netguard.dto.DeviceForm;
import com.netguard.entity.Device;
import com.netguard.entity.DeviceType;
import com.netguard.exception.BusinessException;
import com.netguard.mapper.DeviceMapper;
import com.netguard.mapper.DeviceTypeMapper;
import com.netguard.query.DeviceQuery;
import com.netguard.security.SecurityUtils;
import com.netguard.utils.JsonUtils;
import com.netguard.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备管理服务：设备的增删改查、图片上传与实时状态缓存同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final CacheService cacheService;
    private final FileService fileService;

    /**
     * 分页查询设备列表（含类型名称、告警次数）。
     */
    public IPage<DeviceVO> page(DeviceQuery query) {
        Page<DeviceVO> page = new Page<>(query.getCurrent(), query.getLimit());
        return deviceMapper.selectDevicePage(page, query);
    }

    /**
     * 全部设备，供巡检任务遍历。
     */
    public List<Device> listAll() {
        return deviceMapper.selectList(Wrappers.<Device>lambdaQuery().orderByAsc(Device::getId));
    }

    /**
     * 按状态统计数量。
     */
    public long countByStatus(DeviceStatusEnum status) {
        return deviceMapper.selectCount(Wrappers.<Device>lambdaQuery().eq(Device::getStatus, status));
    }

    /**
     * 查询单个设备，不存在时抛出业务异常。
     */
    public Device getById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在或已被删除");
        }
        return device;
    }

    /**
     * 新增设备，同时写入实时状态缓存。
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(DeviceForm form, MultipartFile image) {
        checkCodeUnique(form.getDeviceCode(), null);
        checkIpUnique(form.getIpAddress(), null);
        checkTypeExists(form.getDeviceTypeId());

        Device device = new Device();
        copyForm(form, device);
        device.setStatus(DeviceStatusEnum.UNKNOWN);
        device.setOfflineCount(0);
        device.setCreateBy(SecurityUtils.currentUsername());
        if (image != null && !image.isEmpty()) {
            device.setImageUrl(fileService.storeDeviceImage(image));
        }
        deviceMapper.insert(device);
        writeStatusCache(device);
        log.info("新增设备：{}（{}）操作人 {}", device.getDeviceName(), device.getIpAddress(), device.getCreateBy());
    }

    /**
     * 修改设备信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(DeviceForm form, MultipartFile image) {
        Device device = getById(form.getId());
        checkCodeUnique(form.getDeviceCode(), device.getId());
        checkIpUnique(form.getIpAddress(), device.getId());
        checkTypeExists(form.getDeviceTypeId());

        copyForm(form, device);
        if (image != null && !image.isEmpty()) {
            device.setImageUrl(fileService.storeDeviceImage(image));
        }
        deviceMapper.updateById(device);
        writeStatusCache(device);
        log.info("修改设备：{}（{}）", device.getDeviceName(), device.getIpAddress());
    }

    /**
     * 删除设备，并清理缓存中的实时状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Device device = getById(id);
        deviceMapper.deleteById(id);
        cacheService.deleteHashField(CacheKeys.DEVICE_STATUS, String.valueOf(id));
        log.warn("删除设备：{}（{}），操作人 {}", device.getDeviceName(), device.getIpAddress(), SecurityUtils.currentUsername());
    }

    /**
     * 巡检结果回写：更新状态、最近探测时间、在线时间与离线次数，并同步 Redis。
     *
     * @param device   设备（已被巡检任务持有）
     * @param online   本次是否探测成功
     * @param probeAt  探测时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyProbeResult(Device device, boolean online, LocalDateTime probeAt) {
        Device update = new Device();
        update.setId(device.getId());
        update.setStatus(online ? DeviceStatusEnum.ONLINE : DeviceStatusEnum.OFFLINE);
        update.setLastCheckTime(probeAt);
        if (online) {
            update.setLastOnlineTime(probeAt);
            update.setOfflineCount(0);
            device.setOfflineCount(0);
        } else {
            update.setOfflineCount((device.getOfflineCount() == null ? 0 : device.getOfflineCount()) + 1);
            device.setOfflineCount(update.getOfflineCount());
        }
        device.setStatus(update.getStatus());
        device.setLastCheckTime(probeAt);
        deviceMapper.updateById(update);
        writeStatusCache(device);
    }

    /**
     * 把设备当前状态写入缓存，供实时看板读取。
     */
    public void writeStatusCache(Device device) {
        Map<String, Object> value = new HashMap<>(4);
        value.put("status", device.getStatus() == null ? DeviceStatusEnum.UNKNOWN.name() : device.getStatus().name());
        value.put("deviceName", device.getDeviceName());
        value.put("checkTime", device.getLastCheckTime() == null ? null : device.getLastCheckTime().toString());
        cacheService.putHash(CacheKeys.DEVICE_STATUS, String.valueOf(device.getId()), JsonUtils.toJson(value));
    }

    /**
     * 启动或手动刷新时，把全部设备状态刷入缓存。
     */
    public void refreshAllStatusCache() {
        List<Device> devices = listAll();
        Map<String, String> values = new LinkedHashMap<>(devices.size());
        for (Device device : devices) {
            Map<String, Object> value = new HashMap<>(4);
            value.put("status", device.getStatus() == null ? DeviceStatusEnum.UNKNOWN.name() : device.getStatus().name());
            value.put("deviceName", device.getDeviceName());
            value.put("checkTime", device.getLastCheckTime() == null ? null : device.getLastCheckTime().toString());
            values.put(String.valueOf(device.getId()), JsonUtils.toJson(value));
        }
        cacheService.putAllHash(CacheKeys.DEVICE_STATUS, values);
    }

    /**
     * 读取缓存中的设备状态，缓存无数据时返回空 Map。
     */
    public Map<String, String> readStatusCache() {
        return cacheService.getAllHash(CacheKeys.DEVICE_STATUS);
    }

    private void copyForm(DeviceForm form, Device device) {
        device.setDeviceCode(form.getDeviceCode().trim());
        device.setDeviceName(form.getDeviceName().trim());
        device.setIpAddress(form.getIpAddress().trim());
        device.setDeviceTypeId(form.getDeviceTypeId());
        device.setMonitorPort(form.getMonitorPort());
        device.setLocation(form.getLocation());
        device.setCoreFlag(form.getCoreFlag() == null ? 0 : form.getCoreFlag());
        device.setRemark(form.getRemark());
        if (StringUtils.hasText(form.getImageUrl())) {
            device.setImageUrl(form.getImageUrl());
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceCode, code)
                .ne(excludeId != null, Device::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("设备编码 " + code + " 已存在，请更换");
        }
    }

    private void checkIpUnique(String ip, Long excludeId) {
        Long count = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getIpAddress, ip)
                .ne(excludeId != null, Device::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("IP 地址 " + ip + " 已被其他设备占用");
        }
    }

    private void checkTypeExists(Long typeId) {
        DeviceType type = deviceTypeMapper.selectById(typeId);
        if (type == null) {
            throw new BusinessException("所选设备类型不存在");
        }
    }
}
