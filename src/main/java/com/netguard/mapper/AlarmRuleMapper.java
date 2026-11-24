package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.AlarmRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警规则数据访问接口。
 */
@Mapper
public interface AlarmRuleMapper extends BaseMapper<AlarmRule> {
}
