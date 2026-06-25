package com.clouddisk.config;

import cn.dev33.satoken.stp.StpInterface;
import com.clouddisk.auth.SystemRole;
import com.clouddisk.cache.CacheService;
import com.clouddisk.entity.User;
import com.clouddisk.service.AdminAccessService;
import com.clouddisk.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private static final String PERM_KEY_PREFIX = "sa:perm:";
    private static final String ROLE_KEY_PREFIX = "sa:role:";
    private static final long CACHE_TTL_SECONDS = 300;

    private final UserCacheService userCacheService;
    private final AdminAccessService adminAccessService;
    private final CacheService cacheService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        long userId = parseLoginId(loginId);
        String cacheKey = PERM_KEY_PREFIX + userId;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? List.of() : Arrays.asList(cached.split(","));
        }
        User user = userCacheService.getById(userId);
        if (user == null) {
            cacheService.set(cacheKey, "", CACHE_TTL_SECONDS);
            return List.of();
        }
        List<String> perms = new ArrayList<>(adminAccessService.resolvePermissions(user));
        cacheService.set(cacheKey, String.join(",", perms), CACHE_TTL_SECONDS);
        return perms;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        long userId = parseLoginId(loginId);
        String cacheKey = ROLE_KEY_PREFIX + userId;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return List.of(cached);
        }
        User user = userCacheService.getById(userId);
        String role = user == null || user.getRole() == null
                ? SystemRole.USER
                : SystemRole.normalize(user.getRole());
        cacheService.set(cacheKey, role, CACHE_TTL_SECONDS);
        return List.of(role);
    }

    public static void evictAuthCache(CacheService cacheService, Long userId) {
        if (userId == null) {
            return;
        }
        cacheService.delete(PERM_KEY_PREFIX + userId);
        cacheService.delete(ROLE_KEY_PREFIX + userId);
    }

    private static long parseLoginId(Object loginId) {
        return Long.parseLong(loginId.toString());
    }
}
