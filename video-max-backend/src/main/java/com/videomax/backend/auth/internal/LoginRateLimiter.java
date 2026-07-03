package com.videomax.backend.auth.internal;

import com.videomax.backend.config.RateLimitConfig;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final RateLimitConfig rateLimitConfig;

    public LoginRateLimiter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    public boolean tryConsume(String ip) {
        return rateLimitConfig.tryConsume(ip);
    }
}
