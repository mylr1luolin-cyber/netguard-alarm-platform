package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.NotifyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知记录数据访问接口。
 */
@Mapper
public interface NotifyRecordMapper extends BaseMapper<NotifyRecord> {
}
