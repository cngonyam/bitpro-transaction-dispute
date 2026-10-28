# Bitpro Transaction Dispute Portal

A full-stack dispute management portal for Bitpro, built with a Spring Boot API and Angular frontend. The application models a realistic banking dispute workflow with review, approval, ledger updates, and reversal safeguards rather than a static status mock.

## Overview

The portal supports:

- customer account and transaction review
- dispute submission for eligible debit transactions
- dispute review progression from Open to In Review
- approval by authorised lead/admin users to issue provisional credit
- reversal processing with idempotency protection
- ledger-aware balance recalculation after critical financial actions
- durable status history and audit-friendly actor metadata

## Business rules

This project is deliberately closer to a production banking workflow than a toy demo:

- disputes are created only for valid customer-owned transactions
- review and approval are enforced by role and workflow state
- only approved disputes can be reversed
- duplicate reversal attempts are blocked
- account balances are recalculated from the transaction ledger after reversal
- review and approval history is recorded to support auditability

## Tech stack

### Backend
- Java 21
- Spring Boot 3.3.x
- Spring Web / MVC
- Spring Data JPA
- Spring Security + JWT
- SQLite with Liquibase migrations
- Spring Boot Actuator
- OpenAPI / Swagger UI

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
├─ Dockerfile
├─ docker-compose.yml
├─ README.md
├─ transaction-dispute.db
├─ target/
└─ frontend/Dockerfile
```

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+
- npm

## Local startup

### 1. Start the backend from the project root

For the verified local run in this workspace, use the dev profile and port 8083:

#### PowerShell

```powershell
Set-Location 'C:\Projects\bitpro\bitpro-transaction-dispute'
Remove-Item -Path '.\transaction-dispute.db' -Force -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE='dev'
$env:APP_SECURITY_USERNAME='agent-user'
$env:APP_SECURITY_PASSWORD='agent-pass123'
$env:SERVER_PORT='8083'
mvn spring-boot:run
```

#### Bash

```bash
cd /c/Projects/bitpro/bitpro-transaction-dispute
rm -f transaction-dispute.db
SPRING_PROFILES_ACTIVE=dev APP_SECURITY_USERNAME=agent-user APP_SECURITY_PASSWORD=agent-pass123 SERVER_PORT=8083 mvn spring-boot:run
```

The backend is exposed at:

- API: http://localhost:8083
- Health check: http://localhost:8083/actuator/health
- Swagger UI: http://localhost:8083/swagger-ui.html

If you are running a fresh local DB, deleting the SQLite file before startup ensures the schema and seed data are rebuilt cleanly.

### 2. Start the frontend

From the frontend folder:

```bash
cd frontend
npm install
npm start -- --host 0.0.0.0 --port 4200
```

Open the app here:

```text
http://localhost:4200
```

### 3. Production-style build check

```bash
cd frontend
npm run build
```

## Docker

This project also supports a containerized stack.

### Build and run

```bash
docker compose up --build -d
```

Typical endpoints:

- Frontend: http://localhost:8080
- Backend: http://localhost:8082
- Swagger UI: http://localhost:8082/swagger-ui.html

To stop the stack:

```bash
docker compose down
```

## Local users

The app includes seeded users for local testing:

- agent-user / agent-pass123
- admin-user / admin-pass123
- lead-user / lead-pass123
- customer-user / customer-pass123

### Workflow notes

- Customer users can submit disputes for their own account.
- Lead users and admin users are permitted to review and approve disputes in the review stage.
- A plain agent user is blocked from approving unless the business policy specifically treats them as a lead-level approver.

## API endpoints

Base URL:

```text
http://localhost:8083/api/v1
```

Key endpoints:

- POST /auth/login
- GET /profile
- GET /accounts
- GET /transactions
- GET /disputes
- GET /disputes/{id}
- POST /disputes
- POST /disputes/{id}/review
- POST /disputes/{id}/approve
- POST /disputes/{id}/reverse
- GET /actuator/health

### Idempotency

Create and reverse dispute actions support an `X-Idempotency-Key` header to protect against accidental duplicate financial requests.

## Database

The local SQLite database is created at the project root:

```text
transaction-dispute.db
```

Inspect it with SQLite or Python when needed:

```bash
sqlite3 transaction-dispute.db "SELECT * FROM accounts;"
sqlite3 transaction-dispute.db "SELECT * FROM transactions;"
sqlite3 transaction-dispute.db "SELECT * FROM disputes;"
```

Schema and seed management are handled with Liquibase under:

```text
src/main/resources/db/changelog/
```

## Clean reset

If the database becomes stale or the schema needs to be rebuilt:

```bash
rm transaction-dispute.db
mvn spring-boot:run
```

On PowerShell:

```powershell
Remove-Item transaction-dispute.db -Force
mvn spring-boot:run
```

## Notes

The project has been validated against the dispute service regression suite, including the lead-user approval path for disputes already in review. This is the expected operational flow for a lead or admin to approve a disputed item before reversal processing.

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
