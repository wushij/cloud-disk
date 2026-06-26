package com.clouddisk.config;

import com.clouddisk.common.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CsrfInterceptor implements HandlerInterceptor {

    private final Environment environment;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod"));

        // 1. 获取或生成 XSRF-TOKEN
        String xsrfCookieVal = getCookieValue(request, "XSRF-TOKEN");
        if (xsrfCookieVal == null || xsrfCookieVal.isBlank()) {
            xsrfCookieVal = UUID.randomUUID().toString().replace("-", "");
            ResponseCookie xsrfCookie = ResponseCookie.from("XSRF-TOKEN", xsrfCookieVal)
                    .path("/")
                    .secure(isProd)
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, xsrfCookie.toString());
        }

        // 2. 仅对可能修改状态的方法进行 CSRF 验证（POST, PUT, DELETE, PATCH）
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            if (isCsrfExemptPath(request.getRequestURI())) {
                return true;
            }
            // 移动端 / API 客户端：Bearer 鉴权不依赖 Cookie，跳过 CSRF
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return true;
            }
            // 仅当存在 Sa-Token 会话 Cookie 时强制 CSRF（PC 浏览器 Cookie 模式）
            String authCookie = getCookieValue(request, "Authorization");
            if (authCookie != null && !authCookie.isBlank()) {
                String xsrfHeaderVal = request.getHeader("X-XSRF-TOKEN");
                if (xsrfHeaderVal == null || !xsrfHeaderVal.equals(xsrfCookieVal)) {
                    throw new BusinessException("CSRF 验证失败，请刷新页面重试", "CSRF_ERROR");
                }
            }
        }
        return true;
    }

    /** 登录等公开接口不校验 CSRF（尚未建立会话或客户端无 XSRF 头） */
    private boolean isCsrfExemptPath(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.equals("/api/auth/login")
                || uri.equals("/api/auth/register")
                || uri.equals("/api/auth/ldap/login")
                || uri.equals("/api/auth/sso/ticket")
                || uri.equals("/api/auth/sync-cookie");
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (name.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
