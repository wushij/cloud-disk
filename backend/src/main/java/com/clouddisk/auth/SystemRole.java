package com.clouddisk.auth;

import java.util.Locale;

/**
 * 系统级用户角色（与团队 {@code TeamRole} 独立）。
 * <ul>
 *   <li>SUPER_ADMIN — 超级管理员，默认账号 admin，可管理全部用户及管理员角色</li>
 *   <li>ADMIN — 管理员，仅可管理普通用户（USER）</li>
 *   <li>USER — 普通用户</li>
 * </ul>
 */
public final class SystemRole {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    private SystemRole() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return USER;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isSuperAdmin(String role) {
        return SUPER_ADMIN.equals(normalize(role));
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(normalize(role));
    }

    public static boolean isAnyAdmin(String role) {
        String r = normalize(role);
        return SUPER_ADMIN.equals(r) || ADMIN.equals(r);
    }

    public static boolean isUser(String role) {
        return USER.equals(normalize(role));
    }

    /** 角色层级：数值越大权限越高 */
    public static int level(String role) {
        return switch (normalize(role)) {
            case SUPER_ADMIN -> 3;
            case ADMIN -> 2;
            default -> 1;
        };
    }

    public static boolean isProtectedSuperAdmin(UserRef user) {
        return user != null && "admin".equalsIgnoreCase(user.username());
    }

    public record UserRef(Long id, String username, String role) {
    }
}
