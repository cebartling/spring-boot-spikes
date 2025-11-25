# Setup Verification

This guide helps you verify that your development environment is correctly configured.

## Automated Verification

### Run Verification Script

```bash
./scripts/test-documentation.sh
```

This script checks:
- Docker availability
- Service health (Vault, PostgreSQL)
- Database connectivity
- Vault secret access
- Application endpoints

### Expected Output

```
=== CQRS Spike - Documentation Verification ===

Checking Docker...
[OK] Docker is available

Checking services...
[OK] Vault is healthy (http://localhost:8200)
[OK] PostgreSQL is healthy (localhost:5432)

Checking database...
[OK] Can connect to database
[OK] Schemas exist (event_store, read_model, command_model)

Checking Vault...
[OK] Can read database secrets
[OK] Can read API key secrets

Checking application (if running)...
[OK] Health endpoint responds
[OK] Actuator info available

=== All checks passed! ===
```

## Manual Verification Steps

### 1. Verify Docker

```bash
# Check Docker daemon
docker info

# Expected: Shows Docker version and system info
```

```bash
# Check Docker Compose
docker compose version

# Expected: Docker Compose version v2.x.x
```

### 2. Verify Infrastructure Services

```bash
# Check service status
make ps
```

Expected output:
```
NAME             STATUS          PORTS
cqrs-postgres    Up (healthy)    0.0.0.0:5432->5432/tcp
cqrs-vault       Up (healthy)    0.0.0.0:8200->8200/tcp
```

```bash
# Run health check
make health
```

Expected output:
```
[OK] Vault is healthy
[OK] PostgreSQL is healthy
All services are healthy!
```

### 3. Verify Vault

**Check Vault API:**
```bash
curl http://localhost:8200/v1/sys/health
```

Expected response:
```json
{
  "initialized": true,
  "sealed": false,
  "standby": false,
  ...
}
```

**Check Vault Secrets:**
```bash
./scripts/vault-get.sh secret/cqrs-spike/database
```

Expected output:
```
Key                 Value
---                 -----
password            local_dev_password
url                 jdbc:postgresql://postgres:5432/cqrs_db
username            cqrs_user
```

**Access Vault UI:**
1. Open http://localhost:8200/ui
2. Login with token: `dev-root-token`
3. Navigate to Secrets > secret > cqrs-spike
4. Verify database and other secrets exist

### 4. Verify PostgreSQL

**Check PostgreSQL readiness:**
```bash
docker exec cqrs-postgres pg_isready -U cqrs_user
```

Expected output:
```
localhost:5432 - accepting connections
```

**Test database connection:**
```bash
./scripts/db-query.sh "SELECT 1 AS test"
```

Expected output:
```
 test
------
    1
(1 row)
```

**Verify schemas exist:**
```bash
./scripts/db-query.sh "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('event_store', 'read_model', 'command_model')"
```

Expected output:
```
  schema_name
---------------
 event_store
 read_model
 command_model
(3 rows)
```

**Check event store tables:**
```bash
make shell-postgres
```

Then in psql:
```sql
\dt event_store.*
```

Expected tables:
- `event_store.domain_event`
- `event_store.event_stream`

Exit with `\q`.

### 5. Verify Application

**Start the application:**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Wait for startup message:
```
Started CqrsApplicationKt in X.XXX seconds
```

**Check health endpoint:**
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

**Check detailed health:**
```bash
curl http://localhost:8080/actuator/health | jq
```

Expected components:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" },
    "vault": { "status": "UP" }
  }
}
```

**Check info endpoint:**
```bash
curl http://localhost:8080/actuator/info
```

### 6. Verify Build System

**Check Gradle wrapper:**
```bash
./gradlew --version
```

Expected output includes:
```
Gradle 8.x.x
Build time:   YYYY-MM-DD
Kotlin:       2.x.x
```

**Run build:**
```bash
./gradlew clean build -x test
```

Expected: `BUILD SUCCESSFUL`

**Run tests:**
```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with test results

### 7. Verify Flyway Migrations

**Check migration status:**
```bash
./gradlew flywayInfo
```

Expected output shows applied migrations:
```
+-----------+---------+---------------------+------+---------------------+---------+
| Category  | Version | Description         | Type | Installed On        | State   |
+-----------+---------+---------------------+------+---------------------+---------+
| Versioned | 001     | create event store  | SQL  | 2024-01-01 10:00:00 | Success |
| Versioned | 002     | create read model   | SQL  | 2024-01-01 10:00:01 | Success |
| Versioned | 003     | create command model| SQL  | 2024-01-01 10:00:02 | Success |
+-----------+---------+---------------------+------+---------------------+---------+
```

## Verification Checklist

Use this checklist to verify your setup:

- [ ] Docker daemon is running (`docker info`)
- [ ] Docker Compose is available (`docker compose version`)
- [ ] Vault container is healthy (`make health`)
- [ ] PostgreSQL container is healthy (`make health`)
- [ ] Can access Vault UI (http://localhost:8200/ui)
- [ ] Vault secrets are initialized (`./scripts/vault-get.sh secret/cqrs-spike/database`)
- [ ] Can connect to PostgreSQL (`./scripts/db-query.sh "SELECT 1"`)
- [ ] Database schemas exist (event_store, read_model, command_model)
- [ ] Application builds successfully (`make build`)
- [ ] Application starts without errors (`./gradlew bootRun`)
- [ ] Health endpoint returns UP (http://localhost:8080/actuator/health)
- [ ] Tests pass (`make test`)

## Common Verification Issues

### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>
```

### Vault Not Initialized

```bash
# Check Vault logs
make logs-vault

# Reinitialize
./scripts/reset-service.sh vault
```

### Database Connection Failed

```bash
# Check PostgreSQL logs
make logs-postgres

# Verify credentials in .env
cat .env | grep POSTGRES

# Reset database
./scripts/reset-service.sh postgres
```

### Missing Schemas

```bash
# Run migrations
./gradlew flywayMigrate

# Or reset and migrate
./gradlew flywayClean flywayMigrate
```

### Build Failures

```bash
# Clean build
./gradlew clean build

# Check Java version
java -version

# Verify it's 21+
```

## Next Steps

If all verifications pass, your environment is ready:

- [Quick Start](quick-start.md) - Daily workflow
- [Daily Workflow](../guides/daily-workflow.md) - Development patterns
- [Architecture](../architecture/overview.md) - Understand the system
