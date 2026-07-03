package com.videomax.backend.auth.dto;

public record AuthResponse(
    String accessToken,
    Integer expiresIn,
    UserResponse user
) {}
