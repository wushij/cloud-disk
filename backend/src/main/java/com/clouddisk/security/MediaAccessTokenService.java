package com.clouddisk.security;

import com.clouddisk.cache.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaAccessTokenService {

    private static final String PREFIX = "media:";
    private static final int TTL_SECONDS = 900;

    private final CacheService cacheService;

    public Map<String, Object> issue(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        cacheService.set(PREFIX + token, String.valueOf(userId), TTL_SECONDS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mediaToken", token);
        result.put("expiresIn", TTL_SECONDS);
        return result;
    }

    public Long resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String val = cacheService.get(PREFIX + token);
        if (!StringUtils.hasText(val)) {
            return null;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
