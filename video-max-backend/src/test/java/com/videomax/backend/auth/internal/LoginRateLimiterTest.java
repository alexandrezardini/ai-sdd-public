package com.videomax.backend.auth.internal;

import com.videomax.backend.config.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link LoginRateLimiter} backed by a real {@link RateLimitConfig}
 * (in-process Bucket4j). Validates the 5-attempts-per-15-minute per-IP policy
 * that backs PRD AC "After 5 failed login attempts from the same IP ... lockout".
 */
class LoginRateLimiterTest {

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter(new RateLimitConfig());
    }

    @Test
    void allowsFiveAttempts_thenBlocksTheSixth() {
        String ip = "203.0.113.5";

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(rateLimiter.tryConsume(ip))
                .as("attempt %d within the window should be allowed", attempt)
                .isTrue();
        }

        assertThat(rateLimiter.tryConsume(ip))
            .as("6th attempt within the window must be blocked")
            .isFalse();
    }

    @Test
    void tracksBucketsPerIpIndependently() {
        String exhausted = "203.0.113.1";
        String fresh = "203.0.113.2";

        for (int i = 0; i < 5; i++) {
            rateLimiter.tryConsume(exhausted);
        }

        assertThat(rateLimiter.tryConsume(exhausted)).isFalse();
        assertThat(rateLimiter.tryConsume(fresh))
            .as("a different IP has its own bucket and is not affected")
            .isTrue();
    }
}
