package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.DailyStat;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日告警统计数据访问接口。
 */
@Mapper
public interface DailyStatMapper extends BaseMapper<DailyStat> {
}
