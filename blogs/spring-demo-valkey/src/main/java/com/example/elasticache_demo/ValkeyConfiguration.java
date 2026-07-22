package com.example.elasticache_demo;

import io.valkey.springframework.data.valkey.cache.ValkeyCacheConfiguration;
import io.valkey.springframework.data.valkey.cache.ValkeyCacheManager;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import java.time.Duration;

@Configuration
public class ValkeyConfiguration {

    @Bean
    public ValkeyCacheManager cacheManager(
        ValkeyConnectionFactory connectionFactory,
        @Value("${spring.cache.valkey.time-to-live:-1}") Duration ttl) {

        ValkeyCacheConfiguration config = ValkeyCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(ttl);

        return ValkeyCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
