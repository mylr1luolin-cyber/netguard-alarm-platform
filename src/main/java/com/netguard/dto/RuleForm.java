package com.netguard.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 告警规则配置表单。
 */
@Data
public class RuleForm {

    @NotNull(message = "规则 ID 不能为空")
    private Long id;

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 64, message = "规则名称长度不能超过 64 个字符")
    private String ruleName;

    /** 巡检间隔，仅允许 5 / 10 / 30 分钟 */
    @NotNull(message = "请选择巡检间隔")
    private Integer inspectInterval;

    @NotNull(message = "请填写连续失败次数")
    @Min(value = 1, message = "连续失败次数不能小于 1")
    @Max(value = 5, message = "连续失败次数不能大于 5")
    private Integer failThreshold;

    @NotNull(message = "请填写探测超时时间")
    @Min(value = 500, message = "探测超时不能小于 500 毫秒")
    @Max(value = 10000, message = "探测超时不能大于 10000 毫秒")
    private Integer timeoutMs;

    /** 核心设备是否产生紧急告警 */
    private Integer urgentEnabled = 1;

    private Integer notifyPageEnabled = 1;

    private Integer notifyMailEnabled = 0;

    /** 邮件接收人，多个用英文逗号分隔 */
    @Size(max = 500, message = "接收人列表过长")
    private String mailReceivers;

    /** 是否启用自动巡检 */
    private Integer activeFlag = 1;

    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;
}
