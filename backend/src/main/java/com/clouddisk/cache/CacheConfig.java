package com.clouddisk.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CacheConfig {

    /**
     * 无 Redis 时使用纯内存缓存
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "clouddisk.redis.enabled", havingValue = "false", matchIfMissing = true)
    public CacheService inMemoryCacheService() {
        return new InMemoryCacheService();
    }
}
