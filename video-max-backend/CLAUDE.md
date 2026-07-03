# CLAUDE.md

## Environment Startup Verification

**Default behavior:** `docker compose up -d` starts `db`, `mailhog`, and `spring-boot-app` containers, but the Spring Boot application must be started manually inside the container (the Dockerfile uses `CMD ["tail", "-f", "/dev/null"]` to keep the container alive without auto-launching the app).

**Full startup sequence:**

```bash
# 1. Start all containers (from project root)
docker compose up -d

# 2. Verify containers are healthy
docker compose ps   # db must be "healthy"; spring-boot-app must be "running"

# 3. Start the Spring Boot app inside the container (long-running — run in background)
docker compose exec -d spring-boot-app sh -c \
  'mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/app.log 2>&1'

# 4. Wait for startup and tail logs
docker compose exec spring-boot-app sh -c \
  'until grep -qE "Started VideoMaxApplication|APPLICATION FAILED" /tmp/app.log 2>/dev/null; \
   do sleep 3; done && tail -30 /tmp/app.log'
```

Then verify each service is ready:

- **Spring Boot API:** `curl http://localhost:8080/api/v1/actuator/health` — expect `{"status":"UP"}`
- **PostgreSQL:** `docker compose exec db pg_isready -U postgres` — expect `accepting connections`
- **MailHog UI:** http://localhost:8025

## Development Environment

This project runs inside Docker. Always use the container for development:

```bash
# Start containers (from project root)
docker compose up -d

# Run the application (inside container)
docker compose exec spring-boot-app mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run in background
docker compose exec -d spring-boot-app sh -c \
  'mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/app.log 2>&1'
```

Services:
- `spring-boot-app` — Spring Boot API, port `8080`
- `db` — PostgreSQL 16, port `5432`, database `video_max_dev`, user/password `postgres`
- `mailhog` — SMTP mock, SMTP port `1025`, Web UI port `8025`

All verification and teardown commands run on the **host machine**:

```bash
# Verify Spring Boot is running
curl http://localhost:8080/api/v1/actuator/health

# Verify PostgreSQL is ready
docker compose exec db pg_isready -U postgres

# Tail application logs
docker compose exec spring-boot-app tail -f /tmp/app.log

# Check container logs
docker compose logs spring-boot-app
docker compose logs db

# Tear down containers (preserves the postgres_data volume — data survives)
docker compose down

# Tear down containers AND delete all data
docker compose down -v
```

## Commands (run inside the container via `docker compose exec spring-boot-app <cmd>`)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # Run application
mvn clean package -DskipTests                         # Build JAR
mvn test                                              # Unit tests
mvn clean                                             # Clean build artifacts
```

## Long-running Processes

Commands that never exit (application server) must be run in background in the Bash tool — otherwise the agent blocks indefinitely waiting for the process to return.

This applies to: `mvn spring-boot:run` and any other persistent process.

## Architecture

Spring Boot 3.3 with Spring Modulith. Source lives in `src/main/java/com/videomax/backend/`, compiled output in `target/`.

- `config/` — Cross-cutting concerns: Security, JWT filter, exception handler, rate limit config
- `auth/` — Authentication module (public API: `AuthController`, `dto/`)
- `auth/internal/` — Domain logic: services, entities, repositories, exceptions

## Troubleshooting

### `FATAL: role "postgres" does not exist` or database errors

**Fix:** Remove the volume and restart:

```bash
docker compose down -v   # stops containers AND removes the postgres_data volume
docker compose up -d     # fresh init
```

### Application fails to start (port already in use)

```bash
# Check what is using port 8080
lsof -i :8080

# Or restart just the backend container
docker compose restart spring-boot-app
```

### View full application logs

```bash
docker compose exec spring-boot-app cat /tmp/app.log
```
