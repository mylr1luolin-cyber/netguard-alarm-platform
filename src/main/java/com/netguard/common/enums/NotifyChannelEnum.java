package com.netguard.common.enums;

import lombok.Getter;

/**
 * 通知渠道：页面消息、邮件。
 */
@Getter
public enum NotifyChannelEnum {

    PAGE("页面通知"),
    MAIL("邮件通知");

    private final String desc;

    NotifyChannelEnum(String desc) {
        this.desc = desc;
    }
}
