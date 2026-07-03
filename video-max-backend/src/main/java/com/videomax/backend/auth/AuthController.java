package com.videomax.backend.auth;

import com.videomax.backend.auth.dto.AuthResponse;
import com.videomax.backend.auth.dto.ForgotPasswordRequest;
import com.videomax.backend.auth.dto.LoginRequest;
import com.videomax.backend.auth.dto.RefreshRequest;
import com.videomax.backend.auth.dto.RegisterRequest;
import com.videomax.backend.auth.dto.ResetPasswordRequest;
import com.videomax.backend.auth.dto.UserResponse;
import com.videomax.backend.auth.internal.AuthService;
import com.videomax.backend.auth.internal.AuthService.AuthResult;
import com.videomax.backend.auth.internal.RefreshTokenService;
import com.videomax.backend.auth.internal.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_PATH = "/";
    private static final String LEGACY_REFRESH_TOKEN_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResult result = authService.register(request.name(), request.email(), request.password());
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String clientIp = getClientIp(httpRequest);
        AuthResult result = authService.login(request.email(), request.password(), clientIp);
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String tokenHash = refreshTokenService.hashToken(refreshToken);
        AuthResult result = authService.refresh(tokenHash, "");
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            String tokenHash = refreshTokenService.hashToken(refreshToken);
            authService.logout(tokenHash);
        }
        clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        User user = authService.getCurrentUser(userId);
        UserResponse response = new UserResponse(
            user.id(),
            user.email(),
            user.name(),
            user.createdAt()
        );
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader("Set-Cookie",
            String.format("%s=%s; HttpOnly; Secure; SameSite=Lax; Path=%s; Max-Age=604800",
                REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_PATH));
        // Expire any cookie a browser stored under the old, narrower path so it can't
        // shadow the new one (browsers send the more specific path's cookie first).
        response.addHeader("Set-Cookie",
            String.format("%s=%s; Path=%s; Max-Age=0",
                REFRESH_TOKEN_COOKIE, "", LEGACY_REFRESH_TOKEN_PATH));
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
            String.format("%s=%s; Path=%s; Max-Age=0",
                REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH));
        response.addHeader("Set-Cookie",
            String.format("%s=%s; Path=%s; Max-Age=0",
                REFRESH_TOKEN_COOKIE, "", LEGACY_REFRESH_TOKEN_PATH));
    }
}
