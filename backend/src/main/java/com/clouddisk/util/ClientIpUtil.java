package com.clouddisk.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 获取客户端 IP。生产环境请由 Nginx 设置 {@code X-Real-IP}，并禁止客户端直连后端绕过。
 */
public final class ClientIpUtil {

    private ClientIpUtil() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) return "unknown";
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String current() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        return resolve(attrs.getRequest());
    }
}
