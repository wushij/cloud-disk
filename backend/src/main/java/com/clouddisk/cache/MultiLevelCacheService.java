package com.clouddisk.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * 多级缓存服务：L1（本地 ConcurrentHashMap）+ L2（Redis）
 * <ul>
 *   <li>get: 先查 L1 -> 未命中查 L2 -> L2 命中则回填 L1</li>
 *   <li>set: 同时写 L1 + L2</li>
 *   <li>delete: 同时删 L1 + L2</li>
 * </ul>
 */
@Slf4j
public class MultiLevelCacheService implements CacheService {

    private static final int L1_MAX_SIZE = 10000;

    private final StringRedisTemplate redis;

    /** L1 本地缓存：key -> Entry(value, expireAtMs) */
    private final ConcurrentHashMap<String, Entry> l1Cache = new ConcurrentHashMap<>();

    /** 统计 */
    private final AtomicLong l1Hits = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    private record Entry(String value, long expireAt) {}

    public MultiLevelCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        // L1
        Entry l1 = l1Cache.get(key);
        if (l1 != null) {
            if (l1.expireAt == 0 || System.currentTimeMillis() < l1.expireAt) {
                l1Hits.incrementAndGet();
                return l1.value;
            }
            l1Cache.remove(key);
        }
        // L2 (Redis)
        try {
            String val = redis.opsForValue().get(key);
            if (val != null) {
                l2Hits.incrementAndGet();
                // 回填 L1（从 Redis 获取 TTL）
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                long ttlSec = (ttl != null && ttl > 0) ? ttl : 300;
                long expireAt = System.currentTimeMillis() + ttlSec * 1000;
                putL1(key, val, expireAt);
                return val;
            }
        } catch (Exception e) {
            log.warn("L2 Redis get 失败 key={}: {}", key, e.getMessage());
        }
        misses.incrementAndGet();
        return null;
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        // L1
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
        putL1(key, value, expireAt);
        // L2
        try {
            if (ttlSeconds > 0) {
                redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            } else {
                redis.opsForValue().set(key, value);
            }
        } catch (Exception e) {
            log.warn("L2 Redis set 失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        l1Cache.remove(key);
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("L2 Redis delete 失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        // 仅使用 Redis 保证原子性，同时更新 L1
        try {
            Long v = redis.opsForValue().increment(key);
            if (v != null && v == 1L && ttlSeconds > 0) {
                redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
            // 同步 L1
            if (v != null) {
                long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
                putL1(key, String.valueOf(v), expireAt);
            }
            return v != null ? v : 0;
        } catch (Exception e) {
            log.warn("Redis increment 失败 key={}: {}", key, e.getMessage());
            return 0;
        }
    }

    @Override
    public Map<String, Object> stats() {
        long total = l1Hits.get() + l2Hits.get() + misses.get();
        double hitRate = total > 0 ? (l1Hits.get() + l2Hits.get()) * 1.0 / total : 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("l1Size", l1Cache.size());
        m.put("l1Hits", l1Hits.get());
        m.put("l2Hits", l2Hits.get());
        m.put("misses", misses.get());
        m.put("total", total);
        m.put("hitRate", Math.round(hitRate * 10000) / 100.0);
        m.put("level", "multi-level");
        return m;
    }

    @Override
    public void evictByPrefix(String prefix) {
        // L1: 遍历删除前缀匹配的 key
        l1Cache.keySet().removeIf(k -> k.startsWith(prefix));
        // L2: Redis SCAN 删除（使用 keys 模式，生产环境建议 SCAN）
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis evictByPrefix 失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    /**
     * 定时清理 L1 过期条目（每 5 分钟），避免内存中积压无效缓存
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredL1() {
        long now = System.currentTimeMillis();
        int before = l1Cache.size();
        l1Cache.entrySet().removeIf(e -> e.getValue().expireAt > 0 && e.getValue().expireAt <= now);
        int removed = before - l1Cache.size();
        if (removed > 0) {
            log.debug("L1 定时清理: 移除 {} 条过期缓存，剩余 {} 条", removed, l1Cache.size());
        }
    }

    // ---------- 内部方法 ----------

    private void putL1(String key, String value, long expireAt) {
        // 超过最大容量时随机淘汰
        if (l1Cache.size() >= L1_MAX_SIZE) {
            Iterator<String> it = l1Cache.keySet().iterator();
            int evictCount = L1_MAX_SIZE / 10; // 淘汰 10%
            while (it.hasNext() && evictCount-- > 0) {
                it.next();
                it.remove();
            }
        }
        l1Cache.put(key, new Entry(value, expireAt));
    }
}
