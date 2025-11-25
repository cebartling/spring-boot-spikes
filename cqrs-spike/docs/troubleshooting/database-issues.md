# Database Troubleshooting

This guide covers PostgreSQL-specific issues and solutions.

## Quick Diagnostics

```bash
# Check PostgreSQL status
docker exec cqrs-postgres pg_isready -U cqrs_user

# View PostgreSQL logs
make logs-postgres

# Check container status
docker ps | grep postgres

# Test connection
./scripts/db-query.sh "SELECT 1"
```

## Common Issues

### PostgreSQL Not Starting

**Symptoms:**
- Container exits immediately
- Healthcheck failing
- Cannot connect on port 5432

**Check logs:**
```bash
docker logs cqrs-postgres
```

**Common causes and solutions:**

1. **Port 5432 in use:**
   ```bash
   lsof -i :5432
   kill -9 <PID>
   docker compose restart postgres
   ```

2. **Data directory corruption:**
   ```bash
   # Reset database (WARNING: data loss)
   docker compose down
   docker volume rm cqrs-postgres-data
   docker compose up -d postgres
   ```

3. **Insufficient disk space:**
   ```bash
   # Check disk space
   df -h

   # Clean Docker
   docker system prune -a
   ```

### Connection Refused

**Symptoms:**
```
connection refused
could not connect to server
```

**Check:**
```bash
# Is PostgreSQL running?
docker ps | grep postgres

# Is it healthy?
docker inspect cqrs-postgres --format '{{.State.Health.Status}}'

# Can you reach the port?
nc -zv localhost 5432
```

**Solutions:**

1. **Start PostgreSQL:**
   ```bash
   docker compose up -d postgres
   ```

2. **Wait for ready:**
   ```bash
   until docker exec cqrs-postgres pg_isready -U cqrs_user; do
     sleep 2
   done
   ```

3. **Restart if stuck:**
   ```bash
   docker compose restart postgres
   ```

### Authentication Failed

**Symptoms:**
```
password authentication failed for user "cqrs_user"
```

**Check credentials:**
```bash
# Check .env file
cat .env | grep POSTGRES

# Check Vault secrets
./scripts/vault-get.sh secret/cqrs-spike/database
```

**Solution:**
```bash
# Ensure credentials match
# .env should have:
POSTGRES_USER=cqrs_user
POSTGRES_PASSWORD=local_dev_password

# Restart to apply
docker compose restart postgres
```

### Database Does Not Exist

**Symptoms:**
```
database "cqrs_db" does not exist
```

**Check:**
```bash
# List databases
docker exec cqrs-postgres psql -U postgres -l
```

**Solution:**
```bash
# Create database
docker exec cqrs-postgres psql -U postgres -c "CREATE DATABASE cqrs_db OWNER cqrs_user"
```

### Schema Not Found

**Symptoms:**
```
schema "event_store" does not exist
```

**Check:**
```bash
./scripts/db-query.sh "\dn"
```

**Solution:**
```bash
# Run migrations
./gradlew flywayMigrate

# Or reset
./gradlew flywayClean flywayMigrate
```

### Migration Failures

**Check migration status:**
```bash
./gradlew flywayInfo
```

**View failed migrations:**
```bash
./scripts/db-query.sh "SELECT * FROM flyway_schema_history WHERE success = false"
```

**Solutions:**

1. **Repair checksum:**
   ```bash
   ./gradlew flywayRepair
   ```

2. **Remove failed record:**
   ```bash
   ./scripts/db-query.sh "DELETE FROM flyway_schema_history WHERE success = false"
   ```

3. **Full reset:**
   ```bash
   ./gradlew flywayClean flywayMigrate
   ```

### Slow Queries

**Identify slow queries:**
```bash
./scripts/db-query.sh "
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state != 'idle'
ORDER BY duration DESC
LIMIT 5
"
```

**Analyze query:**
```bash
./scripts/db-query.sh "EXPLAIN ANALYZE SELECT * FROM event_store.domain_event LIMIT 100"
```

**Solutions:**
1. Add indexes
2. Optimize queries
3. Run `VACUUM ANALYZE`

### Connection Pool Exhausted

**Symptoms:**
```
too many connections
connection pool exhausted
```

**Check connections:**
```bash
./scripts/db-query.sh "SELECT count(*) FROM pg_stat_activity"
```

**Solutions:**

1. **Increase max connections:**
   ```bash
   # In postgres config or docker-compose
   max_connections = 200
   ```

2. **Close idle connections:**
   ```bash
   ./scripts/db-query.sh "
   SELECT pg_terminate_backend(pid)
   FROM pg_stat_activity
   WHERE state = 'idle'
   AND query_start < now() - interval '10 minutes'
   "
   ```

### Lock Issues

**Check for locks:**
```bash
./scripts/db-query.sh "
SELECT
    blocked_locks.pid AS blocked_pid,
    blocked_activity.usename AS blocked_user,
    blocking_locks.pid AS blocking_pid,
    blocking_activity.usename AS blocking_user,
    blocked_activity.query AS blocked_statement
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.relation = blocked_locks.relation
    AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted
"
```

**Kill blocking query:**
```bash
./scripts/db-query.sh "SELECT pg_terminate_backend(<blocking_pid>)"
```

### Disk Space Issues

**Check table sizes:**
```bash
./scripts/db-query.sh "
SELECT
    schemaname,
    relname as table_name,
    pg_size_pretty(pg_total_relation_size(relid)) as total_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 10
"
```

**Reclaim space:**
```bash
./scripts/db-query.sh "VACUUM FULL event_store.domain_event"
```

### Data Corruption

**Symptoms:**
- Integrity constraint violations
- Unexpected query results
- Index corruption errors

**Check for corruption:**
```bash
# Check table
./scripts/db-query.sh "SELECT * FROM event_store.domain_event LIMIT 1"

# Reindex
./scripts/db-query.sh "REINDEX TABLE event_store.domain_event"
```

**Solutions:**

1. **Rebuild indexes:**
   ```bash
   ./scripts/db-query.sh "REINDEX DATABASE cqrs_db"
   ```

2. **Full reset if severe:**
   ```bash
   make db-reset
   make db-seed
   ```

## Application Connection Issues

### R2DBC Connection Errors

**Symptoms:**
```
ConnectionFactory creation failed
R2DBC connection refused
```

**Check R2DBC configuration:**
```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cqrs_db
    username: cqrs_user
    password: ${database.password}
```

**Verify:**
```bash
# Test from host
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db -c "SELECT 1"
```

### Connection Timeout

**Symptoms:**
```
Connection timed out after 30000ms
```

**Solutions:**

1. **Check network:**
   ```bash
   docker network inspect cqrs-network
   ```

2. **Increase timeout:**
   ```yaml
   spring:
     r2dbc:
       pool:
         max-acquire-time: 60s
   ```

## Reset Procedures

### Soft Reset (Reset Data Only)

```bash
make db-reset-data
```

### Full Reset (Schema + Data)

```bash
make db-reset
```

### Complete Rebuild

```bash
# Stop everything
docker compose down

# Remove volume
docker volume rm cqrs-postgres-data

# Start fresh
docker compose up -d postgres

# Run migrations
./gradlew flywayMigrate

# Seed data
make db-seed
```

### Reset Using Script

```bash
./scripts/reset-service.sh postgres
```

## PostgreSQL CLI Reference

```bash
# Connect
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db

# Or via Docker
docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db

# Common commands in psql:
\l          # List databases
\c cqrs_db  # Connect to database
\dn         # List schemas
\dt         # List tables
\d+ table   # Describe table
\q          # Quit
```

## Monitoring

### Connection Statistics

```bash
./scripts/db-query.sh "
SELECT
    state,
    count(*)
FROM pg_stat_activity
WHERE datname = 'cqrs_db'
GROUP BY state
"
```

### Table Statistics

```bash
./scripts/db-query.sh "
SELECT
    schemaname,
    relname,
    n_live_tup as row_count,
    n_dead_tup as dead_rows
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC
"
```

### Query Performance

```bash
./scripts/db-query.sh "
SELECT
    query,
    calls,
    total_time / 1000 as total_secs,
    mean_time / 1000 as mean_secs
FROM pg_stat_statements
ORDER BY total_time DESC
LIMIT 10
"
```

## See Also

- [Database Operations Guide](../guides/database-operations.md)
- [Data Seeding Guide](../guides/seeding-data.md)
- [Architecture Overview](../architecture/overview.md)
- [Common Issues](common-issues.md)
