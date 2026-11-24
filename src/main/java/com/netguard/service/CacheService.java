package com.netguard.service;

import com.netguard.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存服务：优先使用 Redis，Redis 不可用时自动降级为本地内存缓存。
 *
 * <p>降级策略保证在机房未部署 Redis 或 Redis 临时重启时，页面仍能展示设备实时状态，
 * 不会因缓存不可用导致整个监控大屏不可访问。降级后每 60 秒尝试自动恢复。</p>
 */
@Slf4j
@Service
public class CacheService {

    /** 本地内存缓存，仅在降级时使用 */
    private final Map<String, String> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> memoryHash = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    /** Redis 当前是否可用 */
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);
    /** 最近一次探活时间戳，用于控制重试频率 */
    private volatile long lastCheckMillis = 0L;

    private static final long RECHECK_INTERVAL_MS = 60_000L;

    public CacheService(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        if (!"redis".equalsIgnoreCase(appProperties.getCacheMode())) {
            log.warn("缓存模式为 memory，已跳过 Redis 初始化，设备状态将写入本地内存");
            redisAvailable.set(false);
            return;
        }
        boolean ok = ping();
        redisAvailable.set(ok);
        if (ok) {
            log.info("Redis 连接成功，设备实时状态将写入 {} ", com.netguard.common.constant.CacheKeys.DEVICE_STATUS);
        } else {
            log.warn("Redis 不可用，已自动降级为本地内存缓存；每 60 秒尝试重连");
        }
    }

    /**
     * 判断当前是否真的在使用 Redis。
     */
    public boolean isRedisActive() {
        if (!"redis".equalsIgnoreCase(appProperties.getCacheMode())) {
            return false;
        }
        if (redisAvailable.get()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheckMillis > RECHECK_INTERVAL_MS) {
            lastCheckMillis = now;
            boolean ok = ping();
            redisAvailable.set(ok);
            if (ok) {
                log.info("Redis 已恢复连接，缓存重新切换为 Redis");
            }
        }
        return redisAvailable.get();
    }

    private boolean ping() {
        try {
            String pong = redisTemplate.execute(connection -> connection.ping(), true);
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.debug("Redis ping 失败：{}", e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // String 类型
    // ------------------------------------------------------------------

    /**
     * 写入字符串缓存（带过期时间）。
     */
    public void set(String key, String value, Duration ttl) {
        if (isRedisActive()) {
            try {
                redisTemplate.opsForValue().set(key, value, ttl);
                return;
            } catch (Exception e) {
                degrade(e);
            }
        }
        memoryStore.put(key, value);
    }

    public String get(String key) {
        if (isRedisActive()) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                degrade(e);
            }
        }
        return memoryStore.get(key);
    }

    public void delete(String key) {
        if (isRedisActive()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                degrade(e);
            }
        }
        memoryStore.remove(key);
    }

    // ------------------------------------------------------------------
    // Hash 类型：设备实时状态
    // ------------------------------------------------------------------

    public void putHash(String key, String field, String value) {
        if (isRedisActive()) {
            try {
                redisTemplate.opsForHash().put(key, field, value);
                return;
            } catch (Exception e) {
                degrade(e);
            }
        }
        memoryHash.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(field, value);
    }

    public String getHash(String key, String field) {
        if (isRedisActive()) {
            try {
                Object value = redisTemplate.opsForHash().get(key, field);
                return value == null ? null : value.toString();
            } catch (Exception e) {
                degrade(e);
            }
        }
        Map<String, String> map = memoryHash.get(key);
        return map == null ? null : map.get(field);
    }

    public Map<String, String> getAllHash(String key) {
        if (isRedisActive()) {
            try {
                Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
                Map<String, String> result = new HashMap<>(raw.size());
                raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
                return result;
            } catch (Exception e) {
                degrade(e);
            }
        }
        Map<String, String> map = memoryHash.get(key);
        return map == null ? Collections.emptyMap() : new HashMap<>(map);
    }

    public void deleteHash(String key) {
        if (isRedisActive()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                degrade(e);
            }
        }
        memoryHash.remove(key);
    }

    /**
     * 批量写入设备状态，降低网络往返次数。
     */
    public void putAllHash(String key, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (isRedisActive()) {
            try {
                redisTemplate.opsForHash().putAll(key, values);
                return;
            } catch (Exception e) {
                degrade(e);
            }
        }
        memoryHash.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).putAll(values);
    }

    /**
     * 删除 Hash 中的单个字段（设备被删除时调用）。
     */
    public void deleteHashField(String key, String field) {
        if (isRedisActive()) {
            try {
                redisTemplate.opsForHash().delete(key, field);
            } catch (Exception e) {
                degrade(e);
            }
        }
        Map<String, String> map = memoryHash.get(key);
        if (map != null) {
            map.remove(field);
        }
    }

    /**
     * 获取 Hash 全部字段名。
     */
    public Set<String> hashKeys(String key) {
        if (isRedisActive()) {
            try {
                Set<Object> keys = redisTemplate.opsForHash().keys(key);
                Set<String> result = new java.util.HashSet<>(keys.size());
                keys.forEach(k -> result.add(String.valueOf(k)));
                return result;
            } catch (Exception e) {
                degrade(e);
            }
        }
        Map<String, String> map = memoryHash.get(key);
        return map == null ? Collections.emptySet() : map.keySet();
    }

    private void degrade(Exception e) {
        redisAvailable.set(false);
        lastCheckMillis = System.currentTimeMillis();
        log.warn("Redis 操作失败，本次已降级为内存缓存：{}", e.getMessage());
    }
}
