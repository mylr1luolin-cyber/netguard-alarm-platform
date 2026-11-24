package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.InspectDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 巡检明细数据访问接口。
 */
@Mapper
public interface InspectDetailMapper extends BaseMapper<InspectDetail> {
}
