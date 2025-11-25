# Quick Start Guide

Get the CQRS Spike application running in minutes.

## Prerequisites

Before starting, ensure you have:
- Docker Desktop running
- Java 21 installed
- Gradle 8+ (or use included wrapper)

See [Prerequisites](prerequisites.md) for detailed requirements.

## Quick Setup

### 1. Clone and Configure

```bash
# Clone repository
git clone <repository-url>
cd cqrs-spike

# Create environment file
make init-env
```

### 2. Start Infrastructure

```bash
# Start Vault and PostgreSQL
make start

# Wait for services (about 30-60 seconds)
make health
```

### 3. Build Application

```bash
make build
```

### 4. Run Application

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 5. Verify

```bash
# In another terminal
curl http://localhost:8080/actuator/health
```

Expected response: `{"status":"UP"}`

## Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Application | http://localhost:8080 | N/A |
| Vault UI | http://localhost:8200/ui | Token: `dev-root-token` |
| PostgreSQL | localhost:5432 | User: `cqrs_user`, Password: `local_dev_password` |

## Common Commands

### Infrastructure

```bash
make start      # Start infrastructure
make stop       # Stop all services
make restart    # Restart everything
make health     # Check service health
make ps         # Show service status
make logs       # View all logs
```

### Development

```bash
make build      # Build application
make test       # Run tests
./gradlew bootRun --args='--spring.profiles.active=local'  # Run app
```

### Database

```bash
make shell-postgres                     # Open psql shell
./scripts/db-query.sh "SELECT 1"        # Run a query
make db-seed                            # Load test data
```

### Vault

```bash
make shell-vault                                      # Open Vault shell
./scripts/vault-get.sh secret/cqrs-spike/database     # Get secrets
```

## Daily Development Workflow

### Starting Your Day

```bash
# Start infrastructure
make start

# Wait for services
make health

# Run application
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Making Changes

1. Edit code
2. Application auto-reloads (Spring DevTools)
3. Test changes:
   ```bash
   make test
   ```

### Viewing Logs

```bash
# All logs
make logs

# Application logs only
./scripts/logs.sh app

# Errors only
./scripts/logs.sh errors
```

### Ending Your Day

```bash
# Stop services (preserves data)
make stop

# Or clean everything for fresh start tomorrow
make clean
```

## Troubleshooting Quick Fixes

### Services Won't Start

```bash
make clean
make start
```

### Can't Connect to Database

```bash
./scripts/reset-service.sh postgres
```

### Vault Issues

```bash
./scripts/reset-service.sh vault
```

### Application Won't Start

```bash
# Check dependencies
make health

# View logs
make logs

# Clean rebuild
./gradlew clean build
```

## Next Steps

- [First-Time Setup](first-time-setup.md) - Detailed setup guide
- [Daily Workflow](../guides/daily-workflow.md) - Development patterns
- [Architecture Overview](../architecture/overview.md) - Understand the system
- [Troubleshooting](../troubleshooting/common-issues.md) - Solve problems
