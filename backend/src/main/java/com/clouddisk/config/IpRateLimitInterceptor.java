package com.clouddisk.config;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private final CacheService cacheService;
    private final CloudDiskProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        String bucket = bucket(uri);
        if (bucket == null) {
            return true;
        }
        String ip = ClientIpUtil.resolve(request);
        String key = "rate:ip:" + ip + ":" + bucket;
        long count = cacheService.increment(key, 60);
        if (count > limit(bucket)) {
            throw new BusinessException("请求过于频繁，请稍后再试", "RATE_LIMITED");
        }
        return true;
    }

    private String bucket(String uri) {
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/ldap/login")) {
            return "login";
        }
        if (uri.startsWith("/api/auth/register")) {
            return "register";
        }
        if (uri.startsWith("/share/")) {
            return "share";
        }
        return null;
    }

    private int limit(String bucket) {
        return switch (bucket) {
            case "login" -> properties.getRateLimit().getLoginPerMinute();
            case "register" -> properties.getRateLimit().getRegisterPerMinute();
            case "share" -> properties.getRateLimit().getSharePerMinute();
            default -> Integer.MAX_VALUE;
        };
    }
}
