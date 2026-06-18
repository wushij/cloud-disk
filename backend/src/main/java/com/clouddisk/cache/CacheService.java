package com.clouddisk.cache;

import java.util.Map;

public interface CacheService {
    String get(String key);

    void set(String key, String value, long ttlSeconds);

    void delete(String key);

    long increment(String key, long ttlSeconds);

    /**
     * 返回缓存统计信息（L1/L2 命中率、条目数等）
     */
    default Map<String, Object> stats() {
        return Map.of();
    }

    /**
     * 按前缀批量清除缓存
     */
    default void evictByPrefix(String prefix) {
        // 默认无操作，子类可按需实现
    }
}
