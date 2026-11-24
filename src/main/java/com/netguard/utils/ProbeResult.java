package com.netguard.utils;

import lombok.Data;

/**
 * 单次设备探测结果。
 */
@Data
public class ProbeResult {

    /** 是否连通 */
    private boolean success;
    /** 探测方式：TCP / ICMP */
    private String probeType;
    /** 耗时（毫秒） */
    private long latencyMs;
    /** 结果说明 */
    private String message;

    public static ProbeResult of(boolean success, String probeType, long latencyMs, String message) {
        ProbeResult result = new ProbeResult();
        result.setSuccess(success);
        result.setProbeType(probeType);
        result.setLatencyMs(latencyMs);
        result.setMessage(message);
        return result;
    }
}
