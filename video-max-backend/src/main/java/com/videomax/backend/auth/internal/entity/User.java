package com.videomax.backend.auth.internal.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("users")
public record User(
    @Id
    UUID id,
    String email,
    String passwordHash,
    String name,
    Instant createdAt,
    Instant updatedAt
) {
    public User withPasswordHash(String newHash) {
        return new User(id, email, newHash, name, createdAt, updatedAt);
    }
}
