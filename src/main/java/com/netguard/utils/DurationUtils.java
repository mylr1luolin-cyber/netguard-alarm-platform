package com.netguard.utils;

/**
 * 时长格式化工具，把分钟数转换为“x小时y分钟”的自然语言描述。
 */
public final class DurationUtils {

    private DurationUtils() {
    }

    /**
     * 把分钟数格式化为中文可读文本。
     *
     * @param minutes 分钟数，null 或负数返回 “-”
     */
    public static String format(Long minutes) {
        if (minutes == null || minutes < 0) {
            return "-";
        }
        if (minutes == 0) {
            return "不足 1 分钟";
        }
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (mins > 0) {
            sb.append(mins).append("分钟");
        }
        return sb.toString();
    }
}
