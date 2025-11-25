# Daily Workflow Guide

This guide covers the typical development workflow patterns for working with the CQRS Spike application.

## Starting Your Day

### 1. Start Infrastructure

```bash
# Start Vault and PostgreSQL
make start

# Wait for services to be healthy (30-60 seconds)
make health
```

### 2. Verify Services

```bash
# Quick status check
make ps

# Detailed health check
make health
```

### 3. Start the Application

```bash
# Run with local profile
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 4. Open Development Environment

Recommended terminal layout:
- **Terminal 1:** Application running
- **Terminal 2:** Code editing / git operations
- **Terminal 3:** Logs and testing

Or use the log dashboard:
```bash
./scripts/logs-dashboard.sh  # Requires tmux
```

## Making Code Changes

### Typical Development Cycle

1. **Make code changes** in your IDE

2. **Application auto-reloads** (Spring DevTools)
   - Most changes reload automatically
   - For configuration changes, restart may be needed

3. **Test your changes:**
   ```bash
   # Run all tests
   make test

   # Run specific test
   ./gradlew test --tests "ProductServiceTest"
   ```

4. **Check code quality:**
   ```bash
   ./gradlew ktlintCheck
   ```

### Hot Reload vs Restart

**Auto-reloads (no restart needed):**
- Kotlin source changes
- Template changes
- Static resources

**Requires restart:**
- `application.yml` changes
- New dependencies
- Spring configuration classes

## Working with the Database

### Common Database Tasks

```bash
# Open interactive shell
make shell-postgres

# Run a quick query
./scripts/db-query.sh "SELECT COUNT(*) FROM event_store.domain_event"

# List schemas
./scripts/db-query.sh "\dn"

# List tables in a schema
./scripts/db-query.sh "\dt event_store.*"
```

### Loading Test Data

```bash
# Minimal test data
make db-seed

# Standard scenario
make db-seed-standard

# Full test data
make db-seed-full

# Performance test data
make db-seed-perf
```

### Resetting Data

```bash
# Reset data only (keep schema)
make db-reset-data

# Full reset (schema + data)
make db-reset
```

### Running Migrations

```bash
# Check migration status
./gradlew flywayInfo

# Run pending migrations
./gradlew flywayMigrate

# Clean and rebuild (WARNING: data loss!)
./gradlew flywayClean flywayMigrate
```

## Working with Vault

### Accessing Secrets

```bash
# Get database credentials
./scripts/vault-get.sh secret/cqrs-spike/database

# Get API keys
./scripts/vault-get.sh secret/cqrs-spike/api-keys

# List all secrets
./scripts/vault-get.sh list
```

### Using Vault UI

1. Open http://localhost:8200/ui
2. Login with token: `dev-root-token`
3. Navigate secrets at: Secrets > secret > cqrs-spike

### Vault Shell Access

```bash
make shell-vault

# In Vault shell:
vault kv get secret/cqrs-spike/database
vault kv list secret/cqrs-spike
```

## Viewing Logs

### Basic Log Viewing

```bash
# All service logs (follow mode)
make logs

# Specific services
make logs-vault
make logs-postgres
```

### Advanced Log Viewing

```bash
# Application logs
./scripts/logs.sh app

# Filter errors only
./scripts/logs.sh errors

# Filter warnings
./scripts/logs.sh warnings

# Last 100 lines
./scripts/logs.sh tail

# Multi-pane dashboard (requires tmux)
./scripts/logs-dashboard.sh
```

## Running Tests

### Unit Tests

```bash
# All tests
make test

# Specific test class
./gradlew test --tests "ProductServiceTest"

# With verbose output
./gradlew test --info

# With coverage
./gradlew test jacocoTestReport
```

### Integration Tests

```bash
# Ensure infrastructure is running
make start
make health

# Run integration tests
./gradlew integrationTest
```

## Debugging

### Local Debugging

```bash
# Start with debug port
./gradlew bootRun --debug-jvm

# Attach IDE debugger to localhost:5005
```

### IDE Configuration

**VS Code:** Use the "Attach to CQRS App" debug configuration

**IntelliJ:** Use the "Remote Debug" configuration

### Debugging Tips

- Set breakpoints in service classes
- Use conditional breakpoints for specific scenarios
- Check `application-local.yml` for debug settings

## Building

### Quick Build

```bash
# Skip tests for fast iteration
make build
```

### Full Build

```bash
# With tests
./gradlew clean build

# Check for issues
./gradlew check
```

### Clean Build

```bash
# When things aren't working
./gradlew clean
make build
```

## Git Workflow

### Before Committing

```bash
# Run tests
make test

# Check code quality
./gradlew ktlintCheck

# Format code (if needed)
./gradlew ktlintFormat
```

### Recommended Commit Flow

```bash
git status
git add -A
git commit -m "feat: description of change"
```

## Ending Your Day

### Option 1: Keep Data (Recommended)

```bash
# Stop services, preserve volumes
make stop
```

### Option 2: Clean Slate

```bash
# Stop and remove all data
make clean
```

## Performance Monitoring

### Resource Usage

```bash
./scripts/monitor-resources.sh
```

### Startup Time

```bash
./scripts/measure-startup.sh
```

### Performance Tests

```bash
./scripts/test-performance.sh
```

## Troubleshooting During Development

### Service Health Issues

```bash
make health
make logs
./scripts/reset-service.sh all
```

### Database Issues

```bash
./scripts/db-query.sh "SELECT 1"
./scripts/reset-service.sh postgres
```

### Vault Issues

```bash
curl http://localhost:8200/v1/sys/health
./scripts/reset-service.sh vault
```

### Application Issues

```bash
./scripts/logs.sh app
./scripts/logs.sh errors
./gradlew clean build
```

## Quick Reference Card

| Task | Command |
|------|---------|
| Start services | `make start` |
| Check health | `make health` |
| Run app | `./gradlew bootRun --args='--spring.profiles.active=local'` |
| Run tests | `make test` |
| View logs | `make logs` |
| DB shell | `make shell-postgres` |
| Load data | `make db-seed` |
| Reset data | `make db-reset-data` |
| Stop services | `make stop` |
| Clean all | `make clean` |

## See Also

- [Secrets Management](secrets-management.md) - Working with Vault
- [Database Operations](database-operations.md) - Database tasks
- [Debugging](debugging.md) - Debugging techniques
- [Troubleshooting](../troubleshooting/common-issues.md) - Problem solving
