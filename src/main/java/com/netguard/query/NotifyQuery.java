package com.netguard.query;

import lombok.Data;

/**
 * 通知记录查询条件。
 */
@Data
public class NotifyQuery {

    private Integer page = 1;
    private Integer size = 10;
    /** 渠道：PAGE / MAIL */
    private String channel;
    /** 已读状态：1 已读、0 未读 */
    private Integer readFlag;
    /** 关键字：标题 / 内容模糊匹配 */
    private String keyword;

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
