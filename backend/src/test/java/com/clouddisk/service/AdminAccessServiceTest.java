package com.clouddisk.service;

import com.clouddisk.auth.SystemRole;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AdminAccessServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminAccessService adminAccessService;

    private User superAdmin;
    private User regularAdmin;
    private User normalUser;

    @BeforeEach
    void setUp() {
        superAdmin = user("admin", SystemRole.SUPER_ADMIN);
        regularAdmin = user("mgr", SystemRole.ADMIN);
        normalUser = user("alice", SystemRole.USER);
    }

    @Test
    void superAdminCanManageEveryoneExceptProtectedAccount() {
        assertTrue(adminAccessService.canManageUser(superAdmin, regularAdmin));
        assertTrue(adminAccessService.canManageUser(superAdmin, normalUser));
        assertFalse(adminAccessService.canManageUser(superAdmin, superAdmin));
    }

    @Test
    void regularAdminCanOnlyManageUsers() {
        assertTrue(adminAccessService.canManageUser(regularAdmin, normalUser));
        assertFalse(adminAccessService.canManageUser(regularAdmin, regularAdmin));
        assertFalse(adminAccessService.canManageUser(regularAdmin, superAdmin));
    }

    @Test
    void onlySuperAdminCanAssignAdminRole() {
        assertTrue(adminAccessService.canAssignRole(superAdmin, SystemRole.ADMIN, normalUser));
        assertFalse(adminAccessService.canAssignRole(regularAdmin, SystemRole.ADMIN, normalUser));
        assertFalse(adminAccessService.canAssignRole(superAdmin, SystemRole.ADMIN, superAdmin));
    }

    private static User user(String username, String role) {
        User u = new User();
        u.setId((long) username.hashCode());
        u.setUsername(username);
        u.setRole(role);
        return u;
    }
}
