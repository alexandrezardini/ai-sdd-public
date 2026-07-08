package com.videomax.backend.auth;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the authentication feature against a real
 * PostgreSQL (Testcontainers) and a real SMTP server (GreenMail). Each test
 * uses a distinct client IP (via {@code X-Forwarded-For}) so the shared
 * in-process rate-limiter buckets stay isolated per test.
 *
 * <p>Maps directly to PRD Section 9 acceptance criteria for F01.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

    // Singleton container started in a static initializer so it is up before
    // Spring evaluates @DynamicPropertySource. Ryuk tears it down after the JVM.
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    static GreenMail greenMail;

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Point the mailer at the in-JVM GreenMail SMTP server.
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> 3025);
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long-000");
        registry.add("app.mail.fromAddress", () -> "no-reply@videomax.test");
        registry.add("app.mail.resetLinkBase", () -> "http://localhost:3000/reset-password");
    }

    @BeforeAll
    static void startMail() {
        greenMail = new GreenMail(new ServerSetup(3025, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        greenMail.start();
    }

    @AfterAll
    static void stopMail() {
        greenMail.stop();
    }

    @BeforeEach
    void resetMail() {
        greenMail.reset();
    }

    // ---------- helpers ----------

    private HttpHeaders jsonHeaders(String clientIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Forwarded-For", clientIp);
        return headers;
    }

    private ResponseEntity<Map> register(String name, String email, String password, String ip) {
        return rest.postForEntity("/auth/register",
            new HttpEntity<>(Map.of("name", name, "email", email, "password", password), jsonHeaders(ip)),
            Map.class);
    }

    private ResponseEntity<Map> login(String email, String password, String ip) {
        return rest.postForEntity("/auth/login",
            new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders(ip)),
            Map.class);
    }

    // ---------- AC: register + login happy path ----------

    @Test
    void register_thenLogin_succeedsAndReturnsTokens() {
        String email = "alice@example.com";

        ResponseEntity<Map> registration = register("Alice", email, "StrongPass1", "10.0.0.1");

        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registration.getBody()).containsKey("accessToken");
        assertThat(registration.getHeaders().get(HttpHeaders.SET_COOKIE))
            .anyMatch(cookie -> cookie.contains("refresh_token="));

        ResponseEntity<Map> loginResponse = login(email, "StrongPass1", "10.0.0.1");

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().get("accessToken")).isNotNull();
    }

    // ---------- AC: duplicate email ----------

    @Test
    void register_duplicateEmail_returns409() {
        String email = "dupe@example.com";
        register("Dupe", email, "StrongPass1", "10.0.1.1");

        ResponseEntity<Map> second = register("Dupe Again", email, "StrongPass1", "10.0.1.1");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("AUTH_EMAIL_TAKEN");
    }

    // ---------- AC: generic login failure (no enumeration) ----------

    @Test
    void login_wrongPassword_returns401WithGenericMessage() {
        String email = "bob@example.com";
        register("Bob", email, "StrongPass1", "10.0.2.1");

        ResponseEntity<Map> response = login(email, "WrongPass9", "10.0.2.1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("detail")).isEqualTo("Invalid email or password.");
    }

    @Test
    void login_unknownEmail_returns401WithSameGenericMessage() {
        ResponseEntity<Map> response = login("ghost@example.com", "StrongPass1", "10.0.3.1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Identical message to the wrong-password case — prevents account enumeration.
        assertThat(response.getBody().get("detail")).isEqualTo("Invalid email or password.");
    }

    // ---------- AC: password reset email is sent ----------

    @Test
    void forgotPassword_existingUser_sendsResetEmailWithTokenLink() throws Exception {
        String email = "carol@example.com";
        register("Carol", email, "StrongPass1", "10.0.4.1");

        ResponseEntity<Void> response = rest.postForEntity("/auth/forgot-password",
            new HttpEntity<>(Map.of("email", email), jsonHeaders("10.0.4.1")), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(GreenMailUtil.getBody(received[0])).contains("token=");
    }

    // ---------- reset flow end-to-end ----------

    @Test
    void resetPassword_withEmailedToken_changesPasswordAndInvalidatesOld() throws Exception {
        String email = "dave@example.com";
        register("Dave", email, "StrongPass1", "10.0.5.1");

        rest.postForEntity("/auth/forgot-password",
            new HttpEntity<>(Map.of("email", email), jsonHeaders("10.0.5.1")), Void.class);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        String body = GreenMailUtil.getBody(greenMail.getReceivedMessages()[0]);
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_\\-]+)").matcher(body);
        assertThat(matcher.find()).as("reset email contains a token").isTrue();
        String token = matcher.group(1);

        ResponseEntity<Void> reset = rest.postForEntity("/auth/reset-password",
            new HttpEntity<>(Map.of("token", token, "newPassword", "NewStrong2"), jsonHeaders("10.0.5.1")),
            Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login(email, "NewStrong2", "10.0.5.1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, "StrongPass1", "10.0.5.1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void resetPassword_invalidToken_returns400() {
        ResponseEntity<Map> response = rest.postForEntity("/auth/reset-password",
            new HttpEntity<>(Map.of("token", "totally-invalid-token", "newPassword", "NewStrong2"),
                jsonHeaders("10.0.6.1")),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_INVALID_RESET_TOKEN");
    }

    // ---------- AC: rate limiting ----------

    @Test
    void login_after5FailedAttemptsFromSameIp_returns429() {
        String ip = "198.51.100.77";
        String email = "eve@example.com";
        register("Eve", email, "StrongPass1", ip);

        // First 5 attempts are allowed through (and fail credentials → 401).
        for (int attempt = 1; attempt <= 5; attempt++) {
            ResponseEntity<Map> response = login(email, "WrongPass9", ip);
            assertThat(response.getStatusCode())
                .as("attempt %d should be processed and rejected as bad credentials", attempt)
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Registration does not consume a rate-limit token — only logins do — so the
        // bucket starts full and the 6th login attempt is the one blocked by the limiter.
        ResponseEntity<Map> blocked = login(email, "WrongPass9", ip);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("code")).isEqualTo("AUTH_RATE_LIMIT_EXCEEDED");
    }
}
