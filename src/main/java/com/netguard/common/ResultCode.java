package com.netguard.common;

import lombok.Getter;

/**
 * 业务状态码定义。
 *
 * <p>约定：2xx 成功，4xx 客户端错误，5xx 服务端错误，10xx 为业务细分错误。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未登录或登录状态已失效"),
    FORBIDDEN(403, "没有该操作的权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    BUSINESS_ERROR(500, "业务处理失败"),

    DEVICE_NOT_FOUND(1001, "设备不存在"),
    DEVICE_CODE_DUPLICATE(1002, "设备编码已存在"),
    DEVICE_IP_DUPLICATE(1003, "该 IP 地址已被其它设备占用"),
    DEVICE_TYPE_NOT_FOUND(1004, "设备类型不存在"),
    DEVICE_TYPE_IN_USE(1005, "该类型下仍有设备，无法删除"),

    ALARM_NOT_FOUND(2001, "告警记录不存在"),
    ALARM_ALREADY_CONFIRMED(2002, "该告警已被确认，请勿重复操作"),

    RULE_NOT_FOUND(3001, "告警规则不存在"),
    RULE_DUPLICATE(3002, "同类规则已存在"),

    USER_NOT_FOUND(4001, "用户不存在"),
    PASSWORD_INCORRECT(4002, "原密码不正确"),
    USER_DISABLED(4003, "账号已被停用，请联系管理员"),

    FILE_UPLOAD_ERROR(5001, "图片上传失败"),
    FILE_TYPE_NOT_ALLOWED(5002, "仅支持 jpg、jpeg、png、gif 格式的图片");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
