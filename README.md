# Bitpro Holdings - Transaction Dispute Portal

A full-stack dispute management application for Bitpro Holdings. The project combines a Java Spring Boot API with an Angular frontend to let users review transactions, raise disputes, and track the lifecycle of case resolution in a finance operations dashboard.

## Overview

This application models a lightweight banking operations workflow:

- View recent transactions and account activity
- Select a transaction and raise a dispute
- Capture the disputed amount, reason, and supporting description
- Review dispute records and status updates
- Persist operational data in SQLite for local development
- Expose business functionality through a REST API and browser-based UI

The portal is designed for demo and local operational use rather than production-scale banking processing.

## Tech stack

### Backend
- Java 21
- Spring Boot 3.3.4
- Spring Web
- Spring Data JPA
- Spring Security
- Hibernate with SQLite dialects
- Liquibase for schema and seed migration
- Springdoc OpenAPI / Swagger
- Spring Boot Actuator

### Frontend
- Angular 22
- TypeScript
- Standalone component structure
- Local CORS-enabled API integration

## Project structure

```text
bitpro-transaction-dispute/
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ za/co/transactiondispute/
│  │  │     ├─ config/
│  │  │     ├─ controller/
│  │  │     ├─ dto/
│  │  │     ├─ entity/
│  │  │     ├─ enums/
│  │  │     ├─ mapper/
│  │  │     ├─ repository/
│  │  │     ├─ security/
│  │  │     ├─ service/
│  │  │     └─ TransactionDisputeApplication.java
│  │  └─ resources/
│  │     ├─ application.yml
│  │     └─ db/changelog/
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

## Runtime configuration

The main application settings are defined in the backend configuration and default values in code:

- API base URL: `http://localhost:8082/api/v1`
- Frontend URL: `http://localhost:63370`
- SQLite database: `transaction-dispute.db`
- Dev credentials: `dev-user` / `dev-pass123`
- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI docs: `http://localhost:8082/v3/api-docs`
- Health endpoint: `http://localhost:8082/actuator/health`

The frontend auto-authenticates with the seeded dev user on startup to keep the operations dashboard immediately available during local development.

## Features

### Transaction review
Users can view transaction data including:
- reference numbers
- account numbers
- merchant names
- amounts
- statuses
- action controls to initiate a dispute

### Dispute creation
Users can:
- select a transaction
- enter account details
- specify the disputed amount
- choose a dispute reason
- add a narrative description
- submit a dispute for review

### Dispute tracking
The dashboard exposes dispute lifecycle information such as:
- dispute ID
- linked transaction reference
- account number
- disputed amount
- status
- created date

## API overview

Base URL: `http://localhost:8082/api/v1`

### Transactions
- `GET /transactions` - List transactions
- `GET /transactions/{reference}` - Fetch a transaction by reference
- `POST /transactions` - Create a transaction

### Disputes
- `GET /disputes` - List disputes
- `GET /disputes/{id}` - Fetch a dispute by ID
- `POST /disputes` - Submit a new dispute

### Operational endpoints
- `GET /actuator/health` - Health status
- `GET /swagger-ui.html` - Swagger UI
- `GET /v3/api-docs` - OpenAPI endpoint

## Quick start

### Prerequisites
Install the following locally:
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

The backend should be available at:

```text
http://localhost:8082
```

To confirm it is running:

```bash
curl http://localhost:8082/actuator/health
```

Expected result:

```json
{"status":"UP"}
```

### 2. Start the frontend
From the frontend folder:

```bash
cd frontend
npm install
npm start -- --host 0.0.0.0 --port 63370
```

Open:

```text
http://localhost:63370
```

### 3. Create a production build

```bash
cd frontend
npm run build
```

## Authentication and local access

The application uses Spring Security with HTTP Basic authentication for the local portal flow.

Default local credentials:
- Username: `dev-user`
- Password: `dev-pass123`

The frontend auto-logs in with these values when the app loads, so the dashboard is available immediately without a separate sign-in page.

## Database and migration flow

The app uses SQLite and stores data in the project root file:

```text
transaction-dispute.db
```

Schema creation and seed data are managed through Liquibase changelogs under:

```text
src/main/resources/db/changelog/
```

This keeps the database easy to recreate and helps ensure consistent data initialization across local runs.

> If a stale SQLite database is causing startup issues, remove the existing file and restart the backend so Liquibase can rebuild the schema cleanly.

## Notes for local development

- If a stale Java or Node process is holding the expected ports, stop it before restarting the app.
- The backend must be running before the frontend can successfully load API data.
- If you need a clean database reset during development, delete the SQLite file and restart the backend.
- The portal is intentionally focused on local, demo-friendly usage rather than a production-grade multi-tenant banking platform.


The schema is defined in the Liquibase master changelog and corresponds to the following SQLite tables:

- `accounts`
- `transactions`
- `disputes`

### Equivalent SQL schema

```sql
CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_number VARCHAR(255) NOT NULL UNIQUE,
    customer_name VARCHAR(255) NOT NULL,
    currency VARCHAR(255) NOT NULL,
    available_balance DECIMAL(38,2) NOT NULL,
    status VARCHAR(255) NOT NULL
);

CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_reference VARCHAR(255) NOT NULL UNIQUE,
    account_number VARCHAR(255) NOT NULL,
    amount DECIMAL(38,2) NOT NULL,
    currency VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE disputes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dispute_id VARCHAR(255) NOT NULL UNIQUE,
    transaction_reference VARCHAR(255) NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    disputed_amount DECIMAL(38,2) NOT NULL,
    reason_code VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### Liquibase seed data examples

These sample records are inserted through the changelog files:

```sql
INSERT INTO accounts (account_number, customer_name, currency, available_balance, status)
VALUES ('ACC-1001', 'Jane Smith', 'ZAR', 12500.50, 'ACTIVE');

INSERT INTO accounts (account_number, customer_name, currency, available_balance, status)
VALUES ('ACC-1002', 'John Doe', 'ZAR', 7800.00, 'ACTIVE');

INSERT INTO transactions (
    transaction_reference,
    account_number,
    amount,
    currency,
    type,
    merchant_name,
    status,
    transaction_date,
    description
)
VALUES (
    'TXN-10001',
    'ACC-1001',
    120.00,
    'ZAR',
    'DEBIT',
    'Clover Mall',
    'SUCCESS',
    datetime('now', '-2 days'),
    'Groceries'
);
```

```sql
INSERT INTO disputes (
    dispute_id,
    transaction_reference,
    account_number,
    disputed_amount,
    reason_code,
    description,
    status,
    created_at,
    updated_at
)
VALUES (
    'DSP-AB12CD34',
    'TXN-10001',
    'ACC-1001',
    120.00,
    'UNRECOGNISED_TRANSACTION',
    'Customer does not recognize this transaction',
    'OPEN',
    datetime('now', '-1 day'),
    datetime('now', '-3 hours')
);
```

### Useful SQL queries

#### View all accounts
```sql
SELECT * FROM accounts;
```

#### View all transactions
```sql
SELECT * FROM transactions;
```

#### View all disputes
```sql
SELECT * FROM disputes;
```

#### Show only open disputes
```sql
SELECT *
FROM disputes
WHERE status = 'OPEN';
```

#### Show transactions for a specific account
```sql
SELECT *
FROM transactions
WHERE account_number = 'ACC-1001';
```

#### Show disputes linked to a transaction
```sql
SELECT d.*
FROM disputes d
WHERE d.transaction_reference = 'TXN-10001';
```

#### Show total disputed amount by status
```sql
SELECT status, SUM(disputed_amount) AS total_disputed
FROM disputes
GROUP BY status;
```

#### Show recent transactions in descending order
```sql
SELECT *
FROM transactions
ORDER BY transaction_date DESC;
```

### Database interaction notes

- The database file is stored at the project root as `transaction-dispute.db`.
- Hibernate creates the tables automatically when the application starts.
- The application uses SQLite, so the SQL syntax is standard SQLite SQL.
- All table names are generated from the JPA `@Table` annotations, which is why the actual names are `accounts`, `transactions`, and `disputes`.

## Updating tables with Liquibase

When you need to change the database schema, do not edit the SQLite file manually. Instead, add a new Liquibase change set in the master changelog.

### 1. Open the changelog

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

### 2. Add a new change set

Example:

```yaml
databaseChangeLog:
  - changeSet:
      id: 007-add-email-to-accounts
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

### 3. Restart the application

After saving the changelog, restart the backend so Liquibase applies the new changes.

```bash
mvn spring-boot:run
```

### 4. If the database is stale, recreate it

If the old SQLite file still has an earlier version of the schema, delete it before restarting:

```bash
rm transaction-dispute.db
mvn spring-boot:run
```

On Windows PowerShell:

```powershell
Remove-Item transaction-dispute.db
mvn spring-boot:run
```

### 5. Verify the new column or table exists

```sql
PRAGMA table_info(accounts);
```

or:

```sql
SELECT * FROM accounts;
```

### Important Liquibase rules

- Every schema change must be added as a new change set with a unique `id`.
- Never reuse the same change set `id` once it has been applied.
- Keep changes small, versioned, and easy to roll back.
- Use `addColumn`, `dropColumn`, `modifyDataType`, `createTable`, and `insert` as needed for your migration.

## Testing and validation

The backend was validated with Maven build commands and a live health check. The app responded successfully on the expected port.

Example verification commands:

```bash
mvn clean install
mvn test
curl http://localhost:8081/actuator/health
```

## Troubleshooting

### Port 8081 already in use
On Windows, stop the process using the port:

```powershell
Get-NetTCPConnection -LocalPort 8081
Stop-Process -Id <PID>
```

### Stale Java build artifacts
If you see startup or class loading problems, clean the Maven build before restarting:

```bash
mvn clean
mvn spring-boot:run
```

### Frontend package or registry issues
If npm complains about package resolution:

```bash
npm config set registry https://registry.npmjs.org/
```

## Summary

This project is a practical Java + Angular example of a transaction dispute management dashboard. It demonstrates:

- backend API design,
- database persistence,
- data seeding,
- CORS-enabled frontend integration,
- dispute capture and tracking,
- local deployment using SQLite.

It is suitable for demo purposes, internal operations workflows, or as a starting point for a larger financial platform.
