package com.clouddisk.service;

import com.clouddisk.auth.AdminPermission;
import com.clouddisk.auth.SystemRole;
import com.clouddisk.common.BusinessException;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private final UserMapper userMapper;

    public User requireActor() {
        long userId = AuthService.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !SystemRole.isAnyAdmin(user.getRole())) {
            throw new BusinessException("需要管理员权限");
        }
        return user;
    }

    public User requireSuperAdmin() {
        User actor = requireActor();
        if (!isSuperAdmin(actor)) {
            throw new BusinessException("需要超级管理员权限");
        }
        return actor;
    }

    public boolean isSuperAdmin(User user) {
        if (user == null) {
            return false;
        }
        if (SystemRole.isSuperAdmin(user.getRole())) {
            return true;
        }
        return SystemRole.isProtectedSuperAdmin(toRef(user));
    }

    public boolean isAnyAdmin(User user) {
        return user != null && SystemRole.isAnyAdmin(user.getRole());
    }

    public Set<String> resolvePermissions(User user) {
        if (user == null) {
            return Set.of();
        }
        if (isSuperAdmin(user)) {
            return Set.of("admin:*", "file:*", "share:*");
        }
        if (!SystemRole.isAdmin(user.getRole())) {
            return Set.of("file:read", "file:write", "share:write");
        }
        return new LinkedHashSet<>(AdminPermission.DEFAULT_FOR_ADMIN);
    }

    public void requirePermission(User actor, String permission) {
        if (!hasPermission(actor, permission)) {
            throw new BusinessException("权限不足");
        }
    }

    public boolean hasPermission(User actor, String permission) {
        if (actor == null) {
            return false;
        }
        if (isSuperAdmin(actor)) {
            return true;
        }
        return resolvePermissions(actor).contains(permission);
    }

    public User requireManageableTarget(User actor, Long targetUserId) {
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if (!canManageUser(actor, target)) {
            throw new BusinessException("无权管理该用户");
        }
        return target;
    }

    public boolean canManageUser(User actor, User target) {
        if (actor == null || target == null) {
            return false;
        }
        if (SystemRole.isProtectedSuperAdmin(toRef(target))) {
            return false;
        }
        if (isSuperAdmin(actor)) {
            return true;
        }
        if (!SystemRole.isAdmin(actor.getRole())) {
            return false;
        }
        return SystemRole.isUser(target.getRole());
    }

    public boolean canAssignRole(User actor, String newRole, User target) {
        if (actor == null || target == null) {
            return false;
        }
        if (SystemRole.isProtectedSuperAdmin(toRef(target))) {
            return false;
        }
        if (!isSuperAdmin(actor)) {
            return false;
        }
        String role = SystemRole.normalize(newRole);
        return SystemRole.ADMIN.equals(role) || SystemRole.USER.equals(role);
    }

    public void assertRoleAssignable(User actor, String newRole, User target) {
        if (!canAssignRole(actor, newRole, target)) {
            if (!isSuperAdmin(actor)) {
                throw new BusinessException("仅超级管理员可调整管理员角色");
            }
            throw new BusinessException("非法的角色值，必须为 ADMIN 或 USER");
        }
    }

    private static SystemRole.UserRef toRef(User user) {
        return new SystemRole.UserRef(user.getId(), user.getUsername(), user.getRole());
    }
}
