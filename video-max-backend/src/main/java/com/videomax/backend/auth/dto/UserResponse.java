package com.videomax.backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String name,
    Instant createdAt
) {}
