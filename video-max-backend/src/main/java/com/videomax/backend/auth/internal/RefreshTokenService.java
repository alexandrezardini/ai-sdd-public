package com.videomax.backend.auth.internal;

import com.videomax.backend.auth.internal.entity.RefreshToken;
import com.videomax.backend.auth.internal.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    public String generateToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String token) {
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = md.digest(token.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    public RefreshToken persistToken(UUID userId, String tokenHash, int ttlSeconds) {
        Instant expiresAt = Instant.now().plus(ttlSeconds, ChronoUnit.SECONDS);
        RefreshToken token = new RefreshToken(
            null,
            userId,
            tokenHash,
            expiresAt,
            null,
            Instant.now()
        );
        return repository.save(token);
    }

    public RefreshToken rotateToken(String oldTokenHash, UUID userId, int ttlSeconds) {
        repository.findByTokenHash(oldTokenHash)
            .ifPresent(token -> repository.save(token.revoke()));

        String newToken = generateToken();
        return persistToken(userId, hashToken(newToken), ttlSeconds);
    }

    public RefreshToken findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new RuntimeException("Token not found"));
    }

    public void revokeAll(UUID userId) {
        repository.deleteAllByUserId(userId);
    }

    public boolean isTokenValid(RefreshToken token) {
        return token.isValid();
    }
}
