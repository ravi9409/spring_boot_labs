# Enterprise Caching in Spring Boot — Complete Guide

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser)                         │
│                                                                 │
│  HTTP Cache: Cache-Control / ETag / Last-Modified               │
│  ┌───────────────────────────────────────────────────────┐      │
│  │  304 Not Modified? → Use local cached copy            │      │
│  │  Expired?          → Send request to server           │      │
│  └───────────────────────────────────────────────────────┘      │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CDN / REVERSE PROXY                           │
│              (CloudFront, Akamai, Nginx, Varnish)               │
│                                                                 │
│  Reads Cache-Control: public, s-maxage=600                      │
│  Caches responses for all users at the edge                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION SERVER                            │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  L1 CACHE — Caffeine (In-Memory)                         │   │
│  │  • ~nanoseconds latency                                  │   │
│  │  • Per-JVM (not shared across pods)                      │   │
│  │  • 500-1000 entries, 5-10 min TTL                        │   │
│  └────────────────────────┬─────────────────────────────────┘   │
│                           │ MISS                                 │
│                           ▼                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  L2 CACHE — Redis (Distributed)                          │   │
│  │  • ~1-5ms latency (network hop)                          │   │
│  │  • Shared across ALL pods/instances                      │   │
│  │  • Survives app restarts                                 │   │
│  │  • 30-120 min TTL per cache                              │   │
│  └────────────────────────┬─────────────────────────────────┘   │
│                           │ MISS                                 │
│                           ▼                                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  DATABASE (PostgreSQL / MySQL)                           │   │
│  │  • ~50-500ms latency                                     │   │
│  │  • JPA/Hibernate 2nd-level cache (optional)              │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Cache Levels Implemented

### Level 0: HTTP Cache (Browser + CDN)
- **File:** `HttpCacheConfig.java`
- **File:** `ProductController.java` (ETag + Last-Modified endpoints)
- Prevents requests from reaching the server entirely
- Uses `Cache-Control`, `ETag`, `Last-Modified` headers

### Level 1: Caffeine (In-Memory)
- **File:** `CaffeineConfig.java`
- Fastest possible cache (~nanosecond access)
- Lives in JVM heap, lost on restart
- Best for hot, frequently read data

### Level 2: Redis (Distributed)
- **File:** `RedisConfig.java`
- Shared across all application instances
- Survives application restarts
- Per-cache TTL configuration

### Multi-Level: L1 + L2 Combined
- **File:** `MultiLevelCacheConfig.java`
- Custom `Cache` implementation that checks L1 first, falls back to L2
- L2 hits automatically backfill L1

### Supporting Infrastructure
- **Cache Warming:** `CacheWarmupService.java` — pre-loads data on startup
- **Monitoring:** `CacheMonitorAspect.java` — AOP-based execution time logging
- **Metrics:** Spring Actuator + Micrometer (Prometheus-compatible)

## API Endpoints

```
GET  /api/v1/products/{id}               → L1 cache (Caffeine)
GET  /api/v1/products/sku/{sku}          → L1 cache (different cache name)
GET  /api/v1/products/category/{cat}     → L1 cache (conditional)
GET  /api/v1/products/redis/{id}         → L2 cache (Redis)
GET  /api/v1/products/multilevel/{id}    → L1 + L2 multi-level cache
GET  /api/v1/products/http-cached/{id}   → HTTP ETag caching
GET  /api/v1/products/last-modified/{id} → HTTP Last-Modified caching
GET  /api/v1/products/{id}/stock         → Short-lived cache (30s TTL)

POST /api/v1/products                    → Create + @CachePut
PUT  /api/v1/products/{id}               → Update + @Caching (put + evict)
DEL  /api/v1/products/{id}               → Delete + @CacheEvict

POST /api/v1/products/cache/clear        → Clear product cache
POST /api/v1/products/cache/clear-all    → Clear ALL caches
```

## Monitoring Endpoints

```
GET  /actuator/caches           → List all caches and stats
GET  /actuator/metrics          → All metrics
GET  /actuator/prometheus       → Prometheus scrape endpoint
```

## Running the Application

```bash
# 1. Start Redis (required for L2 cache)
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 2. Run the app
./mvnw spring-boot:run

# 3. Test: first call (DB hit, ~500ms)
curl http://localhost:8080/api/v1/products/1

# 4. Test: second call (cache hit, ~0ms)
curl http://localhost:8080/api/v1/products/1
```

## Running Without Redis

The application will still work using L1 (Caffeine) caching only.
Redis endpoints will throw connection errors.
For a production setup, always have Redis running.

## Spring Cache Annotations Reference

| Annotation    | Purpose                                           |
|---------------|---------------------------------------------------|
| @Cacheable    | Check cache before executing; cache the result    |
| @CachePut     | Always execute and update cache (write-through)   |
| @CacheEvict   | Remove entry/entries from cache                   |
| @Caching      | Combine multiple cache operations                 |
| @CacheConfig  | Class-level cache defaults                        |
| @EnableCaching| Activates Spring's cache infrastructure           |
