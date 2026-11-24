package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.InspectLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 巡检批次数据访问接口。
 */
@Mapper
public interface InspectLogMapper extends BaseMapper<InspectLog> {
}
