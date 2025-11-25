# Common Issues and Solutions

This guide covers frequently encountered problems and their solutions.

## Table of Contents

- [Infrastructure Won't Start](#infrastructure-wont-start)
- [Services Unhealthy](#services-unhealthy)
- [Connection Issues](#connection-issues)
- [Application Issues](#application-issues)
- [Performance Problems](#performance-problems)
- [Data Issues](#data-issues)

## Infrastructure Won't Start

### Docker Daemon Not Running

**Symptoms:**
```
Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

**Solution:**
1. Start Docker Desktop
2. Wait for Docker to fully start (whale icon should be steady)
3. Verify: `docker info`

### Port Already in Use

**Symptoms:**
```
Bind for 0.0.0.0:8080 failed: port is already allocated
```

**Solution:**

Find and stop the process using the port:

```bash
# macOS/Linux
lsof -i :8080
kill -9 <PID>

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

Or change the port in `.env`:
```bash
APP_PORT=8081
```

### Insufficient Docker Resources

**Symptoms:**
- Containers crashing with OOM
- Very slow startup
- Container exits with code 137

**Solution:**

Increase Docker resources:
1. Open Docker Desktop > Settings
2. Resources > increase memory to 4GB+
3. Apply & Restart

### Volume Permission Issues

**Symptoms:**
```
Permission denied when trying to create/write files
```

**Solution:**

```bash
# Fix volume permissions
chmod -R 755 infrastructure/
docker compose down -v
docker compose up -d
```

### Old Container State

**Symptoms:**
- Unexpected behavior
- Old configuration being used

**Solution:**

Clean restart:
```bash
make clean
make start
```

## Services Unhealthy

### Vault Unhealthy

**Check:**
```bash
docker logs cqrs-vault
curl http://localhost:8200/v1/sys/health
```

**Common Causes:**
- Port 8200 in use
- Insufficient memory
- Volume mount issues

**Solution:**
```bash
# Restart Vault
docker compose restart vault

# Wait 30 seconds, then check
make health

# If still failing, reset
./scripts/reset-service.sh vault
```

### PostgreSQL Unhealthy

**Check:**
```bash
docker logs cqrs-postgres
docker exec cqrs-postgres pg_isready -U cqrs_user
```

**Common Causes:**
- Port 5432 in use
- Data corruption
- Insufficient disk space

**Solution:**
```bash
# Restart
docker compose restart postgres

# If corrupted, reset
make db-reset
```

### Application Unhealthy

**Check:**
```bash
docker logs cqrs-app
curl http://localhost:8080/actuator/health
```

**Common Causes:**
- Cannot connect to Vault
- Cannot connect to PostgreSQL
- Migration failures
- Out of memory

**Solution:**
```bash
# Check dependencies first
make health

# View detailed logs
./scripts/logs.sh app

# Restart
docker compose restart app
```

## Connection Issues

### Cannot Connect to Database

**Symptoms:**
- Application fails to start
- Connection timeout errors
- `Connection refused`

**Verification:**
```bash
# From host
psql -h localhost -U cqrs_user -d cqrs_db

# Check PostgreSQL is running
docker exec cqrs-postgres pg_isready -U cqrs_user
```

**Solutions:**

1. **Check PostgreSQL is running:**
   ```bash
   docker ps | grep postgres
   ```

2. **Verify credentials in Vault:**
   ```bash
   ./scripts/vault-get.sh secret/cqrs-spike/database
   ```

3. **Check network:**
   ```bash
   docker network inspect cqrs-network
   ```

4. **Restart services:**
   ```bash
   docker compose restart postgres app
   ```

### Cannot Connect to Vault

**Symptoms:**
- Application fails to start with Vault errors
- 403 Forbidden errors

**Verification:**
```bash
curl http://localhost:8200/v1/sys/health
```

**Solutions:**

1. **Check Vault is running:**
   ```bash
   docker ps | grep vault
   ```

2. **Verify token:**
   ```bash
   grep VAULT_ROOT_TOKEN .env
   # Should be: dev-root-token
   ```

3. **Reinitialize Vault:**
   ```bash
   ./scripts/reset-service.sh vault
   ```

### Network Connectivity Issues

**Symptoms:**
- Containers can't communicate
- DNS resolution failures

**Solutions:**
```bash
# Recreate network
docker network rm cqrs-network
docker compose up -d

# Verify network
docker network inspect cqrs-network
```

## Application Issues

### Application Won't Start

**Check logs:**
```bash
./gradlew bootRun 2>&1 | head -100
```

**Common Causes:**

1. **Java version wrong:**
   ```bash
   java -version  # Should be 21+
   ```

2. **Dependencies not running:**
   ```bash
   make health
   ```

3. **Build issues:**
   ```bash
   ./gradlew clean build
   ```

### Migration Failures

**Check:**
```bash
./gradlew flywayInfo
```

**Solutions:**

1. **Failed migration:**
   ```bash
   ./scripts/db-query.sh "DELETE FROM flyway_schema_history WHERE success = false"
   ```

2. **Checksum mismatch:**
   ```bash
   ./gradlew flywayRepair
   ```

3. **Start fresh:**
   ```bash
   ./gradlew flywayClean flywayMigrate
   ```

### Dependency Injection Failures

**Symptoms:**
```
NoSuchBeanDefinitionException
UnsatisfiedDependencyException
```

**Solutions:**
```bash
# Clean and rebuild
./gradlew clean build

# Check for circular dependencies in logs
./scripts/logs.sh app | grep -i "circular"
```

## Performance Problems

### Slow Startup

**Expected:** < 60 seconds infrastructure, < 30 seconds application

**Solutions:**

1. **Check Docker resources:**
   - Increase CPU allocation
   - Increase memory allocation

2. **Check disk performance:**
   ```bash
   docker system prune  # Clean up old images
   ```

3. **Measure timing:**
   ```bash
   ./scripts/measure-startup.sh
   ```

### High Memory Usage

**Check:**
```bash
./scripts/monitor-resources.sh
```

**Solutions:**

1. **Reduce JVM heap:**
   ```bash
   # In .env
   JAVA_OPTS="-Xms256m -Xmx512m"
   ```

2. **Check for memory leaks:**
   ```bash
   ./scripts/logs.sh app | grep -i "memory\|heap\|gc"
   ```

### Slow Database Queries

**Check:**
```bash
./scripts/db-query.sh "EXPLAIN ANALYZE SELECT * FROM event_store.domain_event LIMIT 100"
```

**Solutions:**
- Add indexes for slow queries
- Analyze query plans
- Run vacuum: `VACUUM ANALYZE;`

## Data Issues

### Missing Data

**Check:**
```bash
./scripts/db-query.sh "SELECT COUNT(*) FROM event_store.domain_event"
```

**Solution:**
```bash
# Reseed data
make db-seed
```

### Data Corruption

**Symptoms:**
- Constraint violations
- Foreign key errors

**Solution:**
```bash
# Reset and reseed
make db-reset
make db-seed
```

### Missing Schemas

**Check:**
```bash
./scripts/db-query.sh "\dn"
```

**Solution:**
```bash
./gradlew flywayMigrate
```

## Quick Fixes Summary

| Problem | Quick Fix |
|---------|-----------|
| Docker not running | Start Docker Desktop |
| Port in use | `lsof -i :PORT` then `kill -9 PID` |
| Services unhealthy | `make restart` |
| Can't connect to DB | `./scripts/reset-service.sh postgres` |
| Can't connect to Vault | `./scripts/reset-service.sh vault` |
| App won't start | `make health` then check logs |
| Migration failed | `./gradlew flywayRepair` |
| Need fresh start | `make clean && make start` |

## Collecting Diagnostics

For complex issues, collect diagnostics:

```bash
# Service status
make ps > diagnostics.txt

# All logs
make logs > logs.txt 2>&1

# Resource usage
./scripts/monitor-resources.sh >> diagnostics.txt

# Network info
docker network inspect cqrs-network >> diagnostics.txt
```

## Getting More Help

1. Check service-specific troubleshooting:
   - [Vault Issues](vault-issues.md)
   - [Database Issues](database-issues.md)
   - [Docker Issues](docker-issues.md)

2. Review architecture documentation:
   - [Architecture Overview](../architecture/overview.md)
   - [Infrastructure Components](../architecture/infrastructure-components.md)

3. Search application logs:
   ```bash
   ./scripts/logs.sh errors
   ```
