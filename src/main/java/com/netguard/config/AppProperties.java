package com.netguard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台自定义配置，绑定 application.yml 中的 app.* 节点。
 *
 * <p>通过开关控制 Redis、RabbitMQ、邮件等外部依赖，缺少中间件时仍可降级运行。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 缓存模式：redis 使用 Redis，memory 使用本地内存（自动降级） */
    private String cacheMode = "redis";

    private Mq mq = new Mq();

    private Mail mail = new Mail();

    private Upload upload = new Upload();

    private Monitor monitor = new Monitor();

    @Data
    public static class Mq {
        /** 是否启用 RabbitMQ 异步通知；false 时改为本地同步处理 */
        private boolean enabled = true;
    }

    @Data
    public static class Mail {
        /** 是否启用邮件通知 */
        private boolean enabled = true;
        /** 邮件发送人展示名 */
        private String fromName = "网络故障告警监控平台";
    }

    @Data
    public static class Upload {
        /** 图片上传目录（相对于运行目录） */
        private String dir = "uploads";
        /** 访问前缀 */
        private String accessPath = "/uploads/";
    }

    @Data
    public static class Monitor {
        /** 默认巡检间隔（分钟），规则表缺失时使用 */
        private int defaultInterval = 5;
        /** 默认探测超时（毫秒） */
        private int defaultTimeout = 2000;
        /** 连续失败多少次判定离线 */
        private int defaultFailThreshold = 2;
    }
}
