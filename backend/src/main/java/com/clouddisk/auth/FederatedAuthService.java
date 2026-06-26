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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import com.clouddisk.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

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
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

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

        String ticket = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> ticketData = new HashMap<>();
        ticketData.put("token", String.valueOf(auth.get("token")));
        ticketData.put("username", String.valueOf(auth.get("username")));
        ticketData.put("nickname", String.valueOf(auth.get("nickname")));
        ticketData.put("role", String.valueOf(auth.get("role")));

        try {
            cacheService.set("sso:ticket:" + ticket, objectMapper.writeValueAsString(ticketData), 60);
        } catch (Exception e) {
            throw new BusinessException("SSO 登录失败");
        }

        Map<String, String> result = new HashMap<>();
        result.put("redirectUrl", sso.buildFrontendRedirectUrl(ticket));
        return result;
    }

    public Map<String, Object> loginWithSsoTicket(String ticket, HttpServletResponse response) {
        String key = "sso:ticket:" + ticket;
        String json = cacheService.get(key);
        if (json == null || json.isBlank()) {
            throw new BusinessException("票据无效或已过期");
        }
        cacheService.delete(key);
        try {
            Map<String, String> ticketData = objectMapper.readValue(json, new TypeReference<>() {});
            String token = ticketData.get("token");
            String username = ticketData.get("username");
            String nickname = ticketData.get("nickname");
            String role = ticketData.get("role");

            // Write session Cookie
            org.springframework.web.context.request.ServletRequestAttributes attributes = 
                    (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            boolean isSecure = false;
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                isSecure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            }
            org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie.from("Authorization", token)
                    .httpOnly(true)
                    .secure(isSecure || environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")))
                    .path("/")
                    .maxAge(86400)
                    .sameSite("Lax")
                    .build();
            response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("username", username);
            result.put("nickname", nickname);
            result.put("role", role);
            return result;
        } catch (Exception e) {
            throw new BusinessException("SSO 票据校验失败");
        }
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
