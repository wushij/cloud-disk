package com.clouddisk.service;

import com.clouddisk.cache.CacheService;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final String NULL_MARKER = "NULL";
    private static final long TTL_BASE = 3600;
    private static final long TTL_JITTER = 600;
    private static final long NULL_TTL = 30;

    private final CacheService cacheService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public User getById(Long id) {
        if (id == null) return null;
        String key = "user:" + id;
        String cached = cacheService.get(key);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                return null;
            }
            try {
                return objectMapper.readValue(cached, User.class);
            } catch (JsonProcessingException ignored) {
                cacheService.delete(key);
            }
        }
        User user = userMapper.selectById(id);
        if (user != null) {
            try {
                long ttl = TTL_BASE + ThreadLocalRandom.current().nextLong(TTL_JITTER);
                cacheService.set(key, objectMapper.writeValueAsString(user), ttl);
            } catch (JsonProcessingException ignored) {
            }
        } else {
            cacheService.set(key, NULL_MARKER, NULL_TTL);
        }
        return user;
    }

    public void evict(Long id) {
        if (id != null) {
            cacheService.delete("user:" + id);
        }
    }
}
