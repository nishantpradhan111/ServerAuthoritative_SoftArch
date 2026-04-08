# PostgreSQL Authentication Setup Guide

The authentication system requires a PostgreSQL database. This guide walks through the setup process.

## Prerequisites

- PostgreSQL 12+ installed ([Download here](https://www.postgresql.org/download/))
- Access to PostgreSQL command-line tools (`psql`)
- Administrator or superuser password for PostgreSQL

## Quick Setup (Windows with PostgreSQL Installed)

### 1. Create Database and User

Open PowerShell and run:

```powershell
# Set PostgreSQL password (replace with your setup password)
$pgPassword = "your-superuser-password"

# Create the user and database
$sqlCommands = @"
CREATE USER codereboot_user WITH PASSWORD 'codereboot_pass';
CREATE DATABASE codereboot OWNER codereboot_user;
GRANT ALL PRIVILEGES ON DATABASE codereboot TO codereboot_user;
"@

$sqlCommands | & psql -U postgres
```

Or manually using `psql`:

```sql
-- Connect to default 'postgres' database as superuser
psql -U postgres

-- Inside psql prompt:
CREATE USER codereboot_user WITH PASSWORD 'codereboot_pass';
CREATE DATABASE codereboot OWNER codereboot_user;
GRANT ALL PRIVILEGES ON DATABASE codereboot TO codereboot_user;
\q
```

### 2. Initialize Database Schema

Connect as the new user and create the users table:

```bash
psql -U codereboot_user -d codereboot
```

Then in the psql prompt:

```sql
-- Create users table with BCrypt password hashes
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for faster lookups
CREATE INDEX idx_username ON users (username);
CREATE INDEX idx_email ON users (email);

-- Verify table was created
\dt users
\q
```

## Verify Connection

Test the connection from your application:

```bash
# In the CodeReboot project directory
cd "C:\Nishant\Work\BITS - Engineering\3-2\Soft Arch\Project\CodeReboot"

# Start the Spring Boot application
./run.ps1
```

If successful, you'll see:
```
Tomcat started on port 8080 (http) with context path '/'
```

## Configuration Details

The database connection is configured in `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/codereboot
spring.datasource.username=codereboot_user
spring.datasource.password=codereboot_pass
spring.jpa.hibernate.ddl-auto=validate
```

### Configuration Parameters

- **url**: PostgreSQL connection string. Change `localhost:5432` if your PostgreSQL is on another host/port
- **username**: Database user (created above)
- **password**: Database password (created above)
- **ddl-auto=validate**: Only validates existing schema; does NOT auto-create tables
  - Change to `update` if you want Hibernate to auto-create tables on startup
  - Recommended for development: `update`
  - Recommended for production: `validate` (requires pre-created schema)

## Troubleshooting

### Error: "password authentication failed for user codereboot_user"

The credentials in `application.properties` don't match the database. Verify:
1. User `codereboot_user` exists: `SELECT * FROM pg_user WHERE usename='codereboot_user';`
2. Password is correct: `psql -U codereboot_user -d codereboot`
3. Update password if needed:
   ```sql
   psql -U postgres
   ALTER USER codereboot_user WITH PASSWORD 'new_password';
   -- Then update application.properties
   ```

### Error: "database codereboot does not exist"

The database wasn't created. Run:
```sql
psql -U postgres
CREATE DATABASE codereboot OWNER codereboot_user;
\q
```

### Error: "relation users does not exist" (at login/register)

The users table wasn't created. Run the SQL schema commands in step 2 above.

## Changing Connection Settings

If your PostgreSQL is on a different host/port:

1. Edit `src/main/resources/application.properties`
2. Update `spring.datasource.url`:
   ```properties
   spring.datasource.url=jdbc:postgresql://your-host:your-port/codereboot
   ```
3. Recompile and restart: `./run.ps1 -SkipBuild=false`

## Advanced: Auto-Schema Creation for Development

For faster development iterations, change `ddl-auto` to `create-drop` (careful: drops schema on shutdown):

```properties
spring.jpa.hibernate.ddl-auto=create-drop
```

This automatically creates/updates the schema on startup. NOT recommended for production.

## Testing Authentication Endpoints

Once the server is running:

### Register a User

```bash
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

Expected response (201 Created):
```json
{
    "userId": 1,
    "username": "test_user",
    "email": "test@example.com",
    "message": "Registration successful"
}
```

### Login

```bash
$body = @{
    username = "test_user"
    password = "SecurePass123"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" `
    -Method POST `
    -Headers @{"Content-Type"="application/json"} `
    -Body $body
```

Expected response (200 OK):
```json
{
    "userId": 1,
    "username": "test_user",
    "email": "test@example.com",
    "message": "Login successful"
}
```

### Invalid Login

```bash
$body = @{
    username = "test_user"
    password = "WrongPassword"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" `
    -Method POST `
    -Headers @{"Content-Type"="application/json"} `
    -Body $body
```

Expected response (400 Bad Request):
```json
{
    "message": "Invalid username or password"
}
```

## Browser Testing

1. Navigate to `http://localhost:8080/login.html`
2. Click the "Register" tab
3. Fill in username (3-20 alphanumeric + underscore), email, and password (8+ chars)
4. Click "Create account"
5. On success, you're automatically logged in and redirected to room lobby
6. Create or join a room and start playing!

## Database Maintenance

### Backup User Data

```bash
# Backup the database
pg_dump -U codereboot_user -d codereboot > codereboot_backup.sql

# Restore from backup
psql -U codereboot_user -d codereboot < codereboot_backup.sql
```

### View All Registered Users

```sql
psql -U codereboot_user -d codereboot

SELECT id, username, email, created_at FROM users ORDER BY created_at DESC;
\q
```

### Delete a User

```sql
psql -U codereboot_user -d codereboot

DELETE FROM users WHERE username = 'username_to_delete';
\q
```

## Security Notes

1. **Never commit credentials**: `application.properties` should be .gitignored in production
2. **Use environment variables**: Override with `SPRING_DATASOURCE_PASSWORD` on production servers
3. **BCrypt Strength**: Passwords are hashed with BCrypt (strength 12), strong against GPU brute-force
4. **SQL Injection Prevention**: All queries use JPA parameterized queries automatically
5. **HTTPS**: Use HTTPS in production to protect credentials in transit

## Next Steps

- [x] PostgreSQL setup
- [x] Database schema initialized
- [ ] Deploy to Azure Container Apps or App Service
- [ ] Configure managed identity instead of hardcoded credentials
- [ ] Add JWT token authentication (currently stateless session-based)
- [ ] Implement account recovery and email verification
