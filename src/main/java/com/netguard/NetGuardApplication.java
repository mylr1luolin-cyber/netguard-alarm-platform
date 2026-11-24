package com.netguard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 网络故障告警监控平台启动类。
 *
 * <p>平台面向机房网络设备 7×24 小时巡检场景：定时任务探测设备连通性，
 * 状态发生“在线 → 离线”变化时通过 RabbitMQ 异步下发告警，运维人员确认处置，
 * 设备恢复后自动归档告警。</p>
 *
 * @author NetGuard Team
 */
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@MapperScan("com.netguard.mapper")
@SpringBootApplication
public class NetGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetGuardApplication.class, args);
    }
}
