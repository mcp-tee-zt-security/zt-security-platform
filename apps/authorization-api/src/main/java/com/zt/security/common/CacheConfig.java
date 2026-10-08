package com.zt.security.common;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import java.time.Duration;

@Configuration @EnableCaching
public class CacheConfig {
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory factory, RedisCacheConfiguration defaults){
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withCacheConfiguration("risk-score", defaults.entryTtl(Duration.ofSeconds(20)))
                .withCacheConfiguration("attack-path", defaults.entryTtl(Duration.ofSeconds(15)))
                .withCacheConfiguration("policy-simulation", defaults.entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("security-graph", defaults.entryTtl(Duration.ofSeconds(10)))
                .build();
    }
}
