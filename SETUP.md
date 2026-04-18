# CodeReboot Arena Setup

This file is only for local setup and daily run commands.

## Prerequisites

- Java 21
- Maven 3.9+
- Cloudefared
- PostgreSQL running locally (default DB config is in `src/main/resources/application.properties`)

Quick checks:

```powershell
java -version
mvn -version
```

## First-Time Setup

1. Create database and users table

- Follow `docs/POSTGRES_SETUP.md`.
- Default local credentials expected by the app:
   - DB: `codereboot`
   - User: `codereboot_user`
   - Password: `codereboot_pass`

2. Build once

```powershell
mvn clean package -DskipTests
```

## Run Locally

Recommended on Windows:

```powershell
./run.ps1
```

Useful variants:

```powershell
# Faster startup during iteration
./run.ps1 -SkipBuild

# Custom port
./run.ps1 -SkipBuild -Port 8081

# Enable hit-claim diagnostics
./run.ps1 -SkipBuild -EnableHitClaimDiagnostics
```

Alternative (without script):

```powershell
mvn spring-boot:run
```

## Verify Startup

After startup, open:

- `http://localhost:8080/login.html`
- `http://localhost:8080/api/system/health`

## Common Commands

```powershell
# Run tests
mvn test

# Full verification
mvn verify

# Clean artifacts
mvn clean
```

## Troubleshooting

### Port already in use

- `run.ps1` already checks this and exits with guidance.
- Use another port: `./run.ps1 -Port 8081`

### Maven not found

- Install Maven and add `mvn`/`mvn.cmd` to `PATH`.
- Or use the repo script `./run.ps1`, which also checks common local Maven locations.

### Database/auth errors on startup

- Confirm PostgreSQL is running.
- Confirm DB/user/password match `application.properties` or environment variables.
- Re-run steps in `docs/POSTGRES_SETUP.md`.

## Environment Variable Overrides (Optional)

You can override defaults without editing files:

- `SERVER_PORT`
- `SERVER_ADDRESS`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_JWT_SECRET`
- `APP_AUTH_TOKEN_TTL_MINUTES`

If `APP_JWT_SECRET` is not set, the app generates an in-memory ephemeral secret at startup. This works for local development, but all existing tokens become invalid after restart.

## Related Docs

- `README.md` for overview and architecture
- `docs/POSTGRES_SETUP.md` for database initialization
- `docs/DEPLOYMENT.md` for public access/deployment notes
