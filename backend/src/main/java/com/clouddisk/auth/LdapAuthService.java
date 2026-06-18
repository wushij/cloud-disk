package com.clouddisk.auth;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.naming.directory.Attributes;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clouddisk.ldap.enabled", havingValue = "true")
public class LdapAuthService {

    private final LdapTemplate ldapTemplate;
    private final CloudDiskProperties properties;

    public Map<String, String> authenticate(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException("用户名或密码不能为空");
        }
        CloudDiskProperties.Ldap ldap = properties.getLdap();
        String filter = ldap.getUserSearchFilter().replace("{0}", username);
        try {
            boolean ok = ldapTemplate.authenticate(
                    ldap.getUserSearchBase(),
                    filter,
                    password);
            if (!ok) {
                throw new BusinessException("LDAP 认证失败");
            }
            Map<String, String> profile = lookupProfile(username, ldap);
            log.info("LDAP 认证成功: {}", username);
            return profile;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LDAP 认证异常: {}", e.getMessage());
            throw new BusinessException("LDAP 认证失败");
        }
    }

    private Map<String, String> lookupProfile(String username, CloudDiskProperties.Ldap ldap) {
        Map<String, String> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("nickname", username);
        try {
            var results = ldapTemplate.search(
                    LdapQueryBuilder.query()
                            .base(ldap.getUserSearchBase())
                            .filter(ldap.getUserSearchFilter().replace("{0}", username)),
                    (Attributes attrs) -> attrs);
            if (!results.isEmpty()) {
                Attributes attrs = results.get(0);
                if (attrs.get("cn") != null) {
                    profile.put("nickname", attrs.get("cn").get().toString());
                }
                if (attrs.get("mail") != null) {
                    profile.put("email", attrs.get("mail").get().toString());
                }
            }
        } catch (Exception e) {
            log.debug("LDAP 属性读取失败: {}", e.getMessage());
        }
        return profile;
    }
}
