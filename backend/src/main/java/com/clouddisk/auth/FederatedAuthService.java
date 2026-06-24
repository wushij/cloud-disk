package com.clouddisk.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.clouddisk.common.BusinessException;
import com.clouddisk.common.UserStatus;
import com.clouddisk.config.CloudDiskProperties;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.service.AuditLogService;
import com.clouddisk.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FederatedAuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserCacheService userCacheService;
    private final AuditLogService auditLogService;
    private final CloudDiskProperties properties;
    private final ObjectProvider<LdapAuthService> ldapAuthService;
    private final ObjectProvider<SsoAuthService> ssoAuthService;

    public Map<String, Object> authProviders() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ldapEnabled", properties.getLdap().isEnabled());
        m.put("ssoEnabled", properties.getSso().isEnabled());
        if (properties.getSso().isEnabled()) {
            SsoAuthService sso = ssoAuthService.getIfAvailable();
            if (sso != null) {
                m.put("sso", sso.buildAuthorizePayload());
            }
        }
        return m;
    }

    public Map<String, Object> loginWithLdap(String username, String password) {
        if (!properties.getLdap().isEnabled()) {
            throw new BusinessException("LDAP 未启用");
        }
        LdapAuthService ldap = ldapAuthService.getIfAvailable();
        if (ldap == null) {
            throw new BusinessException("LDAP 服务不可用");
        }
        Map<String, String> profile = ldap.authenticate(username, password);
        User user = provisionUser(profile.get("username"), profile.get("nickname"), profile.get("email"), "LDAP");
        return finishLogin(user, "LDAP");
    }

    public Map<String, String> loginWithSsoCode(String code, String state) {
        if (!properties.getSso().isEnabled()) {
            throw new BusinessException("SSO 未启用");
        }
        SsoAuthService sso = ssoAuthService.getIfAvailable();
        if (sso == null) {
            throw new BusinessException("SSO 服务不可用");
        }
        Map<String, String> profile = sso.exchangeCode(code, state);
        User user = provisionUser(profile.get("username"), profile.get("nickname"), profile.get("email"), "SSO");
        Map<String, Object> auth = finishLogin(user, "SSO");
        Map<String, String> result = new HashMap<>();
        result.put("token", String.valueOf(auth.get("token")));
        result.put("username", String.valueOf(auth.get("username")));
        result.put("nickname", String.valueOf(auth.get("nickname")));
        result.put("redirectUrl", sso.buildFrontendRedirectUrl(result));
        return result;
    }

    private User provisionUser(String username, String nickname, String email, String source) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            if ("LDAP".equals(source) && !properties.getLdap().isAutoProvision()) {
                throw new BusinessException("用户不存在，请联系管理员开通");
            }
            user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setNickname(StringUtils.hasText(nickname) ? nickname : username);
            user.setEmail(email);
            user.setStatus(UserStatus.ACTIVE);
            user.setRole("USER");
            user.setStorageQuota(UserStatus.DEFAULT_USER_QUOTA_BYTES);
            userMapper.insert(user);
        } else if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        } else if (user.getStatus() != null && user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException("您的账号尚未通过审核，请等待管理员批准后再登录");
        }
        return user;
    }

    private Map<String, Object> finishLogin(User user, String source) {
        StpUtil.login(user.getId());
        auditLogService.log(user.getId(), user.getUsername(), "LOGIN_" + source, "user",
                String.valueOf(user.getId()), source + " 登录成功");
        userCacheService.evict(user.getId());
        Map<String, Object> m = new HashMap<>();
        m.put("token", StpUtil.getTokenValue());
        m.put("username", user.getUsername());
        m.put("nickname", user.getNickname());
        m.put("role", user.getRole() != null ? user.getRole() : "USER");
        m.put("userId", user.getId());
        return m;
    }
}
