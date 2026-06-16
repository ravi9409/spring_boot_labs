package com.example.cache.service;

import com.example.cache.config.CacheNames;
import com.example.cache.dto.ProductDTO;
import com.example.cache.exception.ResourceNotFoundException;
import com.example.cache.model.Product;
import com.example.cache.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service demonstrating ALL Spring Cache annotations:
 *
 *   @Cacheable  — reads from cache; on miss, executes method & caches result
 *   @CachePut   — always executes method & updates cache (write-through)
 *   @CacheEvict — removes entry from cache
 *   @Caching    — combines multiple cache operations in one method
 *   @CacheConfig — class-level defaults (cache name, manager, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = CacheNames.PRODUCT_BY_ID) // default cache for this class
public class ProductService {

    private final ProductRepository productRepository;

    // ══════════════════════════════════════════════════════════════
    //  @Cacheable — READ with caching
    // ══════════════════════════════════════════════════════════════

    /**
     * L1 CACHE (Caffeine — default primary cache manager).
     *
     * First call  → hits DB, stores result in Caffeine
     * Second call → returns from Caffeine, DB is NOT touched
     *
     * key = "#id" → uses the method parameter as cache key
     */
    @Cacheable(key = "#id")
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long id) {
        log.info("💾 DB HIT — fetching product by id={}", id);
        simulateSlowDbQuery();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toDTO(product);
    }

    /**
     * L1 CACHE with custom key.
     *
     * key = "#sku" → cache key is the SKU string, not the default method args
     */
    @Cacheable(cacheNames = CacheNames.PRODUCT_BY_SKU, key = "#sku")
    @Transactional(readOnly = true)
    public ProductDTO getProductBySku(String sku) {
        log.info("💾 DB HIT — fetching product by sku={}", sku);
        simulateSlowDbQuery();
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return toDTO(product);
    }

    /**
     * L2 CACHE (Redis — distributed).
     *
     * Uses the Redis cache manager explicitly.
     * Shared across all application instances (pods).
     */
    @Cacheable(cacheNames = CacheNames.PRODUCT_REDIS,
               cacheManager = "redisCacheManager",
               key = "#id")
    @Transactional(readOnly = true)
    public ProductDTO getProductFromRedis(Long id) {
        log.info("💾 DB HIT (Redis miss) — fetching product id={}", id);
        simulateSlowDbQuery();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toDTO(product);
    }

    /**
     * MULTI-LEVEL CACHE (L1 Caffeine → L2 Redis → DB).
     *
     * Uses our custom MultiLevelCacheManager.
     * Best of both worlds: L1 speed + L2 distribution.
     */
    @Cacheable(cacheNames = CacheNames.PRODUCT_MULTILEVEL,
               cacheManager = "multiLevelCacheManager",
               key = "#id")
    @Transactional(readOnly = true)
    public ProductDTO getProductMultiLevel(Long id) {
        log.info("💾 DB HIT (L1 + L2 miss) — fetching product id={}", id);
        simulateSlowDbQuery();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toDTO(product);
    }

    /**
     * CONDITIONAL CACHING — only cache if condition is met.
     *
     * condition → evaluated BEFORE method execution (skip cache if false)
     * unless   → evaluated AFTER method execution (don't cache if true)
     */
    @Cacheable(
            cacheNames = CacheNames.PRODUCTS_BY_CAT,
            key = "#category",
            condition = "#category != null && #category.length() > 0",
            unless = "#result == null || #result.isEmpty()"
    )
    @Transactional(readOnly = true)
    public List<ProductDTO> getProductsByCategory(String category) {
        log.info("💾 DB HIT — fetching products for category={}", category);
        simulateSlowDbQuery();
        return productRepository.findByCategory(category)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════
    //  @CachePut — WRITE-THROUGH (always executes, updates cache)
    // ══════════════════════════════════════════════════════════════

    /**
     * @CachePut ALWAYS runs the method and updates the cache with the result.
     * Unlike @Cacheable, it never skips the method.
     *
     * Used for CREATE and UPDATE operations to keep cache in sync.
     */
    @CachePut(key = "#result.id")
    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        log.info("📝 Creating product: {}", dto.getName());
        Product product = toEntity(dto);
        Product saved = productRepository.save(product);
        return toDTO(saved);
    }

    // ══════════════════════════════════════════════════════════════
    //  @CacheEvict — Remove from cache
    // ══════════════════════════════════════════════════════════════

    /**
     * @CacheEvict removes the entry AFTER the method executes.
     *
     * beforeInvocation = false (default) → evicts after success
     * beforeInvocation = true            → evicts before (use if method might fail
     *                                       and you still want stale data removed)
     */
    @CacheEvict(key = "#id")
    @Transactional
    public void deleteProduct(Long id) {
        log.info("🗑️ Deleting product id={} and evicting from cache", id);
        productRepository.deleteById(id);
    }

    /**
     * @CacheEvict with allEntries = true → clears the ENTIRE cache.
     * Used for bulk operations or cache refresh.
     */
    @CacheEvict(allEntries = true)
    public void clearProductCache() {
        log.info("🧹 Cleared entire products cache");
    }

    // ══════════════════════════════════════════════════════════════
    //  @Caching — Combine multiple cache operations
    // ══════════════════════════════════════════════════════════════

    /**
     * @Caching groups multiple @CachePut / @CacheEvict / @Cacheable.
     *
     * On update:
     *   1. Update the product-by-id cache
     *   2. Update the product-by-sku cache
     *   3. Evict the category list cache (stale after update)
     *   4. Evict the multi-level cache
     */
    @Caching(
            put = {
                    @CachePut(cacheNames = CacheNames.PRODUCT_BY_ID, key = "#result.id"),
                    @CachePut(cacheNames = CacheNames.PRODUCT_BY_SKU, key = "#result.sku")
            },
            evict = {
                    @CacheEvict(cacheNames = CacheNames.PRODUCTS_BY_CAT, allEntries = true),
                    @CacheEvict(cacheNames = CacheNames.PRODUCT_MULTILEVEL,
                                cacheManager = "multiLevelCacheManager",
                                key = "#id"),
                    @CacheEvict(cacheNames = CacheNames.PRODUCT_REDIS,
                                cacheManager = "redisCacheManager",
                                key = "#id")
            }
    )
    @Transactional
    public ProductDTO updateProduct(Long id, ProductDTO dto) {
        log.info("✏️ Updating product id={}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        product.setName(dto.getName());
        product.setSku(dto.getSku());
        product.setDescription(dto.getDescription());
        product.setCategory(dto.getCategory());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());

        Product updated = productRepository.save(product);
        return toDTO(updated);
    }

    // ══════════════════════════════════════════════════════════════
    //  SHORT-LIVED CACHE (for volatile data like stock counts)
    // ══════════════════════════════════════════════════════════════

    /**
     * Uses the "shortLivedCacheManager" (30-second TTL).
     * Perfect for data that changes frequently but is read even more.
     */
    @Cacheable(cacheNames = "stockCounts",
               cacheManager = "shortLivedCacheManager",
               key = "#id")
    @Transactional(readOnly = true)
    public Integer getStockCount(Long id) {
        log.info("💾 DB HIT — fetching stock count for id={}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return product.getStockQuantity();
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    private void simulateSlowDbQuery() {
        try {
            Thread.sleep(500); // simulate network/DB latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ProductDTO toDTO(Product p) {
        return ProductDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .sku(p.getSku())
                .description(p.getDescription())
                .category(p.getCategory())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .build();
    }

    private Product toEntity(ProductDTO dto) {
        return Product.builder()
                .name(dto.getName())
                .sku(dto.getSku())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .price(dto.getPrice())
                .stockQuantity(dto.getStockQuantity())
                .build();
    }
}
