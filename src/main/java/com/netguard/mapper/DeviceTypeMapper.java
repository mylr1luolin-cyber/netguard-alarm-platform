package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.DeviceType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备类型数据访问接口。
 */
@Mapper
public interface DeviceTypeMapper extends BaseMapper<DeviceType> {
}
