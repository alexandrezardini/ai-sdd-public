package com.videomax.backend.auth.internal;

import com.videomax.backend.auth.exception.InvalidResetTokenException;
import com.videomax.backend.auth.internal.entity.PasswordResetToken;
import com.videomax.backend.auth.internal.repository.PasswordResetTokenRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository repository;
    private final RefreshTokenService refreshTokenService;

    public PasswordResetService(
            PasswordResetTokenRepository repository,
            RefreshTokenService refreshTokenService) {
        this.repository = repository;
        this.refreshTokenService = refreshTokenService;
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

    public PasswordResetToken createResetToken(UUID userId) {
        String token = generateToken();
        String tokenHash = hashToken(token);
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

        PasswordResetToken resetToken = new PasswordResetToken(
            UUID.randomUUID(),
            userId,
            tokenHash,
            expiresAt,
            null,
            Instant.now()
        );
        return repository.save(resetToken);
    }

    public PasswordResetToken findByTokenHash(String tokenHash) throws InvalidResetTokenException {
        return repository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidResetTokenException(
                "This reset link has expired or is invalid. Request a new one."
            ));
    }

    public void validateAndConsume(String tokenHash) throws InvalidResetTokenException {
        PasswordResetToken token = findByTokenHash(tokenHash);

        if (!token.isValid()) {
            throw new InvalidResetTokenException(
                "This reset link has expired or is invalid. Request a new one."
            );
        }

        repository.save(token.consume());
    }

    public void consumeToken(PasswordResetToken token) {
        repository.save(token.consume());
    }
}
