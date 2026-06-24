package com.clouddisk.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 普通管理员可被授予的细粒度权限（超级管理员隐式拥有全部）。
 */
public final class AdminPermission {

    public static final String DASHBOARD = "admin:dashboard";
    public static final String USERS = "admin:users";
    public static final String REGISTER = "admin:register";
    public static final String AUDIT = "admin:audit";
    public static final String STORAGE = "admin:storage";
    public static final String SEARCH = "admin:search";

    public static final List<String> ALL_ASSIGNABLE = List.of(
            DASHBOARD, USERS, REGISTER, AUDIT, STORAGE, SEARCH);

    public static final List<String> DEFAULT_FOR_ADMIN = List.of(
            DASHBOARD, USERS, REGISTER, AUDIT, STORAGE, SEARCH);

    private AdminPermission() {
    }

    public static Set<String> parse(String raw) {
        Set<String> set = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return set;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.replace("[", "").replace("]", "").replace("\"", "");
        }
        for (String part : trimmed.split("[,;]")) {
            String p = part.trim();
            if (!p.isEmpty() && ALL_ASSIGNABLE.contains(p)) {
                set.add(p);
            }
        }
        return set;
    }

    public static String serialize(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return String.join(",", permissions);
    }

    public static Set<String> defaults() {
        return new LinkedHashSet<>(DEFAULT_FOR_ADMIN);
    }
}
