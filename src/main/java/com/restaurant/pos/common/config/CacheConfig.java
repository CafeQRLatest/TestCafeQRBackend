package com.restaurant.pos.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {
    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private String redisPort;

    @PostConstruct
    public void logRedisConfiguration() {
        boolean productionProfile = activeProfile != null && activeProfile.toLowerCase().contains("prod");
        boolean localRedis = redisHost == null || redisHost.isBlank() || "localhost".equalsIgnoreCase(redisHost) || "127.0.0.1".equals(redisHost);
        if (productionProfile && localRedis) {
            log.warn("Redis cache is enabled but SPRING_DATA_REDIS_HOST appears unset for profile '{}'. Stable-data caches may miss in production.", activeProfile);
        } else {
            log.info("Redis cache configured for profile '{}' at {}:{}", activeProfile, redisHost, redisPort);
        }
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(java.util.Objects.requireNonNull(Duration.ofHours(1)))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(java.util.Objects.requireNonNull(connectionFactory))
                .cacheDefaults(config)
                .build();
    }
}
