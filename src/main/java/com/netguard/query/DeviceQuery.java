package com.netguard.query;

import lombok.Data;

/**
 * 设备列表查询条件。
 */
@Data
public class DeviceQuery {

    /** 页码，从 1 开始 */
    private Integer page = 1;
    /** 每页条数 */
    private Integer size = 10;
    /** 关键字：设备名称 / 编码 / IP 模糊匹配 */
    private String keyword;
    /** 设备类型过滤 */
    private Long typeId;
    /** 在线状态过滤：ONLINE / OFFLINE / UNKNOWN */
    private String status;
    /** 是否核心设备：1 是、0 否、null 全部 */
    private Integer coreFlag;
    /** 排序字段：last_check_time / offline_count / create_time */
    private String sortField;
    /** 排序方向：asc / desc */
    private String sortOrder;

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
