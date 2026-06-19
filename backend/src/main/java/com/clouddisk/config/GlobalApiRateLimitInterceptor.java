package com.clouddisk.config;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全站 API IP 级滑动窗口限流，防止压测/爬虫打满应用线程。
 */
@Component
@RequiredArgsConstructor
public class GlobalApiRateLimitInterceptor implements HandlerInterceptor {

    private final CacheService cacheService;
    private final CloudDiskProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        if (uri.startsWith("/api/auth/captcha")) {
            return true;
        }
        String ip = ClientIpUtil.resolve(request);
        String key = "rate:api:ip:" + ip;
        long count = cacheService.increment(key, 60);
        if (count > properties.getRateLimit().getApiPerMinute()) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
        return true;
    }
}
