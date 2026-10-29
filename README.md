# Bitpro Transaction Dispute Portal

Spring Boot 3 / Java 21 API and Angular frontend for dispute submission, review, approval and reversal.

## Roles

| Role | Submit | Review | Approve | Reverse | Create accounts |
|------|:------:|:------:|:-------:|:-------:|:---------------:|
| CUSTOMER | yes | - | - | - | - |
| AGENT | yes | yes | - | yes | - |
| LEAD | yes | yes | yes | yes | - |
| ADMIN | yes | yes | yes | yes | yes |

The submitter of a dispute cannot approve or reverse it. Roles cannot be changed through the profile API.

## Local run

Requirements: Java 21, Maven 3.9+, Node 22+.

Backend (SQLite, demo users enabled):

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:JWT_SECRET = 'local-dev-secret-at-least-32-characters-long'
$env:APP_SECURITY_DEMO_USERS_ENABLED = 'true'
mvn spring-boot:run
```

Frontend (second terminal):

```powershell
cd frontend
npm install
npm start
```

- Backend: http://localhost:8082
- Frontend: http://localhost:4200
- Swagger UI: http://localhost:8082/swagger-ui.html

Demo logins:
- `agent-user / agent-pass123`
- `lead-user / lead-pass123`
- `admin-user / admin-pass123`
- `customer-user / customer-pass123`

## Docker

```bash
docker compose up --build -d
```

- Backend: http://localhost:8082
- Frontend: http://localhost:8080
- Stop everything: `docker compose down -v`

To use a custom secret and database password:

```bash
JWT_SECRET="$(openssl rand -hex 32)" POSTGRES_PASSWORD="$(openssl rand -hex 16)" docker compose up --build -d
```

The compose stack uses PostgreSQL 16. The local backend and compose backend both use port 8082, so run one at a time.

## Configuration

| Variable | Purpose |
|----------|---------|
| `JWT_SECRET` | Required; minimum 32 characters |
| `APP_SECURITY_DEMO_USERS_ENABLED` | Enables demo users for local testing |
| `APP_DB_PATH` | SQLite file location |
| `SPRING_PROFILES_ACTIVE=postgres` | Use PostgreSQL |
| `APP_DB_URL` / `APP_DB_USERNAME` / `APP_DB_PASSWORD` | PostgreSQL connection |
| `POSTGRES_PASSWORD` | Compose database password |

## API

Base path: `/api/v1`

- `POST /auth/login`
- `GET /accounts`, `POST /accounts` (admin)
- `GET /transactions`
- `GET /disputes`, `GET /disputes/{id}`, `GET /disputes/{id}/history`
- `POST /disputes` (requires `X-Idempotency-Key`)
- `POST /disputes/{id}/review`, `POST /disputes/{id}/approve`
- `POST /disputes/{id}/reverse` (requires `X-Idempotency-Key`)

The same idempotency key with the same payload returns the same result. The same key with a different payload returns 400. Reversing an already resolved dispute returns 409.

## Database and concurrency

- Default profile uses SQLite.
- PostgreSQL is supported via `SPRING_PROFILES_ACTIVE=postgres`.
- Liquibase changeSets live in `src/main/resources/db/changelog/changes/` and the master file includes them.
- The append-only audit tables are SQLite-only in one changeSet and PostgreSQL-only in another.
- Reversal uses a row lock on PostgreSQL, with version checks and unique constraints as a second line of defence.

## Tests

```bash
mvn verify                                  # backend tests and JaCoCo gate
cd frontend && npm test -- --no-watch      # frontend unit tests
```

`ConcurrentReversalPostgresTest` runs on PostgreSQL through Testcontainers and is skipped automatically when Docker is unavailable.

## Notes

This is still a demo-grade setup. For production, replace local users with an OIDC provider, move secrets to a secret manager, add database pagination, and add an outbox for notifications.
