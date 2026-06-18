package com.clouddisk.security;

import com.clouddisk.cache.InMemoryCacheService;
import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginProtectionServiceTest {

    private LoginProtectionService protection;

    @BeforeEach
    void setUp() {
        CloudDiskProperties props = new CloudDiskProperties();
        props.getRateLimit().setLoginFailMax(3);
        props.getRateLimit().setLoginLockMinutes(15);
        props.getRateLimit().setCaptchaAfterFailures(2);
        protection = new LoginProtectionService(new InMemoryCacheService(), props);
    }

    @Test
    void captchaRequired_afterFailures() {
        protection.recordFailure("1.1.1.1", "alice");
        assertFalse(protection.captchaRequired("1.1.1.1"));
        protection.recordFailure("1.1.1.1", "alice");
        assertTrue(protection.captchaRequired("1.1.1.1"));
    }

    @Test
    void accountLocked_afterMaxFailures() {
        for (int i = 0; i < 3; i++) {
            protection.recordFailure("1.1.1.1", "bob");
        }
        assertThrows(BusinessException.class, () -> protection.checkAllowed("1.1.1.1", "bob"));
    }

    @Test
    void clearOnSuccess_resetsFailures() {
        protection.recordFailure("1.1.1.1", "carol");
        protection.recordFailure("1.1.1.1", "carol");
        protection.clearOnSuccess("1.1.1.1", "carol");
        assertFalse(protection.captchaRequired("1.1.1.1"));
    }
}
