# Bitpro Transaction Dispute Portal

A full-stack dispute management portal for Bitpro, built with a Spring Boot API and Angular frontend. The backend uses SQLite and Liquibase for local persistence, and the dispute flow is aligned to a real money-movement workflow rather than a state-only mock.

## Overview

The portal supports:

- reviewing the current customer account and transaction history
- submitting a dispute for a transaction
- tracking dispute lifecycle states through review and resolution
- reversing a dispute with an idempotency key
- creating a real credit ledger entry when a dispute is reversed
- recalculating account balances after each relevant ledger change

## Business behavior

This application is intentionally modeled as a ledger-aware dispute workflow:

- a dispute is created for a specific transaction and account
- a reversal is only allowed for valid dispute states
- reversing a dispute creates a dedicated credit transaction entry
- the affected account balances are recalculated from the full transaction ledger
- duplicate requests are protected with idempotency keys

This is a meaningful improvement over simplistic “status-only” dispute reversal, which would not reflect actual financial movement.

## Tech stack

### Backend
- Java 21
- Spring Boot 3.3.x
- Spring Web
- Spring Data JPA
- Spring Security + JWT
- SQLite with Liquibase migrations
- Swagger / OpenAPI
- Spring Boot Actuator

### Frontend
- Angular 22
- TypeScript
- Standalone components

## Project structure

```text
bitpro-transaction-dispute/
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  └─ resources/
│  └─ test/
├─ frontend/
│  ├─ src/
│  ├─ angular.json
│  ├─ package.json
│  └─ tsconfig.json
├─ pom.xml
├─ docker-compose.yml
├─ Dockerfile
├─ transaction-dispute.db
├─ README.md
├─ target/
└─ frontend/Dockerfile
```

## Quick start

### Prerequisites
- Java 21
- Maven 3.9+
- Node.js 22+
- npm

### 1. Start the backend

From the project root:

```bash
mvn spring-boot:run
```

For a fresh local DB rebuild, remove the SQLite file before starting:

```bash
rm transaction-dispute.db
mvn spring-boot:run
```

On Windows PowerShell:

```powershell
Remove-Item transaction-dispute.db -Force
mvn spring-boot:run
```

The API will be available at:

- Backend: `http://localhost:8082`
- Health check: `http://localhost:8082/actuator/health`
- Swagger UI: `http://localhost:8082/swagger-ui.html`

### 2. Start the frontend

From the frontend folder:

```bash
cd frontend
npm install
npm start -- --host 0.0.0.0 --port 4200
```

Open the app at:

```text
http://localhost:4200
```

### 3. Build the frontend

```bash
cd frontend
npm run build
```

## Docker deployment

The repository also supports a container-based local stack.

### Build and run with Docker Compose

From the project root:

```bash
docker compose up --build -d
```

The app is exposed as:

- Frontend: `http://localhost:8080`
- Backend: `http://localhost:8082`
- Swagger UI: `http://localhost:8082/swagger-ui.html`

To stop the stack:

```bash
docker compose down
```

### Docker files included

- `Dockerfile` for the backend
- `docker-compose.yml` for orchestration
- `frontend/Dockerfile` for the Angular app
- `frontend/nginx.conf` for reverse proxying frontend requests to the backend

## Local credentials

The app is seeded with demo users for local testing:

- Default frontend sign-in: `customer-user` / `customer-pass123`
- Agent workflow user: `agent-user` / `agent-pass123`

The frontend defaults to the customer account so the standard local demo flow opens with a customer session, while the agent account remains available for dispute review and reversal actions.

## API endpoints

Base URL for direct backend access:

```text
http://localhost:8082/api/v1
```

Key endpoints:

- `POST /auth/login`
- `GET /profile`
- `GET /accounts`
- `GET /accounts/{id}`
- `POST /accounts`
- `GET /transactions`
- `GET /transactions/{reference}`
- `POST /transactions`
- `GET /disputes`
- `GET /disputes/{id}`
- `POST /disputes`
- `POST /disputes/{id}/reverse`
- `GET /actuator/health`

### Idempotency

Create and reverse dispute requests support an `X-Idempotency-Key` header. This prevents accidental duplicate execution of a financial action.

## Database

The local SQLite database lives at the project root:

```text
transaction-dispute.db
```

You can inspect the data directly with SQLite:

```bash
sqlite3 transaction-dispute.db "SELECT * FROM accounts;"
sqlite3 transaction-dispute.db "SELECT * FROM transactions;"
sqlite3 transaction-dispute.db "SELECT * FROM disputes;"
```

If `sqlite3` is not installed, use Python instead:

```bash
python -c "import sqlite3; conn=sqlite3.connect('transaction-dispute.db'); print(conn.execute('SELECT * FROM accounts LIMIT 10').fetchall())"
python -c "import sqlite3; conn=sqlite3.connect('transaction-dispute.db'); print(conn.execute('SELECT * FROM transactions LIMIT 10').fetchall())"
python -c "import sqlite3; conn=sqlite3.connect('transaction-dispute.db'); print(conn.execute('SELECT * FROM disputes LIMIT 10').fetchall())"
```

Schema and seed data are managed via Liquibase under:

```text
src/main/resources/db/changelog/
```

## Liquibase and clean reset

This project uses Liquibase for schema evolution. The standard workflow is:

1. Add a new changeSet to the master changelog file:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

2. Use a unique incremental changeSet id.

3. Restart the backend so Spring Boot applies the pending migration.

```bash
mvn spring-boot:run
```

### Clean reset

If the database is stale or the schema needs to be rebuilt from scratch:

```bash
rm transaction-dispute.db
mvn spring-boot:run
```

On Windows PowerShell:

```powershell
Remove-Item transaction-dispute.db -Force
mvn spring-boot:run
```

This forces Liquibase to rebuild the schema from the changelog files.

## Important rules and expectations

- Every dispute reversal creates a concrete ledger credit transaction.
- Account balances are recalculated from actual transaction records.
- Dispute states are validated before transition.
- Only the current customer account is accessible for a signed-in user.
- The app is meant for local/demo validation and is not a complete production banking implementation.

## Notes

- The backend must be running before the frontend can load data.
- Port conflicts can be resolved by stopping the old Java or Node process before restarting the application.
- The app was validated from a clean DB state with the backend health endpoint reporting `UP` and login succeeding for the seeded demo user.
