# PostgreSQL Setup

This project requires PostgreSQL for authentication data (`users` table).

## Prerequisites

- PostgreSQL 12+
- `psql` available in your terminal
- Access to a superuser account (usually `postgres`)

## 1. Create Database and User

Open `psql` as a superuser:

```powershell
psql -U postgres
```

Run:

```sql
CREATE USER codereboot_user WITH PASSWORD 'codereboot_pass';
CREATE DATABASE codereboot OWNER codereboot_user;
GRANT ALL PRIVILEGES ON DATABASE codereboot TO codereboot_user;
\q
```

## 2. Create Schema

Connect as the application user:

```powershell
psql -U codereboot_user -d codereboot
```

Run:

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_username ON users (username);
CREATE INDEX idx_email ON users (email);
\q
```

## 3. App Configuration Mapping

The app reads DB config from environment variables with defaults in `src/main/resources/application.properties`:

- `DB_URL` (default: `jdbc:postgresql://localhost:5432/codereboot`)
- `DB_USERNAME` (default: `codereboot_user`)
- `DB_PASSWORD` (default: `codereboot_pass`)

If you used the defaults above, no extra configuration is required.

## 4. Verify

Start the app:

```powershell
./run.ps1 -SkipBuild
```

Then check:

- `http://localhost:8080/api/system/health`
- `http://localhost:8080/login.html`

Optional API sanity check:

```powershell
$body = @{
    username = "test_user"
    email = "test@example.com"
    password = "SecurePass123"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8080/api/auth/register" `
    -Method POST `
    -Headers @{"Content-Type"="application/json"} `
    -Body $body
```

Expected successful auth responses include `accessToken`.

## Troubleshooting

### Password authentication failed

- Confirm user exists:

```sql
psql -U postgres
SELECT usename FROM pg_user WHERE usename = 'codereboot_user';
```

- Reset password if needed:

```sql
ALTER USER codereboot_user WITH PASSWORD 'codereboot_pass';
```

### Database does not exist

```sql
psql -U postgres
CREATE DATABASE codereboot OWNER codereboot_user;
```

### Relation `users` does not exist

Run the schema creation block from section 2.

## Notes

- Keep production credentials out of source control.
- For production/staging, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` via environment.
