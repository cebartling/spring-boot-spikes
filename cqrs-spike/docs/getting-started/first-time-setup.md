# First-Time Setup

This guide walks you through the initial setup of the CQRS Spike development environment.

## Overview

The setup process involves:
1. Cloning the repository
2. Configuring environment variables
3. Starting infrastructure services
4. Building the application
5. Verifying the setup

## Step 1: Clone the Repository

```bash
git clone <repository-url>
cd cqrs-spike
```

## Step 2: Configure Environment

### Create Environment File

The project uses environment variables for configuration. Create your local `.env` file:

```bash
# Using Makefile (recommended)
make init-env

# Or manually
cp .env.example .env
```

### Review Configuration

Open `.env` and review the default values:

```bash
# Vault Configuration
VAULT_ROOT_TOKEN=dev-root-token
VAULT_ADDR=http://localhost:8200

# PostgreSQL Configuration
POSTGRES_DB=cqrs_db
POSTGRES_USER=cqrs_user
POSTGRES_PASSWORD=local_dev_password

# Application Configuration
SPRING_PROFILES_ACTIVE=local
```

For local development, the defaults are typically sufficient.

### Customize (Optional)

If you need to change ports due to conflicts:

```bash
# Edit .env
vim .env

# Example changes:
APP_PORT=8081          # Change application port
POSTGRES_PORT=5433     # Change PostgreSQL port
VAULT_PORT=8201        # Change Vault port
```

## Step 3: Start Infrastructure

### Start All Services

```bash
make start
```

This command starts:
- **Vault** - Secrets management (port 8200)
- **Vault Init** - Initializes secrets in Vault
- **PostgreSQL** - Database (port 5432)

### Monitor Startup

Watch the startup progress:

```bash
# View logs during startup
make logs

# Or watch service status
watch -n 2 'make ps'
```

### Verify Services Are Healthy

```bash
make health
```

Expected output:
```
Checking service health...
[OK] Vault is healthy
[OK] PostgreSQL is healthy
All services are healthy!
```

## Step 4: Build the Application

### Build Without Tests

```bash
make build
```

This runs `./gradlew clean build -x test` to build the application quickly.

### Build With Tests

```bash
make test
```

This runs the full test suite to verify everything works.

### First Build Notes

The first build may take longer as Gradle downloads dependencies. Subsequent builds will be faster due to caching.

## Step 5: Run Database Migrations

Migrations run automatically when the application starts, but you can run them manually:

```bash
./gradlew flywayMigrate
```

Check migration status:
```bash
./gradlew flywayInfo
```

## Step 6: Start the Application

### Run with Local Profile

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Verify Application Started

Wait for the startup message, then verify:

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

## Step 7: Verify Complete Setup

### Run Verification Script

```bash
./scripts/test-documentation.sh
```

This verifies:
- All services are accessible
- Database connections work
- Vault secrets are configured
- Application endpoints respond

### Manual Verification

**1. Vault UI:**
- Open http://localhost:8200/ui
- Login with token: `dev-root-token`
- Navigate to Secrets > secret > cqrs-spike
- Verify secrets exist

**2. Database:**
```bash
make shell-postgres

# In psql shell:
\dn                          # List schemas
\dt event_store.*            # List event store tables
\dt read_model.*             # List read model tables
\dt command_model.*          # List command model tables
\q                           # Exit
```

**3. Application:**
```bash
# Health check
curl http://localhost:8080/actuator/health

# Info endpoint
curl http://localhost:8080/actuator/info
```

## Setup Complete

Your development environment is now ready. Here's what you have:

| Component | Status | URL/Port |
|-----------|--------|----------|
| Vault | Running | http://localhost:8200 |
| PostgreSQL | Running | localhost:5432 |
| Application | Running | http://localhost:8080 |

## Stopping Services

When you're done developing:

```bash
# Stop all services (preserves data)
make stop

# Stop and remove volumes (clean slate)
make clean
```

## Next Steps

- [Quick Start](quick-start.md) - Daily development workflow
- [Daily Workflow](../guides/daily-workflow.md) - Common development patterns
- [Architecture Overview](../architecture/overview.md) - Understand the system

## Troubleshooting First-Time Setup

### Services Won't Start

```bash
# Check Docker is running
docker info

# Check for port conflicts
lsof -i :8200
lsof -i :5432

# View detailed logs
make logs
```

### Build Fails

```bash
# Clean and rebuild
./gradlew clean build

# Check Java version
java -version  # Should be 21+

# Verify Gradle wrapper
./gradlew --version
```

### Application Won't Connect to Database

```bash
# Verify PostgreSQL is running
docker exec cqrs-postgres pg_isready -U cqrs_user

# Check Vault has database secrets
./scripts/vault-get.sh secret/cqrs-spike/database

# Restart services
make restart
```

### Vault Not Initializing

```bash
# Check Vault logs
make logs-vault

# Reinitialize Vault
./scripts/reset-service.sh vault
```

See [Common Issues](../troubleshooting/common-issues.md) for more troubleshooting help.
