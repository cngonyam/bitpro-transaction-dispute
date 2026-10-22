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
- Node.js 18+
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
npm start -- --host 0.0.0.0 --port 63370
```

Open the app at:

```text
http://localhost:63370
```

### 3. Build the frontend

```bash
cd frontend
npm run build
```

## Local credentials

The app is seeded with a local demo user:

- Username: `dev-user`
- Password: `dev-pass123`

The frontend automatically authenticates with these values when it starts.

## API endpoints

Base URL:

```text
http://localhost:8082/api/v1
```

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

Schema and seed data are managed via Liquibase under:

```text
src/main/resources/db/changelog/
```

If the database becomes stale, remove the SQLite file and restart the backend so Liquibase recreates it.

## Notes

- The backend must be running before the frontend can load data.
- This is intended for local/demo use rather than a production banking platform.
- Port conflicts can be resolved by stopping the old Java or Node process before restarting the application.
