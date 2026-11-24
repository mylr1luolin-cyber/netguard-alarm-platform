package com.netguard.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 设备新增/编辑表单。
 */
@Data
public class DeviceForm {

    /** 编辑时携带主键，新增时为空 */
    private Long id;

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 32, message = "设备编码长度不能超过 32 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "设备编码只能包含字母、数字、下划线和短横线")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 64, message = "设备名称长度不能超过 64 个字符")
    private String deviceName;

    @NotBlank(message = "设备 IP 不能为空")
    @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$",
            message = "请输入合法的 IPv4 地址")
    private String ipAddress;

    @NotNull(message = "请选择设备类型")
    private Long deviceTypeId;

    @Min(value = 1, message = "监控端口范围为 1-65535")
    @Max(value = 65535, message = "监控端口范围为 1-65535")
    private Integer monitorPort;

    @Size(max = 100, message = "位置描述长度不能超过 100 个字符")
    private String location;

    /** 是否核心设备：1 是、0 否 */
    private Integer coreFlag = 0;

    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

    /** 已有的图片地址，上传新图时被覆盖 */
    private String imageUrl;
}
