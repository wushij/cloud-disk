package com.clouddisk.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCacheService implements CacheService {

    private record Entry(String value, long expireAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (e.expireAt > 0 && System.currentTimeMillis() > e.expireAt) {
            store.remove(key);
            return null;
        }
        return e.value;
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
        store.put(key, new Entry(value, expireAt));
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
        Entry entry = store.compute(key, (k, old) -> {
            long next = 1;
            long currentExpire = expireAt;
            if (old != null && (old.expireAt == 0 || System.currentTimeMillis() <= old.expireAt)) {
                try {
                    next = Long.parseLong(old.value) + 1;
                    currentExpire = old.expireAt;
                } catch (NumberFormatException ignored) {
                    next = 1;
                }
            }
            return new Entry(String.valueOf(next), currentExpire);
        });
        return entry != null ? Long.parseLong(entry.value) : 1;
    }
}
