package com.videomax.backend.auth.internal;

import com.videomax.backend.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link JwtService} — HS256 signing, parsing and validation.
 * Uses a real key (no Spring context); the secret must be >= 32 bytes for HS256.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long-000";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, "video-max", 900, 604800));
    }

    @Test
    void generateAccessToken_thenGetUserId_returnsSameSubject() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId);

        assertThat(jwtService.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void generateAccessToken_producesValidToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID());

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void generateRefreshToken_producesValidToken() {
        String token = jwtService.generateRefreshToken(UUID.randomUUID());

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_forMalformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void isTokenValid_forTokenSignedWithDifferentSecret_returnsFalse() {
        JwtService foreignIssuer = new JwtService(
            new JwtProperties("another-secret-key-that-is-32-bytes-plus-01", "video-max", 900, 604800));
        String foreignToken = foreignIssuer.generateAccessToken(UUID.randomUUID());

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void ttlAccessors_reflectConfiguredValues() {
        assertThat(jwtService.getAccessTtl()).isEqualTo(900);
        assertThat(jwtService.getRefreshTtl()).isEqualTo(604800);
    }
}
