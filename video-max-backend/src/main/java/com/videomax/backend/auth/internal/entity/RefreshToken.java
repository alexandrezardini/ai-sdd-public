package com.videomax.backend.auth.internal.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("refresh_tokens")
public record RefreshToken(
    @Id
    UUID id,
    UUID userId,
    String tokenHash,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt
) {
    public RefreshToken revoke() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, Instant.now(), createdAt);
    }

    public boolean isValid() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }
}
