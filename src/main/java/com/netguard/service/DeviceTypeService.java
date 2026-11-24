package com.netguard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.netguard.dto.DeviceTypeForm;
import com.netguard.entity.Device;
import com.netguard.entity.DeviceType;
import com.netguard.exception.BusinessException;
import com.netguard.mapper.DeviceMapper;
import com.netguard.mapper.DeviceTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备类型字典服务：类型的新增、修改、删除与排序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTypeService {

    private final DeviceTypeMapper deviceTypeMapper;
    private final DeviceMapper deviceMapper;

    /**
     * 全部类型，按排序号升序。
     */
    public List<DeviceType> listAll() {
        return deviceTypeMapper.selectList(Wrappers.<DeviceType>lambdaQuery()
                .orderByAsc(DeviceType::getSortOrder)
                .orderByAsc(DeviceType::getId));
    }

    public DeviceType getById(Long id) {
        DeviceType type = deviceTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException("设备类型不存在");
        }
        return type;
    }

    /**
     * 统计该类型下设备数量，用于删除前校验。
     */
    public long countDevice(Long typeId) {
        Long count = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery().eq(Device::getDeviceTypeId, typeId));
        return count == null ? 0L : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(DeviceTypeForm form) {
        checkCodeUnique(form.getTypeCode(), null);
        DeviceType type = new DeviceType();
        copy(form, type);
        deviceTypeMapper.insert(type);
        log.info("新增设备类型：{}({})", type.getTypeName(), type.getTypeCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(DeviceTypeForm form) {
        DeviceType type = getById(form.getId());
        checkCodeUnique(form.getTypeCode(), type.getId());
        copy(form, type);
        deviceTypeMapper.updateById(type);
        log.info("修改设备类型：{}({})", type.getTypeName(), type.getTypeCode());
    }

    /**
     * 删除类型：存在关联设备时拒绝，避免产生孤儿设备。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DeviceType type = getById(id);
        long used = countDevice(id);
        if (used > 0) {
            throw new BusinessException("该类型下还有 " + used + " 台设备，请先调整设备类型再删除");
        }
        deviceTypeMapper.deleteById(id);
        log.warn("删除设备类型：{}({})", type.getTypeName(), type.getTypeCode());
    }

    private void copy(DeviceTypeForm form, DeviceType type) {
        type.setTypeName(form.getTypeName().trim());
        type.setTypeCode(form.getTypeCode().trim().toUpperCase());
        type.setIcon(form.getIcon());
        type.setCoreFlag(form.getCoreFlag() == null ? 0 : form.getCoreFlag());
        type.setSortOrder(form.getSortOrder() == null ? 99 : form.getSortOrder());
        type.setRemark(form.getRemark());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = deviceTypeMapper.selectCount(Wrappers.<DeviceType>lambdaQuery()
                .eq(DeviceType::getTypeCode, code.trim().toUpperCase())
                .ne(excludeId != null, DeviceType::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("类型编码 " + code + " 已存在");
        }
    }
}
