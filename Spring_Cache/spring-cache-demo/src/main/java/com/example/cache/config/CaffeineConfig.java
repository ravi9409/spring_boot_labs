package com.example.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  L1 CACHE — Caffeine (In-Memory, Single JVM)               ║
 * ║                                                              ║
 * ║  • Fastest cache layer (~nanoseconds)                        ║
 * ║  • Lives inside JVM heap                                     ║
 * ║  • Lost on application restart                               ║
 * ║  • NOT shared across instances                               ║
 * ║  • Best for: hot data, read-heavy, low-latency lookups       ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Configuration
@Slf4j
public class CaffeineConfig {

    /**
     * Primary cache manager — Spring uses this by default
     * when no specific cache manager is specified.
     */
    @Bean("caffeineCacheManager")
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Default Caffeine spec for any cache not explicitly configured
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(10))
                .expireAfterAccess(Duration.ofMinutes(5))
                .recordStats()  // IMPORTANT: enables hit/miss metrics for Actuator
        );

        // Register known cache names so they're pre-created
        cacheManager.setCacheNames(java.util.List.of(
                CacheNames.PRODUCT_BY_ID,
                CacheNames.PRODUCT_BY_SKU,
                CacheNames.PRODUCTS_BY_CAT,
                CacheNames.CATEGORIES,
                CacheNames.USERS
        ));

        // Allow dynamic creation of caches not in the above list
        cacheManager.setAllowNullValues(false);

        log.info("✅ L1 Caffeine CacheManager initialized with caches: {}",
                cacheManager.getCacheNames());

        return cacheManager;
    }

    /**
     * Separate Caffeine cache manager with SHORT TTL
     * for rapidly changing data (e.g., stock prices, inventory counts).
     */
    @Bean("shortLivedCacheManager")
    public CacheManager shortLivedCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofSeconds(30))
                .recordStats()
        );
        return cacheManager;
    }
}
