package com.example.cache.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  CACHE MONITORING ASPECT (AOP)                              ║
 * ║                                                              ║
 * ║  Cross-cutting concern: logs execution time for all          ║
 * ║  service methods. In production, this data goes to           ║
 * ║  Datadog/Grafana/New Relic dashboards.                       ║
 * ║                                                              ║
 * ║  Cached call:  ~0ms  (L1 hit)                                ║
 * ║  Cached call:  ~2ms  (L2 Redis hit)                          ║
 * ║  Uncached:     ~500ms (DB query)                             ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Aspect
@Component
@Slf4j
public class CacheMonitorAspect {

    @Around("execution(* com.example.cache.service.*.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        long start = System.nanoTime();

        Object result = joinPoint.proceed();

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // In production, emit this as a metric instead of a log
        if (elapsedMs < 5) {
            log.debug("⚡ CACHE HIT  {} — {}ms", method, elapsedMs);
        } else {
            log.info("🐢 CACHE MISS {} — {}ms (likely DB call)", method, elapsedMs);
        }

        return result;
    }
}
