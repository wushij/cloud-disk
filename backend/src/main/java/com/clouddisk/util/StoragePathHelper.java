package com.clouddisk.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * MinIO / 本地存储对象键命名规范（可读、可维护）：
 * <ul>
 *   <li>用户文件：users/{username}/{uuid}_{fileName}</li>
 *   <li>用户头像：头像/{username}.jpg</li>
 *   <li>团队头像：团队头像/{teamName}_{spaceId}.jpg（spaceId 保证同名团队不冲突）</li>
 * </ul>
 */
public final class StoragePathHelper {

    private StoragePathHelper() {
    }

    /** 路径段消毒：保留中文，替换文件系统/对象存储非法字符 */
    public static String sanitizeSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "unknown";
        }
        return raw.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
    }

    public static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static String userFilePath(String username, String fileName) {
        String safeUser = sanitizeSegment(username);
        String safeName = sanitizeFileName(fileName);
        return "users/" + safeUser + "/"
                + UUID.randomUUID().toString().replace("-", "") + "_" + safeName;
    }

    public static String userAvatarPath(String username) {
        return userAvatarPath(username, "jpg");
    }

    public static String userAvatarPath(String username, String ext) {
        String safeExt = normalizeImageExt(ext);
        return "头像/" + sanitizeSegment(username) + "." + safeExt;
    }

    private static String normalizeImageExt(String ext) {
        if (!StringUtils.hasText(ext)) {
            return "jpg";
        }
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "jpeg" -> "jpg";
            case "jpg", "png", "gif", "webp" -> ext.toLowerCase(Locale.ROOT);
            default -> "jpg";
        };
    }

    public static String teamAvatarPath(String teamName, long spaceId) {
        return "团队头像/" + sanitizeSegment(teamName) + "_" + spaceId + ".jpg";
    }
}
