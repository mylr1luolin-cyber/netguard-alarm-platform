package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.netguard.entity.AlarmRecord;
import com.netguard.query.AlarmQuery;
import com.netguard.vo.AlarmRecordVO;
import com.netguard.vo.CountItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警记录数据访问接口。
 */
@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {

    /**
     * 分页查询告警（关联设备与设备类型，支持时间范围、级别、状态、关键字组合过滤）。
     */
    IPage<AlarmRecordVO> selectAlarmPage(IPage<AlarmRecordVO> page, @Param("q") AlarmQuery query);

    /**
     * 按天统计告警数量，用于月度告警趋势图。
     *
     * @param start 起始时间（含）
     * @param end   结束时间（不含）
     */
    List<CountItemVO> countByDay(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * 按告警级别统计。
     */
    List<CountItemVO> countByLevel(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    /**
     * 按告警类型统计。
     */
    List<CountItemVO> countByType(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);
}
