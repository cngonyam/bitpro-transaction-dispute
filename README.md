# Bitpro Holdings - Transaction Dispute Portal

A full-stack transaction dispute management application built for Bitpro Holdings. The project combines a Java Spring Boot API with an Angular frontend to let users review transactions, submit disputes, and track dispute status in a clean, business-style dashboard.

Created by Ndumiso Ngonyama.

## Project purpose

This application was designed to model a real-world banking and payment operations workflow:

- View recent account transactions
- Select a transaction and raise a dispute
- Capture the disputed amount, reason, and description
- Track submitted disputes through their lifecycle
- Store transaction and dispute data in SQLite for simple local persistence
- Expose the backend through REST endpoints and a web UI

The solution is intended to simulate a lightweight internal operations portal for financial dispute handling.

## Architecture

### Backend
- Java 21
- Spring Boot 3.3.4
- Spring Web
- Spring Data JPA
- Hibernate
- SQLite database
- Spring Security
- Springdoc OpenAPI / Swagger
- Actuator for health checks

### Frontend
- Angular 22
- TypeScript
- Standalone components
- API integration with CORS enabled for local development

### Data model
The app stores:
- Accounts
- Transactions
- Disputes

Sample seed data is inserted automatically when the database is empty.

## Project structure

```text
bitpro-transaction-dispute/
├─ src/
│  └─ main/
│     ├─ java/
│     │  └─ za/co/transactiondispute/
│     │     ├─ config/
│     │     ├─ controller/
│     │     ├─ dto/
│     │     ├─ entity/
│     │     ├─ enums/
│     │     ├─ mapper/
│     │     ├─ repository/
│     │     ├─ service/
│     │     └─ TransactionDisputeApplication.java
│     └─ resources/
│        └─ application.yml
├─ frontend/
│  ├─ src/
│  ├─ package.json
│  └─ angular.json
├─ pom.xml
├─ transaction-dispute.db
├─ README.md
└─ target/
```

## Runtime configuration

The backend is configured in `src/main/resources/application.yml`:

- App name: `transaction-dispute-api`
- Database: SQLite file `transaction-dispute.db`
- Server port: `8081`
- Default admin credentials:
  - Username: `admin`
  - Password: `admin123`
- Swagger docs: `/swagger-ui.html`
- OpenAPI docs: `/v3/api-docs`
- Health endpoint: `/actuator/health`

## Features

### Transaction view
Users can see a list of transactions with:
- transaction reference
- account number
- merchant name
- amount
- status
- action buttons

### Dispute submission
Users can:
- select a transaction,
- enter account details,
- set the disputed amount,
- choose a reason code,
- add a description,
- submit the dispute.

### Dispute tracking
The disputes screen shows:
- dispute ID
- linked transaction reference
- account number
- disputed amount
- status
- created date

## API endpoints

Base URL: `http://localhost:8081/api/v1`

### Transactions
- `GET /api/v1/transactions` - View all transactions
- `GET /api/v1/transactions/{reference}` - View one transaction by reference
- `POST /api/v1/transactions` - Create a transaction

### Disputes
- `GET /api/v1/disputes` - View all disputes
- `GET /api/v1/disputes/{id}` - View one dispute by ID
- `POST /api/v1/disputes` - Submit a new dispute

### Monitoring
- `GET /actuator/health` - Health status
- `GET /swagger-ui.html` - Swagger UI
- `GET /v3/api-docs` - OpenAPI JSON

## Build and run instructions

### 1. Prerequisites
Install:
- Java 21+
- Maven 3.9+
- Node.js 18+
- npm

### 2. Backend build and run
From the project root:

```bash
mvn clean install
mvn spring-boot:run
```

The backend will start on:

```text
http://localhost:8081
```

To verify it is running:

```bash
curl http://localhost:8081/actuator/health
```

Expected output should include:

```json
{"status":"UP"}
```

### 3. Frontend build and run
From the `frontend` folder:

```bash
cd frontend
npm install
npm start -- --host 0.0.0.0 --port 4200
```

The Angular app usually runs on:

```text
http://localhost:4200
```

If port 4200 is already in use, Angular will prompt to use another port automatically. A typical fallback is:

```text
http://localhost:55698
```

To create a production build:

```bash
cd frontend
npm run build
```

### Frontend startup notes
- The backend must already be running on port 8081 before the frontend can load API data.
- If Angular asks: `Would you like to use a different port?`, answer `Y` and continue with the suggested port.
- The app expects the backend at `http://localhost:8081/api/v1` and uses CORS for local development.
- If you want to launch it without the interactive prompt, use a free port explicitly:

```bash
cd frontend
npm start -- --host 0.0.0.0 --port 4200
```

or another available port such as:

```bash
cd frontend
npm start -- --host 0.0.0.0 --port 4300
```

## Database

The application uses SQLite and stores data in the project root file:

```text
transaction-dispute.db
```

The schema and seed data are managed with Liquibase, not a Java seeder. The master changelog lives in:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

This ensures the database is created and populated consistently every time the app starts from a clean database.

> If the SQLite file already exists and contains stale tables, delete it before restarting the backend so Liquibase can apply the changesets cleanly.

## Data seeding and migration flow

The project uses Liquibase change sets to:

1. Create the `accounts` table
2. Create the `transactions` table
3. Create the `disputes` table
4. Insert sample account records
5. Insert sample transaction records
6. Insert sample dispute records

This approach keeps database changes versioned and repeatable, and avoids runtime seeding logic in Java.

## SQL scripts and database schema

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
