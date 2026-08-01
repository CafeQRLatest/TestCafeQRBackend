package com.restaurant.pos.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.pos.common.context.ContextProvider;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Bean("categoryKeyGenerator")
    public KeyGenerator categoryKeyGenerator(ContextProvider contextProvider) {
        return (target, method, params) -> new SimpleKey(
                params[0] != null ? params[0] : "DEFAULT",  // scope
                params[1] != null ? params[1] : "NONE",     // branchId
                contextProvider.getCurrentTenant(),
                contextProvider.getCurrentOrg()
        );
    }

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
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Probe Redis connectivity once at startup
        try {
            connectionFactory.getConnection().ping();
        } catch (Exception e) {
            log.warn("Redis is not available ({}). Falling back to NoOp (no-cache) mode. All @Cacheable/@CacheEvict calls will be silently skipped.", e.getMessage());
            return new NoOpCacheManager();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        // Use non-locking writer so cache errors don't propagate to callers
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        return new FaultTolerantRedisCacheManager(writer, config);
    }

    /**
     * Wraps RedisCacheManager so that any Redis error during cache get/put/evict
     * is caught and logged rather than propagated to the business layer.
     */
    static class FaultTolerantRedisCacheManager extends RedisCacheManager {

        public FaultTolerantRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
            super(cacheWriter, defaultCacheConfiguration);
        }

        @Override
        public Cache getCache(String name) {
            Cache cache = super.getCache(name);
            return cache != null ? new FaultTolerantCache(cache) : null;
        }
    }

    /**
     * Delegates to the real Redis cache but swallows all exceptions so
     * Redis outages never crash a business operation.
     */
    static class FaultTolerantCache implements Cache {
        private final Cache delegate;

        FaultTolerantCache(Cache delegate) {
            this.delegate = delegate;
        }

        @Override public String getName() { return delegate.getName(); }
        @Override public Object getNativeCache() { return delegate.getNativeCache(); }

        @Override
        public ValueWrapper get(Object key) {
            try { return delegate.get(key); } catch (Exception e) {
                log.warn("[Cache] GET failed for key={}: {}", key, e.getMessage());
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            try { return delegate.get(key, type); } catch (Exception e) {
                log.warn("[Cache] GET(type) failed for key={}: {}", key, e.getMessage());
                return null;
            }
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            try { return delegate.get(key, valueLoader); } catch (Exception e) {
                log.warn("[Cache] GET(callable) failed for key={}: {}", key, e.getMessage());
                try { return valueLoader.call(); } catch (Exception ex) { throw new RuntimeException(ex); }
            }
        }

        @Override
        public void put(Object key, Object value) {
            try { delegate.put(key, value); } catch (Exception e) {
                log.warn("[Cache] PUT failed for key={}: {}", key, e.getMessage());
            }
        }

        @Override
        public void evict(Object key) {
            try { delegate.evict(key); } catch (Exception e) {
                log.warn("[Cache] EVICT failed for key={}: {}", key, e.getMessage());
            }
        }

        @Override
        public void clear() {
            try { delegate.clear(); } catch (Exception e) {
                log.warn("[Cache] CLEAR failed: {}", e.getMessage());
            }
        }

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FaultTolerantCache.class);
    }
}
