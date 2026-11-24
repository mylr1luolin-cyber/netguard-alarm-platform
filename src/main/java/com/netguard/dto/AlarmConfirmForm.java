package com.netguard.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 告警确认表单：运维人员填写处理情况。
 */
@Data
public class AlarmConfirmForm {

    @NotNull(message = "告警 ID 不能为空")
    private Long id;

    @Size(max = 255, message = "处理说明不能超过 255 个字符")
    private String handleRemark;
}
