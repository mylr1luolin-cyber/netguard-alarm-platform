package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.netguard.entity.Device;
import com.netguard.query.DeviceQuery;
import com.netguard.vo.CountItemVO;
import com.netguard.vo.DeviceFaultRankVO;
import com.netguard.vo.DeviceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备数据访问接口。
 *
 * <p>分页查询与统计使用 XML 动态 SQL，实现设备表与类型表的多表关联。</p>
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 分页查询设备（关联设备类型，动态拼装过滤条件）。
     */
    IPage<DeviceVO> selectDevicePage(IPage<DeviceVO> page, @Param("q") DeviceQuery query);

    /**
     * 设备故障次数排行（按时间范围统计离线告警）。
     */
    List<DeviceFaultRankVO> selectFaultRank(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("limit") Integer limit);

    /**
     * 按设备类型统计设备数量，用于饼图。
     */
    List<CountItemVO> countGroupByType();

    /**
     * 按在线状态统计设备数量。
     */
    List<CountItemVO> countGroupByStatus();

    /**
     * 统计某类型下的设备数量，删除类型前校验。
     */
    long countByTypeId(@Param("typeId") Long typeId);
}
