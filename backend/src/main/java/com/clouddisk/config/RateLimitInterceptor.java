package com.clouddisk.config;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final CacheService cacheService;
    private final CloudDiskProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/upload") && !uri.equals("/api/files/simple")) {
            return true;
        }
        try {
            long userId = AuthService.currentUserId();
            String key = "rate:upload:" + userId;
            long count = cacheService.increment(key, 60);
            if (count > properties.getRateLimit().getUploadPerMinute()) {
                throw new BusinessException("上传过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
        }
        return true;
    }
}
