package com.clouddisk.service;

import com.clouddisk.cache.CacheService;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final long TTL = 3600;

    private final CacheService cacheService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public User getById(Long id) {
        if (id == null) return null;
        String key = "user:" + id;
        String cached = cacheService.get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, User.class);
            } catch (JsonProcessingException ignored) {
                cacheService.delete(key);
            }
        }
        User user = userMapper.selectById(id);
        if (user != null) {
            try {
                cacheService.set(key, objectMapper.writeValueAsString(user), TTL);
            } catch (JsonProcessingException ignored) {
            }
        }
        return user;
    }

    public void evict(Long id) {
        if (id != null) {
            cacheService.delete("user:" + id);
        }
    }
}
