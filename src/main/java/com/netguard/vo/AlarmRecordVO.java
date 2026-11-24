package com.netguard.vo;

import com.netguard.entity.AlarmRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警列表视图对象，附带设备类型名称。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlarmRecordVO extends AlarmRecord {

    private String deviceTypeName;

    /** 故障持续时长的可读描述，如“1小时20分钟” */
    private String durationText;
}
