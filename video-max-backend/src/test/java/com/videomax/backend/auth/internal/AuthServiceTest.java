package com.videomax.backend.auth.internal;

import com.videomax.backend.auth.exception.EmailAlreadyRegisteredException;
import com.videomax.backend.auth.exception.InvalidCredentialsException;
import com.videomax.backend.auth.exception.InvalidResetTokenException;
import com.videomax.backend.auth.exception.RateLimitExceededException;
import com.videomax.backend.auth.exception.WeakPasswordException;
import com.videomax.backend.auth.internal.entity.PasswordResetToken;
import com.videomax.backend.auth.internal.entity.RefreshToken;
import com.videomax.backend.auth.internal.entity.User;
import com.videomax.backend.auth.internal.repository.UserRepository;
import com.videomax.backend.config.MailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AuthService} orchestration. All collaborators are mocked
 * (Mockito), so this exercises the business rules only: password complexity,
 * enumeration guards, rate-limit gate, refresh rotation and reset flow.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private EmailService emailService;
    @Mock private LoginRateLimiter rateLimiter;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailProperties mailProperties;

    private AuthService authService;

    private static final String EMAIL = "alex@example.com";
    private static final String PASSWORD = "StrongPass1";
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtService, refreshTokenService,
            passwordResetService, emailService, rateLimiter, passwordEncoder, mailProperties);
        userId = UUID.randomUUID();
        user = new User(userId, EMAIL, "stored-hash", "Alex", Instant.now(), Instant.now());
    }

    // ---------- register ----------

    @Test
    void register_newEmail_persistsUserWithHashAndIssuesTokens() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(userId, u.email(), u.passwordHash(), u.name(), u.createdAt(), u.updatedAt());
        });
        when(jwtService.generateAccessToken(userId)).thenReturn("access-jwt");
        when(jwtService.getAccessTtl()).thenReturn(900);
        when(refreshTokenService.generateToken()).thenReturn("raw-refresh");
        when(refreshTokenService.hashToken("raw-refresh")).thenReturn("hash-refresh");

        AuthService.AuthResult result = authService.register("Alex", EMAIL, PASSWORD);

        assertThat(result.response().accessToken()).isEqualTo("access-jwt");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.response().user().email()).isEqualTo(EMAIL);
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().passwordHash()).isEqualTo("bcrypt-hash");
        verify(refreshTokenService).persistToken(eq(userId), eq("hash-refresh"), anyInt());
    }

    @Test
    void register_duplicateEmail_throwsAndPersistsNothing() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register("Alex", EMAIL, PASSWORD))
            .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_weakPassword_throwsBeforeAnyPersistence() {
        assertThatThrownBy(() -> authService.register("Alex", EMAIL, "weak"))
            .isInstanceOf(WeakPasswordException.class);
        verifyNoInteractions(userRepository);
    }

    // ---------- login ----------

    @Test
    void login_correctCredentials_returnsTokensAndPersistsRefresh() {
        when(rateLimiter.tryConsume("1.2.3.4")).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(userId)).thenReturn("access-jwt");
        when(jwtService.getAccessTtl()).thenReturn(900);
        when(jwtService.getRefreshTtl()).thenReturn(604800);
        when(refreshTokenService.generateToken()).thenReturn("raw-refresh");
        when(refreshTokenService.hashToken("raw-refresh")).thenReturn("hash-refresh");

        AuthService.AuthResult result = authService.login(EMAIL, PASSWORD, "1.2.3.4");

        assertThat(result.response().accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        verify(refreshTokenService).persistToken(eq(userId), eq("hash-refresh"), eq(604800));
    }

    @Test
    void login_wrongPassword_throwsGenericInvalidCredentials() {
        when(rateLimiter.tryConsume(any())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, "wrong", "1.2.3.4"))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password.");
    }

    @Test
    void login_unknownEmail_throwsSameGenericMessageAsWrongPassword() {
        when(rateLimiter.tryConsume(any())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD, "1.2.3.4"))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password.");
    }

    @Test
    void login_rateLimitExceeded_throwsBeforeTouchingTheDatabase() {
        when(rateLimiter.tryConsume("1.2.3.4")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD, "1.2.3.4"))
            .isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(userRepository);
    }

    // ---------- refresh ----------

    @Test
    void refresh_validToken_rotatesRefreshAndReturnsNewAccess() {
        RefreshToken current = new RefreshToken(
            UUID.randomUUID(), userId, "old-hash",
            Instant.now().plusSeconds(1000), null, Instant.now());
        when(refreshTokenService.findByTokenHash("old-hash")).thenReturn(current);
        when(refreshTokenService.isTokenValid(current)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(userId)).thenReturn("new-access");
        when(jwtService.getAccessTtl()).thenReturn(900);
        when(jwtService.getRefreshTtl()).thenReturn(604800);
        when(refreshTokenService.generateToken()).thenReturn("new-raw");
        when(refreshTokenService.rotateToken("old-hash", userId, 604800)).thenReturn(
            new RefreshToken(UUID.randomUUID(), userId, "new-hash",
                Instant.now().plusSeconds(1000), null, Instant.now()));

        AuthService.AuthResult result = authService.refresh("old-hash", "");

        assertThat(result.response().accessToken()).isEqualTo("new-access");
        verify(refreshTokenService).rotateToken("old-hash", userId, 604800);
    }

    @Test
    void refresh_revokedOrExpiredToken_throwsInvalidCredentials() {
        RefreshToken revoked = new RefreshToken(
            UUID.randomUUID(), userId, "old-hash",
            Instant.now().plusSeconds(1000), Instant.now(), Instant.now());
        when(refreshTokenService.findByTokenHash("old-hash")).thenReturn(revoked);
        when(refreshTokenService.isTokenValid(revoked)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("old-hash", ""))
            .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).rotateToken(any(), any(), anyInt());
    }

    // ---------- forgot password ----------

    @Test
    void forgotPassword_existingUser_createsTokenAndSendsEmailWithLink() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        PasswordResetToken prt = new PasswordResetToken(
            UUID.randomUUID(), userId, "hash",
            Instant.now().plusSeconds(1800), null, Instant.now());
        when(passwordResetService.createResetToken(userId))
            .thenReturn(new PasswordResetService.ResetTokenResult(prt, "raw-reset"));
        when(mailProperties.resetLinkBase()).thenReturn("http://localhost:3000/reset-password");

        authService.forgotPassword(EMAIL);

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), link.capture());
        assertThat(link.getValue()).contains("token=raw-reset");
    }

    @Test
    void forgotPassword_unknownEmail_doesNothing_toPreventEnumeration() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@example.com");

        verifyNoInteractions(passwordResetService, emailService);
    }

    // ---------- reset password ----------

    @Test
    void resetPassword_validToken_updatesPasswordAndRevokesAllRefreshTokens() {
        PasswordResetToken token = new PasswordResetToken(
            UUID.randomUUID(), userId, "reset-hash",
            Instant.now().plusSeconds(600), null, Instant.now());
        when(passwordResetService.hashToken("raw-reset")).thenReturn("reset-hash");
        when(passwordResetService.findByTokenHash("reset-hash")).thenReturn(token);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewStrong2")).thenReturn("new-bcrypt-hash");

        authService.resetPassword("raw-reset", "NewStrong2");

        verify(passwordResetService).validateAndConsume("reset-hash");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().passwordHash()).isEqualTo("new-bcrypt-hash");
        verify(refreshTokenService).revokeAll(userId);
    }

    @Test
    void resetPassword_expiredToken_throwsInvalidResetToken() {
        PasswordResetToken expired = new PasswordResetToken(
            UUID.randomUUID(), userId, "reset-hash",
            Instant.now().minusSeconds(10), null, Instant.now());
        when(passwordResetService.hashToken("raw-reset")).thenReturn("reset-hash");
        when(passwordResetService.findByTokenHash("reset-hash")).thenReturn(expired);

        assertThatThrownBy(() -> authService.resetPassword("raw-reset", "NewStrong2"))
            .isInstanceOf(InvalidResetTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_reusedToken_throwsInvalidResetToken() {
        PasswordResetToken consumed = new PasswordResetToken(
            UUID.randomUUID(), userId, "reset-hash",
            Instant.now().plusSeconds(600), Instant.now(), Instant.now());
        when(passwordResetService.hashToken("raw-reset")).thenReturn("reset-hash");
        when(passwordResetService.findByTokenHash("reset-hash")).thenReturn(consumed);

        assertThatThrownBy(() -> authService.resetPassword("raw-reset", "NewStrong2"))
            .isInstanceOf(InvalidResetTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_weakNewPassword_throwsWeakPassword() {
        assertThatThrownBy(() -> authService.resetPassword("raw-reset", "weak"))
            .isInstanceOf(WeakPasswordException.class);
        verifyNoInteractions(passwordResetService, userRepository, refreshTokenService);
    }
}
