package com.clouddisk.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "clouddisk.redis.enabled", havingValue = "true")
public class RedisCacheConfig {

    /**
     * Redis 启用时使用多级缓存（L1 本地 + L2 Redis）
     */
    @Bean
    @Primary
    public CacheService multiLevelCacheService(StringRedisTemplate template) {
        return new MultiLevelCacheService(template);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
