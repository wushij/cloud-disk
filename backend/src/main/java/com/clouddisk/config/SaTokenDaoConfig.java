package com.clouddisk.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 未启用 Redis 时使用 Sa-Token 内存持久层（单机开发）。
 * 启用 Redis 时由 sa-token-redis-jackson 提供 SaTokenDaoRedisJackson。
 */
@Configuration
@ConditionalOnProperty(name = "clouddisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class SaTokenDaoConfig {

    @Bean
    @ConditionalOnMissingBean(SaTokenDao.class)
    public SaTokenDao saTokenDao() {
        return new SaTokenDaoDefaultImpl();
    }
}
