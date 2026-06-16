package com.example.cache.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  HTTP CACHE (Browser / CDN / Reverse Proxy Layer)           ║
 * ║                                                              ║
 * ║  • Prevents requests from even reaching the server           ║
 * ║  • Cache-Control, ETag, Last-Modified headers                ║
 * ║  • Used by browsers, CDNs (CloudFront, Akamai), and         ║
 * ║    reverse proxies (Nginx, Varnish)                          ║
 * ║  • Best for: static resources, read-only API responses       ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Cache-Control header values:
 *
 *   max-age=300          → browser caches for 5 minutes
 *   no-cache             → browser revalidates every time (still caches)
 *   no-store             → never cache (sensitive data like banking)
 *   public               → CDNs can cache this (shared)
 *   private              → only the user's browser can cache (personal data)
 *   must-revalidate      → once expired, MUST check server before using
 *   s-maxage=600         → CDN-specific max age (overrides max-age for CDN)
 */
@Configuration
public class HttpCacheConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ── Products API: cache 5 minutes, shared (CDN-cacheable) ──
        WebContentInterceptor productInterceptor = new WebContentInterceptor();
        productInterceptor.addCacheMapping(
                CacheControl.maxAge(5, TimeUnit.MINUTES)
                        .cachePublic()             // CDNs can cache
                        .mustRevalidate(),          // recheck after expiry
                "/api/v1/products/**"
        );
        registry.addInterceptor(productInterceptor);

        // ── Categories: cache 1 hour (rarely changes) ──
        WebContentInterceptor categoryInterceptor = new WebContentInterceptor();
        categoryInterceptor.addCacheMapping(
                CacheControl.maxAge(1, TimeUnit.HOURS)
                        .cachePublic(),
                "/api/v1/categories/**"
        );
        registry.addInterceptor(categoryInterceptor);

        // ── User-specific data: private cache, 2 minutes ──
        WebContentInterceptor userInterceptor = new WebContentInterceptor();
        userInterceptor.addCacheMapping(
                CacheControl.maxAge(2, TimeUnit.MINUTES)
                        .cachePrivate(),            // NEVER cache in CDN
                "/api/v1/users/**"
        );
        registry.addInterceptor(userInterceptor);

        // ── Cart / Checkout: NEVER cache ──
        WebContentInterceptor noCacheInterceptor = new WebContentInterceptor();
        noCacheInterceptor.addCacheMapping(
                CacheControl.noStore(),             // sensitive, real-time data
                "/api/v1/cart/**",
                "/api/v1/checkout/**"
        );
        registry.addInterceptor(noCacheInterceptor);
    }
}
