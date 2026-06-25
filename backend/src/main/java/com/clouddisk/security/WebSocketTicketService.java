package com.clouddisk.security;

import com.clouddisk.cache.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketTicketService {

    private static final String PREFIX = "ws:ticket:";
    private static final int TTL_SECONDS = 60;

    private final CacheService cacheService;

    public String issue(long userId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        cacheService.set(PREFIX + ticket, String.valueOf(userId), TTL_SECONDS);
        return ticket;
    }

    public Long consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        String key = PREFIX + ticket;
        String userId = cacheService.get(key);
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        cacheService.delete(key);
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
