# Database Operations Guide

This guide covers working with PostgreSQL in the CQRS Spike application.

## Overview

The application uses PostgreSQL 18 with three separate schemas following the CQRS pattern:

| Schema | Purpose | Access Pattern |
|--------|---------|----------------|
| `event_store` | Event sourcing events | Write-heavy, append-only |
| `read_model` | Query/read projections | Read-heavy, denormalized |
| `command_model` | Command/write operations | Write operations, normalized |

## Connecting to PostgreSQL

### Using Makefile

```bash
# Open interactive psql shell
make shell-postgres
```

### Using psql Directly

```bash
# From host machine
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db
# Password: local_dev_password

# From Docker
docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db
```

### Using Query Script

```bash
# Run a single query
./scripts/db-query.sh "SELECT COUNT(*) FROM event_store.domain_event"

# Multiple queries
./scripts/db-query.sh "SELECT 1; SELECT 2;"
```

### Connection Details

| Parameter | Value |
|-----------|-------|
| Host | `localhost` (from host) or `postgres` (from Docker) |
| Port | `5432` |
| Database | `cqrs_db` |
| Username | `cqrs_user` |
| Password | `local_dev_password` |

**JDBC URL:**
```
jdbc:postgresql://localhost:5432/cqrs_db
```

**R2DBC URL:**
```
r2dbc:postgresql://localhost:5432/cqrs_db
```

## Schema Exploration

### List All Schemas

```bash
./scripts/db-query.sh "\dn"
```

### List Tables in Schema

```bash
# Event store tables
./scripts/db-query.sh "\dt event_store.*"

# Read model tables
./scripts/db-query.sh "\dt read_model.*"

# Command model tables
./scripts/db-query.sh "\dt command_model.*"
```

### Describe Table Structure

```bash
# In psql shell
make shell-postgres

# Then describe table
\d event_store.domain_event
```

## Working with Event Store

### Event Stream Table

The `event_store.event_stream` table tracks aggregate streams:

```sql
-- View event streams
SELECT * FROM event_store.event_stream LIMIT 10;

-- Count streams by type
SELECT aggregate_type, COUNT(*)
FROM event_store.event_stream
GROUP BY aggregate_type;
```

### Domain Events Table

The `event_store.domain_event` table stores all events:

```sql
-- Recent events
SELECT * FROM event_store.domain_event
ORDER BY occurred_at DESC
LIMIT 10;

-- Events for specific aggregate
SELECT * FROM event_store.domain_event
WHERE stream_id = 'your-stream-id'
ORDER BY version;

-- Event type distribution
SELECT event_type, COUNT(*)
FROM event_store.domain_event
GROUP BY event_type;
```

### Querying Event Data (JSONB)

```sql
-- Query event data fields
SELECT
    event_id,
    event_type,
    event_data->>'fieldName' as field_value
FROM event_store.domain_event
WHERE event_data->>'fieldName' = 'value';

-- Search in nested JSON
SELECT * FROM event_store.domain_event
WHERE event_data @> '{"status": "active"}';
```

## Working with Read Model

### Query Projections

```sql
-- Example: product catalog view
SELECT * FROM read_model.products LIMIT 10;

-- Join with other projections
SELECT p.*, c.name as category_name
FROM read_model.products p
JOIN read_model.categories c ON p.category_id = c.id;
```

### Check Projection State

```sql
-- Verify projection is up to date
SELECT
    MAX(last_event_id) as latest_event,
    MAX(updated_at) as last_update
FROM read_model.projection_metadata;
```

## Database Migrations

### Using Flyway

Migrations are managed by Flyway and located in `src/main/resources/db/migration/`.

```bash
# Check migration status
./gradlew flywayInfo

# Run pending migrations
./gradlew flywayMigrate

# Validate migrations
./gradlew flywayValidate

# Clean database (WARNING: deletes all data)
./gradlew flywayClean
```

### Creating New Migrations

```bash
# Use migration script
./scripts/create-migration.sh "add_new_feature"
# Creates: V###__add_new_feature.sql
```

Migration naming convention:
```
V{version}__{description}.sql
```

Example: `V004__add_audit_columns.sql`

### Migration Best Practices

1. **Make migrations idempotent** where possible
2. **Use transactions** for data migrations
3. **Add rollback scripts** for complex changes
4. **Test migrations** before committing

## Data Seeding

### Seed Test Data

```bash
# Minimal data set
make db-seed

# Standard scenario
make db-seed-standard

# Full test data
make db-seed-full

# Performance testing data
make db-seed-perf
```

### Custom Scenarios

```bash
# Load specific scenario
make db-scenario
# Interactive menu to select scenario
```

### Seed Data Location

Seed scripts are in `infrastructure/postgres/seed-data/`:
```
seed-data/
├── scenarios/
│   ├── minimal.sql
│   ├── standard.sql
│   ├── full.sql
│   └── performance.sql
├── scripts/
│   ├── seed.sh
│   ├── reset.sh
│   └── reset-data-only.sh
└── README.md
```

## Resetting Data

### Reset Data Only (Keep Schema)

```bash
make db-reset-data
```

This:
- Truncates all tables
- Preserves schema structure
- Resets sequences

### Full Reset (Schema + Data)

```bash
make db-reset
```

This:
- Drops all schemas
- Recreates from migrations
- Requires application restart

### Reset via Flyway

```bash
# Nuclear option
./gradlew flywayClean flywayMigrate
```

## Backup and Restore

### Create Backup

```bash
# Full database backup
pg_dump -h localhost -U cqrs_user cqrs_db > backup.sql

# Specific schema
pg_dump -h localhost -U cqrs_user -n event_store cqrs_db > event_store_backup.sql

# Data only
pg_dump -h localhost -U cqrs_user --data-only cqrs_db > data_backup.sql
```

### Restore Backup

```bash
# Restore full backup
psql -h localhost -U cqrs_user -d cqrs_db < backup.sql

# Restore to fresh database
createdb -h localhost -U cqrs_user cqrs_db_restored
psql -h localhost -U cqrs_user -d cqrs_db_restored < backup.sql
```

## Performance

### Query Analysis

```bash
# Explain query plan
./scripts/db-query.sh "EXPLAIN ANALYZE SELECT * FROM event_store.domain_event LIMIT 100"
```

### Index Information

```sql
-- List indexes
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'event_store';

-- Index usage stats
SELECT
    relname as table_name,
    indexrelname as index_name,
    idx_scan as times_used,
    idx_tup_read as tuples_read
FROM pg_stat_user_indexes
WHERE schemaname = 'event_store';
```

### Table Statistics

```sql
-- Table sizes
SELECT
    schemaname,
    relname as table_name,
    pg_size_pretty(pg_total_relation_size(relid)) as total_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

-- Row counts
SELECT
    schemaname,
    relname as table_name,
    n_live_tup as row_count
FROM pg_stat_user_tables
WHERE schemaname IN ('event_store', 'read_model', 'command_model');
```

### Maintenance

```sql
-- Analyze tables for query optimization
ANALYZE event_store.domain_event;

-- Vacuum to reclaim space
VACUUM ANALYZE event_store.domain_event;
```

## Troubleshooting

### Connection Issues

```bash
# Check PostgreSQL is running
docker exec cqrs-postgres pg_isready -U cqrs_user

# Check logs
make logs-postgres

# Restart PostgreSQL
./scripts/reset-service.sh postgres
```

### Permission Errors

```sql
-- Check user permissions
\du cqrs_user

-- Grant permissions if needed
GRANT ALL PRIVILEGES ON SCHEMA event_store TO cqrs_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA event_store TO cqrs_user;
```

### Migration Failures

```bash
# Check migration status
./gradlew flywayInfo

# Repair checksums
./gradlew flywayRepair

# View failed migration
./scripts/db-query.sh "SELECT * FROM flyway_schema_history WHERE success = false"
```

### Lock Issues

```sql
-- Find blocking queries
SELECT pid, usename, query, state
FROM pg_stat_activity
WHERE state = 'active';

-- Kill blocking query (use with caution)
SELECT pg_terminate_backend(pid);
```

## See Also

- [Data Seeding](seeding-data.md) - Loading test data
- [Architecture Overview](../architecture/overview.md) - Database schema design
- [Database Troubleshooting](../troubleshooting/database-issues.md) - Problem solving
- [Commands Reference](../reference/commands.md) - All database commands
