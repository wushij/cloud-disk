package com.clouddisk.security;

import com.clouddisk.config.CloudDiskProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态 API 安全配置服务（支持后台界面即时切换开关）
 * <p>
 * 优先从 Redis 缓存 "security:config" 中读取配置；
 * 若 Redis 未配置，则回退到 application.yml / CloudDiskProperties 的初始默认配置。
 */
@Service
@RequiredArgsConstructor
public class SecurityConfigService {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigService.class);
    private static final String REDIS_KEY = "security:config";

    private final CloudDiskProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 获取完整安全配置 Map（供管理员后台调用）
     */
    public Map<String, Object> getConfig() {
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取安全配置失败，使用配置文件默认值: {}", e.getMessage());
        }
        return getDefaultConfig();
    }

    /**
     * 获取对外公开的安全开关 Map（供 /api/auth/config 及登录前协商调用）
     */
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> full = getConfig();
        Map<String, Object> pub = new LinkedHashMap<>();
        pub.put("timestampEnabled", Boolean.TRUE.equals(full.get("timestampEnabled")));
        pub.put("nonceEnabled", Boolean.TRUE.equals(full.get("nonceEnabled")));
        boolean signActive = Boolean.TRUE.equals(full.get("sm3SignEnabled"))
                || Boolean.TRUE.equals(full.get("sm2SignEnabled"));
        pub.put("sm3SignEnabled", signActive);
        pub.put("sm2SignEnabled", signActive);
        pub.put("sm4EncryptEnabled", Boolean.TRUE.equals(full.get("sm4EncryptEnabled")));
        return pub;
    }

    /**
     * 修改并保存安全配置（管理员后台调用，保存至 Redis 热生效）
     */
    public Map<String, Object> updateConfig(Map<String, Object> req) {
        Map<String, Object> current = getConfig();

        if (req.containsKey("timestampEnabled")) {
            current.put("timestampEnabled", Boolean.TRUE.equals(req.get("timestampEnabled")));
        }
        if (req.containsKey("nonceEnabled")) {
            current.put("nonceEnabled", Boolean.TRUE.equals(req.get("nonceEnabled")));
        }
        if (req.containsKey("sm3SignEnabled")) {
            boolean active = Boolean.TRUE.equals(req.get("sm3SignEnabled"));
            current.put("sm3SignEnabled", active);
            current.put("sm2SignEnabled", active);
        } else if (req.containsKey("sm2SignEnabled")) {
            boolean active = Boolean.TRUE.equals(req.get("sm2SignEnabled"));
            current.put("sm3SignEnabled", active);
            current.put("sm2SignEnabled", active);
        }
        if (req.containsKey("sm4EncryptEnabled")) {
            current.put("sm4EncryptEnabled", Boolean.TRUE.equals(req.get("sm4EncryptEnabled")));
        }
        if (req.containsKey("timestampWindowMs")) {
            try {
                long windowMs = Long.parseLong(String.valueOf(req.get("timestampWindowMs")));
                if (windowMs >= 1000L && windowMs <= 3600000L) { // 1秒~1小时
                    current.put("timestampWindowMs", windowMs);
                }
            } catch (Exception ignored) {}
        }

        try {
            stringRedisTemplate.opsForValue().set(REDIS_KEY, objectMapper.writeValueAsString(current));
            log.info("API 安全配置已热更新: {}", current);
        } catch (Exception e) {
            log.error("保存安全配置至 Redis 失败: {}", e.getMessage(), e);
        }
        return current;
    }

    // ── 便捷 Getter 方法（供 ApiSecurityFilter 与 AuthController 快速判断） ──

    public boolean isTimestampEnabled() {
        Object val = getConfig().get("timestampEnabled");
        return val != null ? Boolean.TRUE.equals(val) : properties.getApiSecurity().isTimestampEnabled();
    }

    public long getTimestampWindowMs() {
        Object val = getConfig().get("timestampWindowMs");
        if (val instanceof Number) return ((Number) val).longValue();
        if (val != null) {
            try { return Long.parseLong(String.valueOf(val)); } catch (Exception ignored) {}
        }
        return properties.getApiSecurity().getTimestampWindowMs();
    }

    public boolean isNonceEnabled() {
        Object val = getConfig().get("nonceEnabled");
        return val != null ? Boolean.TRUE.equals(val) : properties.getApiSecurity().isNonceEnabled();
    }

    public boolean isSm3SignEnabled() {
        Object val3 = getConfig().get("sm3SignEnabled");
        Object val2 = getConfig().get("sm2SignEnabled");
        if (val3 != null) return Boolean.TRUE.equals(val3);
        if (val2 != null) return Boolean.TRUE.equals(val2);
        return properties.getApiSecurity().isSm3SignEnabled() || properties.getApiSecurity().isSm2SignEnabled();
    }

    public boolean isSm4EncryptEnabled() {
        Object val = getConfig().get("sm4EncryptEnabled");
        return val != null ? Boolean.TRUE.equals(val) : properties.getApiSecurity().isSm4EncryptEnabled();
    }

    private Map<String, Object> getDefaultConfig() {
        CloudDiskProperties.ApiSecurity sec = properties.getApiSecurity();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestampEnabled", sec.isTimestampEnabled());
        map.put("timestampWindowMs", sec.getTimestampWindowMs());
        map.put("nonceEnabled", sec.isNonceEnabled());
        boolean signActive = sec.isSm3SignEnabled() || sec.isSm2SignEnabled();
        map.put("sm3SignEnabled", signActive);
        map.put("sm2SignEnabled", signActive);
        map.put("sm4EncryptEnabled", sec.isSm4EncryptEnabled());
        return map;
    }
}
