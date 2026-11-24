package com.netguard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用“名称-数值”统计项，供 ECharts 饼图、柱状图使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CountItemVO {

    private String name;

    private Integer value;

    /** 额外维度，如占比 */
    private Double extra;
}
