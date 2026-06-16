package com.example.cache.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  MULTI-LEVEL CACHE (L1 + L2 Combined)                      ║
 * ║                                                              ║
 * ║  Read path:  L1 (Caffeine) → L2 (Redis) → Database          ║
 * ║  Write path: Database → Evict L1 → Evict L2                 ║
 * ║                                                              ║
 * ║  This is how MNCs handle caching in microservices:           ║
 * ║  • L1 absorbs the highest-frequency reads (ns latency)       ║
 * ║  • L2 catches L1 misses (ms latency, shared across pods)    ║
 * ║  • DB is only hit on double cache miss                       ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Configuration
@Slf4j
public class MultiLevelCacheConfig {

    @Bean("multiLevelCacheManager")
    public CacheManager multiLevelCacheManager(
            CacheManager caffeineCacheManager,
            CacheManager redisCacheManager) {

        return new AbstractCacheManager() {
            @Override
            @NonNull
            protected Collection<? extends Cache> loadCaches() {
                return Collections.emptyList(); // caches created on demand
            }

            @Override
            protected Cache getMissingCache(@NonNull String name) {
                Cache l1 = caffeineCacheManager.getCache(name);
                Cache l2 = redisCacheManager.getCache(name);

                if (l1 == null && l2 == null) return null;

                return new MultiLevelCache(name, l1, l2);
            }
        };
    }

    /**
     * Custom Cache implementation that reads from L1 first, falls back to L2,
     * and writes through to both layers.
     */
    @Slf4j
    static class MultiLevelCache implements Cache {

        private final String name;
        private final Cache l1;  // Caffeine (fast, local)
        private final Cache l2;  // Redis (slower, shared)

        MultiLevelCache(String name, Cache l1, Cache l2) {
            this.name = name;
            this.l1 = l1;
            this.l2 = l2;
        }

        @Override
        @NonNull
        public String getName() {
            return name;
        }

        @Override
        @NonNull
        public Object getNativeCache() {
            return this;
        }

        /**
         * READ PATH:
         * 1. Check L1 (Caffeine) → if hit, return immediately
         * 2. Check L2 (Redis)    → if hit, populate L1, return
         * 3. Both miss            → return null, Spring calls the @Cacheable method
         */
        @Override
        public ValueWrapper get(@NonNull Object key) {
            // Try L1 first
            if (l1 != null) {
                ValueWrapper l1Value = l1.get(key);
                if (l1Value != null) {
                    log.debug("L1 HIT  [{}] key={}", name, key);
                    return l1Value;
                }
                log.debug("L1 MISS [{}] key={}", name, key);
            }

            // Fall back to L2
            if (l2 != null) {
                ValueWrapper l2Value = l2.get(key);
                if (l2Value != null) {
                    log.debug("L2 HIT  [{}] key={}", name, key);
                    // Backfill L1 so next read is faster
                    if (l1 != null) {
                        l1.put(key, l2Value.get());
                        log.debug("L1 BACKFILL [{}] key={}", name, key);
                    }
                    return l2Value;
                }
                log.debug("L2 MISS [{}] key={}", name, key);
            }

            return null; // double miss → DB lookup
        }

        @Override
        public <T> T get(@NonNull Object key, Class<T> type) {
            ValueWrapper wrapper = get(key);
            if (wrapper == null) return null;

            Object value = wrapper.get();
            if (value != null && type != null && !type.isInstance(value)) {
                throw new IllegalStateException(
                        "Cached value [" + value + "] is not of type [" + type.getName() + "]");
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        }

        @Override
        public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
            ValueWrapper wrapper = get(key);
            if (wrapper != null) {
                @SuppressWarnings("unchecked")
                T result = (T) wrapper.get();
                return result;
            }

            // Double miss — call the loader and cache the result
            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new Cache.ValueRetrievalException(key, valueLoader, e);
            }
        }

        /**
         * WRITE PATH: Write to both layers
         */
        @Override
        public void put(@NonNull Object key, Object value) {
            if (l1 != null) l1.put(key, value);
            if (l2 != null) l2.put(key, value);
            log.debug("PUT [{}] key={} → L1 + L2", name, key);
        }

        /**
         * EVICT: Remove from both layers
         */
        @Override
        public void evict(@NonNull Object key) {
            if (l1 != null) l1.evict(key);
            if (l2 != null) l2.evict(key);
            log.debug("EVICT [{}] key={} from L1 + L2", name, key);
        }

        @Override
        public void clear() {
            if (l1 != null) l1.clear();
            if (l2 != null) l2.clear();
            log.info("CLEAR [{}] all entries from L1 + L2", name);
        }
    }
}
