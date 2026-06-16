package com.example.cache.controller;

import com.example.cache.dto.ProductDTO;
import com.example.cache.service.CacheWarmupService;
import com.example.cache.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REST controller demonstrating ALL cache levels:
 *
 *   1. HTTP Cache   → Cache-Control / ETag headers (browser & CDN)
 *   2. L1 Cache     → Caffeine (in-memory, single JVM)
 *   3. L2 Cache     → Redis (distributed, shared across pods)
 *   4. Multi-Level  → L1 + L2 combined
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final CacheWarmupService cacheWarmupService;

    // ══════════════════════════════════════════════════════════════
    //  L1 CACHE — Caffeine (in-memory)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) {
        ProductDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductDTO> getProductBySku(@PathVariable String sku) {
        ProductDTO product = productService.getProductBySku(sku);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductDTO>> getByCategory(@PathVariable String category) {
        List<ProductDTO> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(products);
    }

    // ══════════════════════════════════════════════════════════════
    //  L2 CACHE — Redis (distributed)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/redis/{id}")
    public ResponseEntity<ProductDTO> getProductRedis(@PathVariable Long id) {
        ProductDTO product = productService.getProductFromRedis(id);
        return ResponseEntity.ok(product);
    }

    // ══════════════════════════════════════════════════════════════
    //  MULTI-LEVEL CACHE — L1 (Caffeine) + L2 (Redis)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/multilevel/{id}")
    public ResponseEntity<ProductDTO> getProductMultiLevel(@PathVariable Long id) {
        ProductDTO product = productService.getProductMultiLevel(id);
        return ResponseEntity.ok(product);
    }

    // ══════════════════════════════════════════════════════════════
    //  HTTP CACHE — ETag-based conditional caching
    // ══════════════════════════════════════════════════════════════

    /**
     * ETag-based HTTP caching:
     *
     * 1st request → server sends response + ETag: "abc123"
     * 2nd request → browser sends If-None-Match: "abc123"
     *   → if data unchanged → server returns 304 Not Modified (no body!)
     *   → if data changed   → server returns 200 + new ETag
     *
     * Saves bandwidth because 304 has no response body.
     */
    @GetMapping("/http-cached/{id}")
    public ResponseEntity<ProductDTO> getProductWithETag(
            @PathVariable Long id,
            WebRequest request) {

        ProductDTO product = productService.getProductById(id);

        // Generate ETag from product data
        String eTag = generateETag(product);

        // Check if client's cached version is still valid
        if (request.checkNotModified(eTag)) {
            // Returns 304 Not Modified — no body sent!
            return null;
        }

        return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(CacheControl
                        .maxAge(5, TimeUnit.MINUTES)
                        .cachePublic()
                        .mustRevalidate())
                .body(product);
    }

    /**
     * Last-Modified based HTTP caching:
     *
     * Similar to ETag but uses timestamps instead of hashes.
     * Browser sends If-Modified-Since header on subsequent requests.
     */
    @GetMapping("/last-modified/{id}")
    public ResponseEntity<ProductDTO> getProductWithLastModified(
            @PathVariable Long id,
            WebRequest request) {

        ProductDTO product = productService.getProductById(id);

        // Use a fixed timestamp for demo; in production, use entity's updatedAt
        long lastModifiedMs = LocalDateTime.now()
                .minusMinutes(10)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();

        if (request.checkNotModified(lastModifiedMs)) {
            return null; // 304 Not Modified
        }

        return ResponseEntity.ok()
                .lastModified(lastModifiedMs)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
                .body(product);
    }

    // ══════════════════════════════════════════════════════════════
    //  WRITE OPERATIONS (with cache sync)
    // ══════════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<ProductDTO> create(@RequestBody ProductDTO dto) {
        ProductDTO created = productService.createProduct(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(
            @PathVariable Long id,
            @RequestBody ProductDTO dto) {
        ProductDTO updated = productService.updateProduct(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════
    //  SHORT-LIVED CACHE (volatile data)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/{id}/stock")
    public ResponseEntity<Integer> getStockCount(@PathVariable Long id) {
        Integer stock = productService.getStockCount(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.SECONDS) // very short HTTP cache
                        .cachePrivate())
                .body(stock);
    }

    // ══════════════════════════════════════════════════════════════
    //  ADMIN — Cache management endpoints
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        productService.clearProductCache();
        return ResponseEntity.ok("Product cache cleared");
    }

    @PostMapping("/cache/clear-all")
    public ResponseEntity<String> clearAllCaches() {
        cacheWarmupService.clearAllCaches();
        return ResponseEntity.ok("All caches cleared");
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    private String generateETag(ProductDTO product) {
        int hash = (product.getId() + ":" +
                    product.getName() + ":" +
                    product.getPrice() + ":" +
                    product.getStockQuantity()).hashCode();
        return "\"" + Integer.toHexString(hash) + "\"";
    }
}
