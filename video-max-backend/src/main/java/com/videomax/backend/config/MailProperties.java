package com.videomax.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
    String fromAddress,
    String resetLinkBase
) {}
