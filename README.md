# Bitpro Transaction Dispute Portal

A full-stack dispute management app for Bitpro Holdings, built with a Spring Boot API and Angular frontend.

## Overview

This project lets users:

- review recent transactions
- raise a dispute against a transaction
- capture the disputed amount, reason, and description
- track dispute status through the application workflow
- run locally with SQLite and seeded demo data

## Tech stack

### Backend
- Java 21
- Spring Boot 3.3.x
- Spring Web
- Spring Data JPA
- Spring Security
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
├─ transaction-dispute.db
├─ README.md
└─ target/
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
mvn clean install
mvn spring-boot:run
```

The API will be available at:

- `http://localhost:8082`
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

This is the recommended way to run the full app in a single stack.

### Build and run with Docker Compose

From the project root:

```bash
docker compose up --build -d
```

The app will be available at:

- Frontend: `http://localhost:8080`
- Backend: `http://localhost:8082`
- Swagger UI: `http://localhost:8082/swagger-ui.html`

The frontend uses a reverse proxy so browser requests are sent to `/api`, which is forwarded to the backend container.

To stop the containers:

```bash
docker compose down
```

### Docker files included

- `Dockerfile` for the backend
- `docker-compose.yml` for running both services
- `frontend/Dockerfile` for the Angular app
- `frontend/nginx.conf` for reverse proxying `/api` to the backend

## Local credentials

The app is seeded with a local demo user:

- Username: `dev-user`
- Password: `dev-pass123`

The frontend automatically authenticates with these values when it starts.

## API endpoints

Base URL for direct backend access:

```text
http://localhost:8082/api/v1
```

When running via Docker, the frontend is served from `http://localhost:8080` and proxies `/api` to the backend container.

Key endpoints:

- `GET /transactions`
- `GET /transactions/{reference}`
- `POST /transactions`
- `GET /disputes`
- `GET /disputes/{id}`
- `POST /disputes`
- `GET /actuator/health`

## Database

The local database is stored at the project root:

```text
transaction-dispute.db
```

You can inspect it directly with SQLite:

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

## Using Liquibase to update the database

This project does not use a separate Liquibase CLI plugin. The standard workflow is:

1. Add a new changeSet to the master changelog file:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

2. Use a unique, incremental changeSet id such as `008-add-new-column`.

3. Restart the backend so Spring Boot applies the pending Liquibase changes automatically.

```bash
mvn spring-boot:run
```

Example changeSet:

```yaml
databaseChangeLog:
  - changeSet:
      id: 008-add-email-to-accounts
      author: Ndumiso Ngonyama
      changes:
        - addColumn:
            tableName: accounts
            columns:
              - column:
                  name: email
                  type: VARCHAR(255)
                  constraints:
                    nullable: true
```

### When you need a clean reset

If the SQLite file is stale or a migration has already been applied in a bad state:

```bash
rm transaction-dispute.db
mvn spring-boot:run
```

On Windows PowerShell:

```powershell
Remove-Item transaction-dispute.db
mvn spring-boot:run
```

This forces Liquibase to rebuild the schema from the changelog files.

### Liquibase rules

- Add each schema change as a new `changeSet`.
- Never reuse an existing `id`.
- Keep migrations small and versioned.
- Restart the app after changing the changelog so Liquibase can apply the update.

## Notes

- The backend must be running before the frontend can load data.
- This is intended for local/demo use rather than a production banking platform.
- Port conflicts can be resolved by stopping the old Java or Node process before restarting the application.
