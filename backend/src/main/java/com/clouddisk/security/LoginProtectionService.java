package com.clouddisk.security;

import com.clouddisk.cache.CacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoginProtectionService {

    private final CacheService cacheService;
    private final CloudDiskProperties properties;

    public void checkAllowed(String ip, String username) {
        if (isLocked("login:lock:ip:" + ip)) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
        if (StringUtils.hasText(username) && isLocked("login:lock:user:" + username.toLowerCase())) {
            throw new BusinessException("账号登录失败次数过多，请稍后再试");
        }
    }

    public boolean captchaRequired(String ip) {
        long fails = readCount("login:fail:ip:" + ip);
        return fails >= properties.getRateLimit().getCaptchaAfterFailures();
    }

    public void recordFailure(String ip, String username) {
        var rl = properties.getRateLimit();
        long ipFails = increment("login:fail:ip:" + ip, rl.getLoginLockMinutes() * 60L);
        if (ipFails >= rl.getIpBanThreshold()) {
            lock("login:lock:ip:" + ip, rl.getIpBanMinutes() * 60L);
            return;
        }
        if (StringUtils.hasText(username)) {
            String userKey = "login:fail:user:" + username.toLowerCase();
            long userFails = increment(userKey, rl.getLoginLockMinutes() * 60L);
            if (userFails >= rl.getLoginFailMax()) {
                lock("login:lock:user:" + username.toLowerCase(), rl.getLoginLockMinutes() * 60L);
            }
        }
    }

    public void clearOnSuccess(String ip, String username) {
        cacheService.delete("login:fail:ip:" + ip);
        if (StringUtils.hasText(username)) {
            cacheService.delete("login:fail:user:" + username.toLowerCase());
            cacheService.delete("login:lock:user:" + username.toLowerCase());
        }
    }

    private boolean isLocked(String key) {
        return "1".equals(cacheService.get(key));
    }

    private void lock(String key, long ttlSeconds) {
        cacheService.set(key, "1", ttlSeconds);
    }

    private long increment(String key, long ttlSeconds) {
        return cacheService.increment(key, ttlSeconds);
    }

    private long readCount(String key) {
        String v = cacheService.get(key);
        if (v == null) return 0;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
