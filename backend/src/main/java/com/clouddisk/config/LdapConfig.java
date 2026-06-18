package com.clouddisk.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
@ConditionalOnProperty(name = "clouddisk.ldap.enabled", havingValue = "true")
public class LdapConfig {

    @Bean
    public LdapContextSource ldapContextSource(CloudDiskProperties properties) {
        CloudDiskProperties.Ldap ldap = properties.getLdap();
        LdapContextSource source = new LdapContextSource();
        source.setUrl(ldap.getUrls());
        source.setBase(ldap.getBase());
        if (ldap.getUserDn() != null && !ldap.getUserDn().isBlank()) {
            source.setUserDn(ldap.getUserDn());
            source.setPassword(ldap.getPassword());
        }
        source.afterPropertiesSet();
        return source;
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource ldapContextSource) {
        return new LdapTemplate(ldapContextSource);
    }
}
