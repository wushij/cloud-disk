package com.clouddisk.team;

import java.util.Locale;
import java.util.Set;

/**
 * 团队角色与能力矩阵。
 * <ul>
 *   <li>VIEWER — 只读：浏览、下载、预览</li>
 *   <li>MEMBER — 可写：上传、新建文件夹；仅可改删自己上传的文件/创建的文件夹</li>
 *   <li>ADMIN — 可管理团队与全部团队文件</li>
 *   <li>OWNER — 同 ADMIN，且可解散团队</li>
 * </ul>
 */
public final class TeamRole {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";
    public static final String VIEWER = "VIEWER";

    private static final Set<String> INVITABLE = Set.of(MEMBER, ADMIN, VIEWER);
    private static final Set<String> ASSIGNABLE = Set.of(MEMBER, ADMIN, VIEWER);

    private TeamRole() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return MEMBER;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValidInviteRole(String role) {
        return INVITABLE.contains(normalize(role));
    }

    public static boolean isAssignableRole(String role) {
        return ASSIGNABLE.contains(normalize(role));
    }

    public static boolean isOwner(String role) {
        return OWNER.equals(normalize(role));
    }

    public static boolean isAdminOrOwner(String role) {
        String r = normalize(role);
        return OWNER.equals(r) || ADMIN.equals(r);
    }

    public static boolean canWrite(String role) {
        return !VIEWER.equals(normalize(role));
    }

    public static boolean canManageTeam(String role) {
        return isAdminOrOwner(role);
    }

    public static boolean canShare(String role) {
        return isAdminOrOwner(role);
    }

    public static boolean canDeleteAnyTeamContent(String role) {
        return isAdminOrOwner(role);
    }
}
