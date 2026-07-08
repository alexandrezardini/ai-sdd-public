package com.videomax.backend.auth.internal;

import com.videomax.backend.auth.internal.entity.RefreshToken;
import com.videomax.backend.auth.internal.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RefreshTokenService}: opaque token generation, SHA-256
 * hashing, persistence, rotation and bulk revocation.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @InjectMocks
    private RefreshTokenService service;

    @Test
    void generateToken_producesDistinctNonBlankTokens() {
        String first = service.generateToken();
        String second = service.generateToken();

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashToken_isDeterministicAndNeverEqualsRawToken() {
        String raw = "some-opaque-refresh-token";

        assertThat(service.hashToken(raw)).isEqualTo(service.hashToken(raw));
        assertThat(service.hashToken(raw)).isNotEqualTo(raw);
    }

    @Test
    void persistToken_savesWithFutureExpiryAndNotRevoked() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persistToken(userId, "token-hash", 604800);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.tokenHash()).isEqualTo("token-hash");
        assertThat(saved.revokedAt()).isNull();
        assertThat(saved.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rotateToken_revokesOldTokenAndPersistsANewOne() {
        UUID userId = UUID.randomUUID();
        RefreshToken existing = new RefreshToken(
            UUID.randomUUID(), userId, "old-hash",
            Instant.now().plusSeconds(1000), null, Instant.now());
        when(repository.findByTokenHash("old-hash")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rotateToken("old-hash", userId, 604800);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(2)).save(captor.capture());
        List<RefreshToken> saved = captor.getAllValues();

        RefreshToken revokedOld = saved.stream()
            .filter(t -> "old-hash".equals(t.tokenHash()))
            .findFirst().orElseThrow();
        assertThat(revokedOld.revokedAt()).as("old token is revoked").isNotNull();

        RefreshToken freshToken = saved.stream()
            .filter(t -> !"old-hash".equals(t.tokenHash()))
            .findFirst().orElseThrow();
        assertThat(freshToken.revokedAt()).as("new token is active").isNull();
        assertThat(freshToken.userId()).isEqualTo(userId);
    }

    @Test
    void findByTokenHash_whenMissing_throws() {
        when(repository.findByTokenHash("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByTokenHash("missing"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void revokeAll_deletesEveryTokenForTheUser() {
        UUID userId = UUID.randomUUID();

        service.revokeAll(userId);

        verify(repository).deleteAllByUserId(userId);
    }

    @Test
    void isTokenValid_reflectsEntityState() {
        RefreshToken active = new RefreshToken(
            UUID.randomUUID(), UUID.randomUUID(), "h",
            Instant.now().plusSeconds(1000), null, Instant.now());
        RefreshToken revoked = new RefreshToken(
            UUID.randomUUID(), UUID.randomUUID(), "h",
            Instant.now().plusSeconds(1000), Instant.now(), Instant.now());
        RefreshToken expired = new RefreshToken(
            UUID.randomUUID(), UUID.randomUUID(), "h",
            Instant.now().minusSeconds(1), null, Instant.now());

        assertThat(service.isTokenValid(active)).isTrue();
        assertThat(service.isTokenValid(revoked)).isFalse();
        assertThat(service.isTokenValid(expired)).isFalse();
    }
}
