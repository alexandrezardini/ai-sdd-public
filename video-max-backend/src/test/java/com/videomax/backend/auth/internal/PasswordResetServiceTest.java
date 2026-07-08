package com.videomax.backend.auth.internal;

import com.videomax.backend.auth.exception.InvalidResetTokenException;
import com.videomax.backend.auth.internal.entity.PasswordResetToken;
import com.videomax.backend.auth.internal.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PasswordResetService}: 30-minute token creation,
 * hash lookup, and single-use consumption (expired / reused tokens rejected).
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository repository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private PasswordResetService service;

    @Test
    void createResetToken_persistsHashWithThirtyMinuteExpiry_andReturnsRawToken() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PasswordResetService.ResetTokenResult result = service.createResetToken(userId);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(repository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertThat(result.rawToken()).isNotBlank();
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.consumedAt()).isNull();
        // Raw token is never stored — only its hash.
        assertThat(saved.tokenHash()).isEqualTo(service.hashToken(result.rawToken()));

        Instant lowerBound = Instant.now().plus(29, ChronoUnit.MINUTES);
        Instant upperBound = Instant.now().plus(31, ChronoUnit.MINUTES);
        assertThat(saved.expiresAt()).isBetween(lowerBound, upperBound);
    }

    @Test
    void findByTokenHash_whenMissing_throwsInvalidResetToken() {
        when(repository.findByTokenHash("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByTokenHash("missing"))
            .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void validateAndConsume_validToken_marksItConsumed() {
        PasswordResetToken token = new PasswordResetToken(
            UUID.randomUUID(), UUID.randomUUID(), "hash",
            Instant.now().plus(10, ChronoUnit.MINUTES), null, Instant.now());
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        service.validateAndConsume("hash");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().consumedAt()).isNotNull();
    }

    @Test
    void validateAndConsume_expiredToken_throwsAndDoesNotConsume() {
        PasswordResetToken expired = new PasswordResetToken(
            UUID.randomUUID(), UUID.randomUUID(), "hash",
            Instant.now().minus(1, ChronoUnit.MINUTES), null, Instant.now());
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validateAndConsume("hash"))
            .isInstanceOf(InvalidResetTokenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void validateAndConsume_alreadyConsumedToken_throws() {
        PasswordResetToken consumed = new PasswordResetToken(
            UUID.randomUUID(), UUID.randomUUID(), "hash",
            Instant.now().plus(10, ChronoUnit.MINUTES), Instant.now(), Instant.now());
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> service.validateAndConsume("hash"))
            .isInstanceOf(InvalidResetTokenException.class);
        verify(repository, never()).save(any());
    }
}
