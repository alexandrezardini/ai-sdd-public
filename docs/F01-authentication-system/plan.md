# Implementation Plan: F01. Authentication System

**Prerequisites:**
- Java 21 LTS (Temurin) installed
- Maven 3.9+
- Node.js 20 LTS + pnpm (or npm)
- Docker + Docker Compose for local PostgreSQL 16 and MailHog
- Environment variables ready to be provided (documented in `.env.example`): `APP_JWT_SECRET`, `APP_JWT_ISSUER`, database credentials, SMTP host/port/user/from, `APP_MAIL_RESET_LINK_BASE`, `APP_RATE_LIMIT_TRUST_PROXY`
- IDE with Java 21 language level and Lombok plugin enabled

---

## Stage 1: Backend scaffolding and infrastructure

**1. Maven project bootstrap** — Initialize `video-max-backend/` as a Spring Boot 3.3 Maven project targeting Java 21, wiring in the dependencies described in the spec (Web, Data JDBC, Security, OAuth2 Resource Server, Validation, Actuator, Mail, Liquibase, PostgreSQL driver, Bucket4j Caffeine, Lombok, Testcontainers, Spring Modulith).

**2. Application entry point and configuration properties** — Create the `VideoMaxApplication` main class and the `JwtProperties` / `MailProperties` `@ConfigurationProperties` records with `@ConfigurationPropertiesScan`. Add `application.yml`, `application-dev.yml`, and `application-prod.yml` with the base, local, and production profiles referenced in the spec.

**3. Local infrastructure via Docker Compose** — Add a root `docker-compose.yml` running Postgres 16 and MailHog. Provide a `.env.example` covering both apps' variables so the developer can copy it into `.env` and start immediately.

**4. Global cross-cutting configuration** — Implement `SecurityConfig` (stateless filter chain, public auth endpoints, CORS, method security), `RateLimitConfig` (Caffeine cache and Bucket4j `ProxyManager`), and `GlobalExceptionHandler` with `@RestControllerAdvice` mapping validation and domain exceptions to `ProblemDetail`. Refer to the spec for the endpoint permit list and error codes.

---

## Stage 2: Auth data model and persistence

**5. Liquibase master and initial changelogs** — Create `db/changelog/db.changelog-master.yaml` and the three initial changelogs listed in the spec's Data Model section (`users`, `refresh_tokens`, `password_reset_tokens`) with `pgcrypto` enabled and rollback blocks for every changeset.

**6. Spring Data JDBC entities** — Model `User`, `RefreshToken`, and `PasswordResetToken` as immutable Java classes with `@Table`, `@Id`, `@CreatedDate`, and `AggregateReference` for cross-aggregate references, matching the schema in the spec.

**7. Repositories** — Define `CrudRepository` interfaces for each entity with the query methods listed in the spec's Component Overview (case-insensitive email lookup, token-hash lookup, bulk delete by user).

---

## Stage 3: Security infrastructure

**8. JWT service** — Implement `JwtService` to sign and verify HS256 access and refresh tokens using the secret and TTLs from `JwtProperties`. The service must expose token issuance, parsing, and expiry inspection so higher layers can call it without touching a JWT library directly.

**9. Access-token filter** — Add `JwtAuthenticationFilter` that runs once per request, extracts the `Authorization: Bearer` header, validates the token via `JwtService`, and populates the `SecurityContext` with the resolved principal. Wire it before `UsernamePasswordAuthenticationFilter` in `SecurityConfig`.

**10. Password encoder and error advice wiring** — Register a `BCryptPasswordEncoder(12)` bean and add `@ExceptionHandler` methods in `GlobalExceptionHandler` for every domain exception listed in the spec's Component Overview so each maps to the correct `ProblemDetail` status and code.

---

## Stage 4: Auth business services

**11. Refresh token service** — Implement `RefreshTokenService` covering opaque secret generation, SHA-256 hashing, persistence, rotation on refresh, revocation on logout, and bulk revocation on password reset, as described in the spec.

**12. Password reset service** — Implement `PasswordResetService` handling token creation with a 30-minute expiry, hash-based lookup, expiry / consumption checks, and marking `consumedAt` on successful use. Ensure repeated use of the same token fails.

**13. Email service** — Implement `EmailService` using `JavaMailSender` and configurable `MailProperties`, sending the reset email asynchronously so the request thread never blocks. In dev the SMTP target is MailHog; in prod it is any SMTP-compatible provider.

**14. Login rate limiter** — Implement `LoginRateLimiter` wrapping Bucket4j with a Caffeine-backed `ProxyManager`, applying the 5-token-per-15-minute policy per IP. Expose a `tryConsume(ip)` method used by `AuthService.login` before checking credentials.

**15. Auth service orchestration** — Implement `AuthService` combining the previous services to handle register, login, refresh, logout, forgot-password, and reset-password. Enforce the enumeration guards, generic error messages, and rate-limiting checks described in the spec's Technical Decisions and API Contracts sections.

---

## Stage 5: Auth REST endpoints

**16. Auth DTOs** — Create the request/response records in `com.videomax.backend.auth.dto` with Bean Validation annotations (`@Email`, `@NotBlank`, `@Size`, custom `@ValidPassword` for complexity). Field constraints must mirror the spec's API Contracts tables exactly.

**17. Auth controller** — Implement `AuthController` under `/api/v1/auth` with the seven endpoints listed in the spec (register, login, refresh, logout, forgot-password, reset-password, me). Controllers stay thin — delegate to `AuthService`, set/clear the refresh cookie, and let the advice handle exceptions.

**18. Modularity verification** — Add a Spring Modulith test using `ApplicationModules.of(VideoMaxApplication.class).verify()` to ensure the `auth` module respects encapsulation and no other module reaches into `auth.internal`.

---

## Stage 6: Frontend scaffolding and auth UI

**19. Next.js project bootstrap** — Initialize `video-max-frontend/` with the App Router, TypeScript, Tailwind, and shadcn/ui. Configure the base theme tokens, path aliases, and `NEXT_PUBLIC_API_BASE` env variable pointing at the backend.

**20. Auth client and context** — Create `src/lib/auth/client.ts` with typed wrappers for every backend endpoint and a 401-triggered silent refresh interceptor. Create `src/lib/auth/context.tsx` exposing `useAuth()` with `login`, `logout`, `refresh`, and current `user`, plus a boot-time silent refresh when a refresh cookie is present.

**21. Shared Zod schemas** — In `src/lib/auth/schemas.ts`, define Zod schemas that mirror the backend Bean Validation rules for every form. These feed both client-side form validation and TypeScript types for the API client.

**22. Auth pages and forms** — Build the four auth pages (`login`, `register`, `forgot-password`, `reset-password`) inside the `(auth)` route group, each rendering a shadcn/RHF form component. Handle happy paths (redirect to `/library`) and error mapping to inline field errors or toasts.

**23. Route protection and app shell** — Implement `src/middleware.ts` to gate protected route groups by checking for the presence of the refresh cookie. Provide a minimal `(app)/library/page.tsx` placeholder with the `AppHeader` (name + logout) so subsequent features (F06) plug into an existing shell.
