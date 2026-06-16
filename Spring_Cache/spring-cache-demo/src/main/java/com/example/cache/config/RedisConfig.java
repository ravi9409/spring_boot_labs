package com.example.cache.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  L2 CACHE — Redis (Distributed, Shared Across Instances)    ║
 * ║                                                              ║
 * ║  • Millisecond latency (network hop)                         ║
 * ║  • Shared across ALL application instances                   ║
 * ║  • Survives application restarts                             ║
 * ║  • Supports TTL, pub/sub eviction                            ║
 * ║  • Best for: session data, shared lookups, DB query cache    ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${cache.redis.default-ttl:60}")
    private long defaultTtlMinutes;

    @Value("${cache.redis.ttl.products:60}")
    private long productsTtlMinutes;

    @Value("${cache.redis.ttl.users:30}")
    private long usersTtlMinutes;

    @Value("${cache.redis.ttl.categories:120}")
    private long categoriesTtlMinutes;

    /**
     * Custom ObjectMapper for Redis serialization.
     * MUST include type info so Redis can deserialize back to the correct class.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * RedisTemplate for manual Redis operations (beyond Spring Cache).
     * Used for: pub/sub, direct get/set, distributed locks, etc.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        log.info("✅ RedisTemplate configured with JSON serialization");
        return template;
    }

    /**
     * Redis CacheManager with per-cache TTL configuration.
     * In production, each cache should have an appropriate TTL
     * based on how frequently the underlying data changes.
     */
    @Bean("redisCacheManager")
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // Default config for any cache not explicitly configured
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtlMinutes))
                .serializeKeysWith(RedisSerializationContext
                        .SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext
                        .SerializationPair.fromSerializer(jsonSerializer))
                .prefixCacheNameWith("app:cache:")   // namespace prefix in Redis
                .disableCachingNullValues();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();

        perCacheConfig.put(CacheNames.PRODUCT_REDIS,
                defaultConfig.entryTtl(Duration.ofMinutes(productsTtlMinutes)));

        perCacheConfig.put(CacheNames.CATALOG_REDIS,
                defaultConfig.entryTtl(Duration.ofMinutes(categoriesTtlMinutes)));

        perCacheConfig.put(CacheNames.USERS,
                defaultConfig.entryTtl(Duration.ofMinutes(usersTtlMinutes)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .transactionAware()     // cache operations participate in @Transactional
                .enableStatistics()     // hit/miss stats for monitoring
                .build();

        log.info("✅ L2 Redis CacheManager initialized with per-cache TTLs");
        return cacheManager;
    }
}
