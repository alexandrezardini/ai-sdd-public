## Local Development Environment

### Services

| Service | Container | URL | Purpose |
|---|---|---|---|
| Frontend | `video-max-frontend` | http://localhost:3000 | Next.js 14 dev server |
| Backend | `video-max-backend` | http://localhost:8080/api/v1 | Spring Boot API |
| PostgreSQL | `video-max-postgres` | `localhost:5432` | Database |
| MailHog | `video-max-mailhog` | http://localhost:8025 | Email catcher (SMTP: 1025) |

### Start everything

```bash
# 1. Start all infrastructure + frontend containers
docker compose up -d

# 2. Start the Spring Boot app inside its container (long-running — must run in background)
docker compose exec -d spring-boot-app sh -c \
  'mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/app.log 2>&1'

# 3. Tail backend logs until "Started VideoMaxApplication" appears
docker compose exec spring-boot-app sh -c \
  'until grep -qE "Started VideoMaxApplication|APPLICATION FAILED" /tmp/app.log 2>/dev/null; \
   do sleep 3; done && tail -20 /tmp/app.log'

# 4. Verify all services are healthy
curl -sf http://localhost:8080/api/v1/actuator/health
curl -sf -o /dev/null -w "%{http_code}" http://localhost:3000
```

### Stop everything

```bash
# Stop all containers (data is preserved in volumes)
docker compose down

# Stop AND delete all data (fresh DB on next start)
docker compose down -v
```

### Other useful commands

```bash
# Check status of all containers
docker compose ps

# Tail backend application logs live
docker compose exec spring-boot-app tail -f /tmp/app.log

# View logs of any container
docker compose logs -f frontend
docker compose logs -f spring-boot-app

# Restart a single service
docker compose restart frontend
docker compose restart spring-boot-app

# Rebuild images after Dockerfile or dependency changes
docker compose build frontend
docker compose build spring-boot-app
```

---

## Docker Networking

This project runs entirely in Docker containers. When configuring connections between services (database, cache, queue, etc.), **always use the Docker Compose service name** as the host — never `localhost` or `127.0.0.1`.

Inside a container, `localhost` refers to the container itself, not the host machine or other containers. Services communicate through the Docker Compose network using their service names (e.g., `db`, `spring-boot-app`).

- **Correct:** `DB_HOST=db` (the Compose service name)
- **Wrong:** `DB_HOST=localhost`

This applies to all environment variables, configuration files, and code that references service hosts.

## Working Principles

- **Single Responsibility:** each module, service, and function should have a clear, focused responsibility.
- **Type Safety:** Strict TypeScript usage across all layers.
- **Testing:** Strong emphasis on pyramid testing at all levels to ensure reliability and maintainability.
- **Code Quality:** Use ESLint and Prettier for consistent code style. Code reviews should focus on readability, maintainability, and adherence to best practices.
- **Documentation:** Comprehensive docs for architecture, setup, and troubleshooting in `docs/`.


## Git Conventions

- **Main branch:** `main` — never commit directly to it
- Branches: `feature/*`, `bugfix/*`, `hotfix/*`, `docs/*`
- **Commits:** short, descriptive messages focused on the "why" of the change
- **Workflow:** Git Flow conventions. Two long-lived branches:
    - `main` — stable, production-ready code
    - `dev` — integration branch; all feature/bugfix/hotfix branches start from `dev` and merge back into `dev`
    - When `dev` is stable, it is merged into `main`

## Testing Policy

Every change must be tested. During development, run only the tests related to the modified code. Before finishing, always run the full test suite to ensure nothing is broken.

## Scope Limits

- Work on **one feature, fix, or refactoring at a time** — do not mix scopes
- Do not include cosmetic changes (formatting, renaming) alongside functional changes
- If something out of scope comes up during work, note it as a separate task instead of acting on it
- Focus on the defined scope for each task to ensure clarity and maintainability of the codebase.
- If you identify a necessary change that is out of scope, create a new issue or task for it instead of including it in the current work.

## Agent Skill Usage

When working on any task (planning, implementing, debugging, refactoring,
reviewing, etc.), decompose the request into its underlying subtasks and
concerns, then identify which available skills match any of them and activate
those skills.

## Library Documentation Lookup

Before implementing any feature, you MUST use the **context7** MCP tool to look up the relevant library APIs and official documentation.

Always:

- Check the installed library version in the project manifest
- Retrieve the corresponding documentation using context7
- Cross-reference APIs to avoid deprecated or incompatible patterns
- Follow the official documentation over training data

Skip documentation lookup only for trivial operations such as:

- Variable declarations
- Basic control flow
- Simple CRUD using established project patterns

If a library is involved and there is uncertainty, documentation lookup is mandatory.
If the documentation returned does not match the installed version, flag the discrepancy before proceeding.