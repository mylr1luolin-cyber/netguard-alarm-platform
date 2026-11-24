package com.netguard.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 设备类型字典表单。
 */
@Data
public class DeviceTypeForm {

    private Long id;

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 50, message = "类型名称长度不能超过 50 个字符")
    private String typeName;

    @NotBlank(message = "类型编码不能为空")
    @Size(max = 30, message = "类型编码长度不能超过 30 个字符")
    private String typeCode;

    /** 图标类名，如 bi-hdd-network */
    @Size(max = 50, message = "图标类名过长")
    private String icon;

    /** 是否关键设备类型：1 是、0 否 */
    private Integer coreFlag = 0;

    private Integer sortOrder = 99;

    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;
}
