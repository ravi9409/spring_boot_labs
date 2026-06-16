package com.example.cache.config;

/**
 * Central registry of all cache names used in the application.
 * Prevents typos and gives a single place to manage cache names.
 */
public final class CacheNames {

    private CacheNames() {} // utility class

    // ── L1 Caffeine (in-memory) caches ──
    public static final String PRODUCT_BY_ID     = "products";
    public static final String PRODUCT_BY_SKU    = "productsBySku";
    public static final String PRODUCTS_BY_CAT   = "productsByCategory";
    public static final String CATEGORIES        = "categories";
    public static final String USERS             = "users";

    // ── L2 Redis (distributed) caches ──
    public static final String PRODUCT_REDIS     = "products-redis";
    public static final String CATALOG_REDIS     = "catalog-redis";

    // ── Composite (L1 + L2) caches ──
    public static final String PRODUCT_MULTILEVEL = "products-multilevel";
}
