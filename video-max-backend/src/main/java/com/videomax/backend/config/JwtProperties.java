package com.videomax.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    Integer accessTtl,
    Integer refreshTtl
) {}
