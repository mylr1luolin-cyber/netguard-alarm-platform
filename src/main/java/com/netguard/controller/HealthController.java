package com.netguard.controller;

import com.netguard.common.Result;
import com.netguard.entity.SysUser;
import com.netguard.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <p>用途：换服务器、配 Nginx、排查"打不开/打开的不是这个程序"时，
 * 浏览器直接访问 {@code /health}，返回 JSON 就说明请求落到了 NetGuard 上，
 * 并且能一眼看到端口、环境、启动时间；返回别的页面说明 8080 上是另一个程序。</p>
 */
@Slf4j
@RestController
public class HealthController {

    private final Environment environment;
    private final DataSource dataSource;
    private final SysUserMapper sysUserMapper;

    @Value("${server.port:8080}")
    private String port;

    /** 应用启动时间，用于判断进程是不是刚重启过。 */
    private final LocalDateTime startedAt = LocalDateTime.now();

    public HealthController(Environment environment, DataSource dataSource, SysUserMapper sysUserMapper) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 返回应用名、版本、环境、启动时间，以及"程序实际连的库"和账号状态。
     *
     * <p>排查"我在另一个库里改了密码却登不上"时，看 {@code database} 与 {@code adminPasswordLength}
     * 两个字段就能判断到底改没改到同一个库、密文长度是否正常（正常 60）。</p>
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "NetGuard 网络故障告警监控平台");
        data.put("version", "1.0.0");
        data.put("port", port);
        data.put("profiles", String.join(",", environment.getActiveProfiles()));
        data.put("startedAt", startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("status", "UP");

        data.put("database", currentDatabase());
        try {
            long users = sysUserMapper.selectCount(null);
            SysUser admin = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin").last("LIMIT 1"));
            data.put("userCount", users);
            data.put("adminExists", admin != null);
            data.put("adminPasswordLength", admin == null || admin.getPassword() == null ? 0 : admin.getPassword().length());
            data.put("adminStatus", admin == null ? null : admin.getStatus());
        } catch (Exception e) {
            data.put("databaseError", e.getMessage());
        }
        return Result.ok(data);
    }

    /**
     * 读取当前连接实际使用的库名，读不到就返回 unknown。
     */
    private String currentDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String url = connection.getMetaData().getURL();
            return (catalog == null ? "unknown" : catalog) + " @ " + url;
        } catch (Exception e) {
            log.debug("读取数据库信息失败：{}", e.getMessage());
            return "unknown";
        }
    }
}
