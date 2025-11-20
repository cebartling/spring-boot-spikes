# Database Initialization Scripts

This directory contains SQL scripts that are executed automatically during database initialization by the `db-init` one-shot container in Docker Compose.

## How It Works

1. When you run `docker-compose up`, the `postgres` service starts first
2. Once PostgreSQL is healthy, the `db-init` container starts
3. The init container executes all `.sql` files in this directory in **alphabetical order**
4. After execution completes, the init container exits (it won't restart)

## Script Naming Convention

Use a numeric prefix to control execution order:
- `01-init-schema.sql` - Initial schema creation
- `02-seed-data.sql` - Seed data (if needed)
- `03-additional-tables.sql` - Additional tables
- etc.

## Current Schema

### Tables

**resilience_events**
- Tracks all resilience-related events (circuit breaker, rate limiter, retry, etc.)
- Includes metadata as JSONB for flexible event data
- Indexed on `event_type` and `created_at` for efficient querying

**circuit_breaker_state**
- Stores current state of circuit breakers
- Tracks failure/success counts
- Records timestamps of last failure/success
- Useful for monitoring and debugging circuit breaker behavior

**rate_limiter_metrics**
- Tracks rate limiter statistics per time window
- Records permitted and rejected calls
- Useful for analyzing rate limiting effectiveness

### Features

- UUID primary keys using `uuid-ossp` extension
- Automatic `updated_at` timestamp triggers
- Indexes for common query patterns
- JSONB support for flexible metadata storage

## Modifying the Schema

To modify the database schema:

1. **Edit existing scripts** in this directory
2. **Add new scripts** following the naming convention
3. **Restart with clean volumes:**
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

**Note:** The init container only runs once per database volume. To re-run initialization, you must delete the volume (`-v` flag) or manually drop/recreate the database.

## Viewing Initialization Logs

To view the database initialization logs:
```bash
docker-compose logs db-init
```

## Troubleshooting

**Scripts not executing:**
- Ensure scripts have `.sql` extension
- Check file permissions (should be readable)
- View init container logs: `docker-compose logs db-init`

**Schema not updating:**
- Remember to use `docker-compose down -v` to remove volumes
- The init container only runs on fresh database volumes

**Connection errors:**
- The init container waits for PostgreSQL healthcheck to pass
- Check postgres logs: `docker-compose logs postgres`
