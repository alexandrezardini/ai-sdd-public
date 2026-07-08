package com.videomax.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videomax.backend.auth.dto.AuthResponse;
import com.videomax.backend.auth.dto.UserResponse;
import com.videomax.backend.auth.exception.EmailAlreadyRegisteredException;
import com.videomax.backend.auth.exception.InvalidCredentialsException;
import com.videomax.backend.auth.exception.InvalidResetTokenException;
import com.videomax.backend.auth.exception.RateLimitExceededException;
import com.videomax.backend.auth.internal.AuthService;
import com.videomax.backend.auth.internal.AuthService.AuthResult;
import com.videomax.backend.auth.internal.JwtService;
import com.videomax.backend.auth.internal.RefreshTokenService;
import com.videomax.backend.config.JwtAuthenticationFilter;
import com.videomax.backend.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuthController} using {@code @WebMvcTest}. The real
 * {@link SecurityConfig} and JWT filter are imported so the stateless filter
 * chain and RFC 7807 {@code ProblemDetail} mapping are exercised; the service
 * layer is mocked.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private JwtService jwtService;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private AuthResult sampleAuthResult(String accessToken, String refreshToken) {
        UserResponse user = new UserResponse(UUID.randomUUID(), "alex@example.com", "Alex", Instant.now());
        return new AuthResult(new AuthResponse(accessToken, 900, user), refreshToken);
    }

    @Test
    void register_validRequest_returns201WithBodyAndRefreshCookie() throws Exception {
        when(authService.register("Alex", "alex@example.com", "StrongPass1"))
            .thenReturn(sampleAuthResult("access-jwt", "raw-refresh"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Alex", "email", "alex@example.com", "password", "StrongPass1"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").value("access-jwt"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.email").value("alex@example.com"))
            .andExpect(header().string("Set-Cookie", containsString("refresh_token=raw-refresh")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=604800")));
    }

    @Test
    void register_missingEmail_returns400WithValidationDetail() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Alex", "password", "StrongPass1"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUTH_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[0]").value(containsString("email")));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(), any(), any()))
            .thenThrow(new EmailAlreadyRegisteredException(
                "An account with this email already exists. Try logging in."));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Alex", "email", "alex@example.com", "password", "StrongPass1"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUTH_EMAIL_TAKEN"));
    }

    @Test
    void login_wrongCredentials_returns401WithGenericMessage() throws Exception {
        when(authService.login(any(), any(), any()))
            .thenThrow(new InvalidCredentialsException("Invalid email or password."));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "alex@example.com", "password", "whatever1A"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
            // Generic message — no field-level hint about which of email/password was wrong.
            .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void login_rateLimited_returns429() throws Exception {
        when(authService.login(any(), any(), any()))
            .thenThrow(new RateLimitExceededException("Too many login attempts. Try again in 15 minutes."));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "alex@example.com", "password", "whatever1A"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_returns200WithRotatedCookie() throws Exception {
        when(refreshTokenService.hashToken("cookie-value")).thenReturn("cookie-hash");
        when(authService.refresh("cookie-hash", ""))
            .thenReturn(sampleAuthResult("new-access", "new-raw"));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "cookie-value")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access"))
            .andExpect(header().string("Set-Cookie", containsString("refresh_token=new-raw")));
    }

    @Test
    void logout_returns204AndClearsCookie() throws Exception {
        // /auth/logout is an authenticated endpoint — present a valid access token
        // so the JWT filter populates the SecurityContext.
        UUID userId = UUID.randomUUID();
        when(jwtService.getUserId("access-token")).thenReturn(userId);
        when(refreshTokenService.hashToken("cookie-value")).thenReturn("cookie-hash");

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer access-token")
                .cookie(new Cookie("refresh_token", "cookie-value")))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("refresh_token=;")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void forgotPassword_alwaysReturns202() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "alex@example.com"))))
            .andExpect(status().isAccepted());

        verify(authService).forgotPassword("alex@example.com");
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidResetTokenException(
                "This reset link has expired or is invalid. Request a new one."))
            .when(authService).resetPassword(any(), any());

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", "bad-token", "newPassword", "NewStrong2"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_RESET_TOKEN"));
    }

    @Test
    void me_withoutBearerToken_isRejected() throws Exception {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().is4xxClientError());
    }
}
