package com.videomax.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableCaching
public class RateLimitConfig {

    private static final int RATE_LIMIT_TOKENS = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final Map<String, LocalBucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(RATE_LIMIT_WINDOW)
            .maximumSize(10000));
        return cacheManager;
    }

    @Bean
    public Map<String, LocalBucket> loginRateLimitBuckets() {
        return buckets;
    }

    public LocalBucket resolveBucket(String ip) {
        return buckets.computeIfAbsent(ip, key -> {
            Bandwidth limit = Bandwidth.classic(RATE_LIMIT_TOKENS, Refill.intervally(RATE_LIMIT_TOKENS, RATE_LIMIT_WINDOW));
            return Bucket4j.builder()
                .addLimit(limit)
                .build();
        });
    }

    public boolean tryConsume(String ip) {
        return resolveBucket(ip).tryConsume(1);
    }
}
