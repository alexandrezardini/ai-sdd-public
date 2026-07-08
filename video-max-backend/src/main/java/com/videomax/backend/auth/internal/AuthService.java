package com.videomax.backend.auth.internal;

import com.videomax.backend.auth.dto.AuthResponse;
import com.videomax.backend.auth.dto.UserResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Z])(?=.*\\d).{8,72}$");

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final LoginRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final MailProperties mailProperties;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PasswordResetService passwordResetService,
            EmailService emailService,
            LoginRateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            MailProperties mailProperties) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.mailProperties = mailProperties;
    }

    public AuthResult register(String name, String email, String password) throws WeakPasswordException, EmailAlreadyRegisteredException {
        validatePassword(password);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException(
                "An account with this email already exists. Try logging in."
            );
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(
            null,
            email,
            passwordHash,
            name,
            Instant.now(),
            Instant.now()
        );
        user = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.id());
        String refreshToken = refreshTokenService.generateToken();
        String refreshTokenHash = refreshTokenService.hashToken(refreshToken);
        refreshTokenService.persistToken(user.id(), refreshTokenHash, jwtService.getAccessTtl());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResult login(String email, String password, String clientIp) throws InvalidCredentialsException, RateLimitExceededException {
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new RateLimitExceededException(
                "Too many login attempts. Try again in 15 minutes."
            );
        }

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new InvalidCredentialsException(
                "Invalid email or password."
            ));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException(
                "Invalid email or password."
            );
        }

        String accessToken = jwtService.generateAccessToken(user.id());
        String refreshToken = refreshTokenService.generateToken();
        String refreshTokenHash = refreshTokenService.hashToken(refreshToken);
        refreshTokenService.persistToken(user.id(), refreshTokenHash, jwtService.getRefreshTtl());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResult refresh(String refreshTokenHash, String clientIp) throws InvalidCredentialsException {
        RefreshToken token = refreshTokenService.findByTokenHash(refreshTokenHash);

        if (!refreshTokenService.isTokenValid(token)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }

        UUID userId = token.userId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new InvalidCredentialsException("User not found."));

        String accessToken = jwtService.generateAccessToken(user.id());
        String newRefreshToken = refreshTokenService.generateToken();
        RefreshToken rotated = refreshTokenService.rotateToken(
            refreshTokenHash,
            user.id(),
            jwtService.getRefreshTtl()
        );

        return buildAuthResponse(user, accessToken, newRefreshToken);
    }

    public void logout(String refreshTokenHash) {
        refreshTokenService.revokeToken(refreshTokenHash);
    }

    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email)
            .ifPresent(user -> {
                PasswordResetService.ResetTokenResult resetToken = passwordResetService.createResetToken(user.id());
                String resetLink = UriComponentsBuilder.fromUriString(mailProperties.resetLinkBase())
                    .queryParam("token", resetToken.rawToken())
                    .build()
                    .toUriString();
                emailService.sendPasswordResetEmail(email, resetLink);
            });
        // Always return success regardless of whether email exists (prevent enumeration)
    }

    public void resetPassword(String rawToken, String newPassword) throws InvalidResetTokenException, WeakPasswordException {
        validatePassword(newPassword);

        String tokenHash = passwordResetService.hashToken(rawToken);
        PasswordResetToken token = passwordResetService.findByTokenHash(tokenHash);

        if (!token.isValid()) {
            throw new InvalidResetTokenException(
                "This reset link has expired or is invalid. Request a new one."
            );
        }

        passwordResetService.validateAndConsume(tokenHash);

        User user = userRepository.findById(token.userId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        String newPasswordHash = passwordEncoder.encode(newPassword);
        userRepository.save(user.withPasswordHash(newPasswordHash));

        // Revoke all refresh tokens after password reset
        refreshTokenService.revokeAll(user.id());
    }

    public User getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void validatePassword(String password) throws WeakPasswordException {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new WeakPasswordException(
                "Password must be at least 8 characters, with 1 uppercase letter and 1 number."
            );
        }
    }

    private AuthResult buildAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponse response = new AuthResponse(
            accessToken,
            jwtService.getAccessTtl(),
            new UserResponse(
                user.id(),
                user.email(),
                user.name(),
                user.createdAt()
            )
        );
        return new AuthResult(response, refreshToken);
    }

    public record AuthResult(AuthResponse response, String refreshToken) {}
}
