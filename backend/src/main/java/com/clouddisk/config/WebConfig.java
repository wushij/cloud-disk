package com.clouddisk.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CloudDiskProperties properties;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final GlobalApiRateLimitInterceptor globalApiRateLimitInterceptor;

    public WebConfig(CloudDiskProperties properties,
                     RateLimitInterceptor rateLimitInterceptor,
                     IpRateLimitInterceptor ipRateLimitInterceptor,
                     GlobalApiRateLimitInterceptor globalApiRateLimitInterceptor) {
        this.properties = properties;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.ipRateLimitInterceptor = ipRateLimitInterceptor;
        this.globalApiRateLimitInterceptor = globalApiRateLimitInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var patterns = properties.getCors().getAllowedOriginPatterns();
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        registry.addMapping("/share/**")
                .allowedOriginPatterns(patterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalApiRateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/api/auth/login", "/api/auth/ldap/login", "/api/auth/register", "/share/**");
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/upload/**", "/api/files/simple");
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                .match("/api/**")
                .notMatch(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/captcha",
                        "/api/auth/captcha/required",
                        "/api/auth/avatar/view",
                        "/api/admin/users/*/avatar",
                        "/api/teams/*/members/*/avatar",
                        "/api/auth/providers",
                        "/api/auth/ldap/login",
                        "/api/auth/sso/**",
                        "/api/files/*/download",
                        "/api/files/download/zip",
                        "/api/files/*/preview",
                        "/api/files/*/thumbnail",
                        "/api/onlyoffice/**",
                        "/share/**",
                        "/actuator/**"
                )
                .check(r -> StpUtil.checkLogin())
        )).addPathPatterns("/**");
    }
}
