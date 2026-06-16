package com.example.cache.service;

import com.example.cache.config.CacheNames;
import com.example.cache.model.Product;
import com.example.cache.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  CACHE WARMING & SCHEDULED EVICTION                         ║
 * ║                                                              ║
 * ║  Industry practice:                                          ║
 * ║  • Pre-load frequently accessed data on startup              ║
 * ║  • Periodically refresh stale caches                         ║
 * ║  • Scheduled eviction for time-sensitive data                ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final ProductRepository productRepository;
    private final CacheManager caffeineCacheManager;

    /**
     * CACHE WARMING — runs once when the application finishes starting.
     *
     * Pre-loads the top products into L1 cache so the very first
     * user request is fast, not a cold cache miss.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        log.info("🔥 Cache warm-up started...");

        List<Product> allProducts = productRepository.findAll();

        var cache = caffeineCacheManager.getCache(CacheNames.PRODUCT_BY_ID);
        if (cache != null) {
            allProducts.forEach(product -> cache.put(product.getId(),
                    com.example.cache.dto.ProductDTO.builder()
                            .id(product.getId())
                            .name(product.getName())
                            .sku(product.getSku())
                            .description(product.getDescription())
                            .category(product.getCategory())
                            .price(product.getPrice())
                            .stockQuantity(product.getStockQuantity())
                            .build()
            ));
        }

        log.info("🔥 Cache warm-up complete — {} products pre-loaded", allProducts.size());
    }

    /**
     * SCHEDULED CACHE REFRESH — runs every 30 minutes.
     *
     * Clears category cache and lets it be re-populated on next access.
     * In production, this might be triggered by a message queue event
     * (Kafka/RabbitMQ) instead of a fixed schedule.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)  // every 30 minutes
    public void refreshCategoryCache() {
        log.info("🔄 Scheduled category cache refresh");
        var cache = caffeineCacheManager.getCache(CacheNames.PRODUCTS_BY_CAT);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Manual cache clear for admin operations.
     */
    public void clearAllCaches() {
        log.info("🧹 Clearing ALL caches");
        caffeineCacheManager.getCacheNames()
                .forEach(name -> Objects.requireNonNull(
                        caffeineCacheManager.getCache(name)).clear());
    }
}
