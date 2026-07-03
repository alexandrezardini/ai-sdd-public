package com.videomax.backend.auth.internal.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("password_reset_tokens")
public record PasswordResetToken(
    @Id
    UUID id,
    UUID userId,
    String tokenHash,
    Instant expiresAt,
    Instant consumedAt,
    Instant createdAt
) {
    public PasswordResetToken consume() {
        return new PasswordResetToken(id, userId, tokenHash, expiresAt, Instant.now(), createdAt);
    }

    public boolean isValid() {
        return consumedAt == null && Instant.now().isBefore(expiresAt);
    }
}
