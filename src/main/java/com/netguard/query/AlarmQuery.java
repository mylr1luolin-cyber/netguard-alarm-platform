package com.netguard.query;

import lombok.Data;

/**
 * 告警记录查询条件，支持时间范围、级别、状态、关键字组合过滤。
 */
@Data
public class AlarmQuery {

    private Integer page = 1;
    private Integer size = 10;
    /** 关键字：设备名称 / IP 模糊匹配 */
    private String keyword;
    /** 告警级别：URGENT / NORMAL */
    private String level;
    /** 告警状态：PENDING / CONFIRMED / RECOVERED / CLOSED */
    private String status;
    /** 告警类型：OFFLINE / RECOVER */
    private String type;
    /** 开始时间 yyyy-MM-dd */
    private String startDate;
    /** 结束时间 yyyy-MM-dd */
    private String endDate;

    public long getCurrent() {
        return page == null || page < 1 ? 1L : page.longValue();
    }

    public long getLimit() {
        if (size == null || size < 1) {
            return 10L;
        }
        return Math.min(size, 100);
    }
}
