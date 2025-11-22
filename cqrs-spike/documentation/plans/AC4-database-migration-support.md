# Implementation Plan: AC4 - Database Migration Support

**Feature:** [Local Development Services Infrastructure](../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC4 - Database Migration Support

## Overview

Implement database schema versioning and migration management using Flyway to enable controlled, repeatable database changes with full history tracking and rollback capabilities.

## Prerequisites

- AC3 (Relational Database) completed
- PostgreSQL running and accessible
- Spring Boot application configured

## Technology Selection

### Recommended: Flyway

**Rationale:**
- De facto standard for Java/Spring Boot database migrations
- Simple, SQL-based migration approach (also supports Java-based migrations)
- Excellent Spring Boot integration with auto-configuration
- Robust version tracking and validation
- Support for repeatable migrations
- Clear migration history and audit trail
- Handles out-of-order migrations gracefully
- Supports callbacks for custom logic

**Alternatives Considered:**
- Liquibase: More feature-rich but more complex, XML/YAML-based (less intuitive)
- JPA ddl-auto: Not suitable for production-like environments, no version control
- Custom scripts: Reinventing the wheel, error-prone

## Migration Strategy

### Versioned vs Repeatable Migrations

**Versioned Migrations (V prefix):**
- One-time execution
- Sequential version numbers
- Schema changes, table creation, data migrations
- Cannot be modified after execution

**Repeatable Migrations (R prefix):**
- Re-run when checksum changes
- Views, stored procedures, functions
- Idempotent by nature

### Naming Convention

```
V{version}__{description}.sql
R__{description}.sql

Examples:
V1__create_event_store_schema.sql
V2__create_domain_event_table.sql
V3__add_correlation_tracking.sql
R__event_stream_view.sql
```

## Technical Implementation

### 1. Maven Dependencies

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Flyway Core -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <!-- Flyway PostgreSQL support -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
</dependencies>
```

### 2. Spring Boot Configuration

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
    schemas: event_store,read_model,command_model
    default-schema: command_model
    create-schemas: true
    validate-on-migrate: true
    clean-disabled: true
    out-of-order: false
    placeholder-replacement: true
    placeholders:
      application_user: cqrs_user
    table: flyway_schema_history
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
    repeatable-sql-migration-prefix: R
    clean-on-validation-error: false

logging:
  level:
    org.flywaydb: INFO
```

**Configuration Explained:**
- `baseline-on-migrate`: Initialize Flyway on existing database
- `baseline-version`: Starting version for existing databases
- `locations`: Where to find migration scripts
- `schemas`: All schemas to manage
- `default-schema`: Schema for Flyway metadata table
- `create-schemas`: Auto-create schemas if missing
- `validate-on-migrate`: Verify applied migrations haven't changed
- `clean-disabled`: Prevent accidental data loss (always true for safety)
- `out-of-order`: Allow fixing version gaps (false for strict ordering)
- `placeholder-replacement`: Enable variable substitution

### 3. Directory Structure

```
src/main/resources/
└── db/
    └── migration/
        ├── V1__create_base_schemas.sql
        ├── V2__create_event_store.sql
        ├── V3__create_read_model_tables.sql
        ├── V4__create_command_model_tables.sql
        ├── V5__add_event_indexes.sql
        ├── R__event_stream_views.sql
        └── R__materialized_views.sql
```

### 4. Initial Migration Scripts

**V1: Base Schema Creation**
```sql
-- V1__create_base_schemas.sql

-- Create schemas if they don't exist
CREATE SCHEMA IF NOT EXISTS event_store;
CREATE SCHEMA IF NOT EXISTS read_model;
CREATE SCHEMA IF NOT EXISTS command_model;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Grant permissions
GRANT USAGE ON SCHEMA event_store TO ${application_user};
GRANT USAGE ON SCHEMA read_model TO ${application_user};
GRANT USAGE ON SCHEMA command_model TO ${application_user};

GRANT CREATE ON SCHEMA event_store TO ${application_user};
GRANT CREATE ON SCHEMA read_model TO ${application_user};
GRANT CREATE ON SCHEMA command_model TO ${application_user};

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA event_store TO ${application_user};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA read_model TO ${application_user};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA command_model TO ${application_user};

GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA event_store TO ${application_user};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA read_model TO ${application_user};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA command_model TO ${application_user};

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA event_store GRANT ALL ON TABLES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA read_model GRANT ALL ON TABLES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA command_model GRANT ALL ON TABLES TO ${application_user};

ALTER DEFAULT PRIVILEGES IN SCHEMA event_store GRANT ALL ON SEQUENCES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA read_model GRANT ALL ON SEQUENCES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA command_model GRANT ALL ON SEQUENCES TO ${application_user};
```

**V2: Event Store Tables**
```sql
-- V2__create_event_store.sql

SET search_path TO event_store;

-- Event Stream table
CREATE TABLE event_stream (
    stream_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE (aggregate_type, aggregate_id)
);

CREATE INDEX idx_event_stream_aggregate ON event_stream(aggregate_type, aggregate_id);
CREATE INDEX idx_event_stream_updated ON event_stream(updated_at DESC);

COMMENT ON TABLE event_stream IS 'Aggregate event streams for event sourcing';
COMMENT ON COLUMN event_stream.version IS 'Current version for optimistic locking';

-- Domain Events table
CREATE TABLE domain_event (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID NOT NULL REFERENCES event_stream(stream_id) ON DELETE CASCADE,
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    aggregate_version INTEGER NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    causation_id UUID,
    correlation_id UUID,
    user_id VARCHAR(255)
);

CREATE INDEX idx_domain_event_stream ON domain_event(stream_id, aggregate_version);
CREATE INDEX idx_domain_event_type ON domain_event(event_type);
CREATE INDEX idx_domain_event_occurred ON domain_event(occurred_at DESC);
CREATE INDEX idx_domain_event_correlation ON domain_event(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_domain_event_causation ON domain_event(causation_id) WHERE causation_id IS NOT NULL;
CREATE INDEX idx_domain_event_data ON domain_event USING GIN(event_data);
CREATE INDEX idx_domain_event_metadata ON domain_event USING GIN(metadata);

COMMENT ON TABLE domain_event IS 'Immutable domain events';
COMMENT ON COLUMN domain_event.aggregate_version IS 'Version of aggregate after applying this event';
COMMENT ON COLUMN domain_event.causation_id IS 'ID of command that caused this event';
COMMENT ON COLUMN domain_event.correlation_id IS 'Correlation ID for tracking related events across aggregates';

-- Trigger to update event_stream.updated_at
CREATE OR REPLACE FUNCTION update_stream_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE event_stream
    SET updated_at = NEW.occurred_at
    WHERE stream_id = NEW.stream_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_stream_timestamp
AFTER INSERT ON domain_event
FOR EACH ROW
EXECUTE FUNCTION update_stream_timestamp();
```

**V3: Event Append Function**
```sql
-- V3__create_event_append_function.sql

SET search_path TO event_store;

CREATE OR REPLACE FUNCTION append_events(
    p_aggregate_type VARCHAR,
    p_aggregate_id UUID,
    p_expected_version INTEGER,
    p_events JSONB[]
) RETURNS UUID AS $$
DECLARE
    v_stream_id UUID;
    v_current_version INTEGER;
    v_event JSONB;
    v_new_version INTEGER;
BEGIN
    -- Get or create stream with row-level lock
    SELECT stream_id, version INTO v_stream_id, v_current_version
    FROM event_stream
    WHERE aggregate_type = p_aggregate_type AND aggregate_id = p_aggregate_id
    FOR UPDATE;

    IF v_stream_id IS NULL THEN
        INSERT INTO event_stream (aggregate_type, aggregate_id, version)
        VALUES (p_aggregate_type, p_aggregate_id, 0)
        RETURNING stream_id, version INTO v_stream_id, v_current_version;
    END IF;

    -- Optimistic concurrency check
    IF v_current_version != p_expected_version THEN
        RAISE EXCEPTION 'Concurrency conflict: expected version %, actual version %',
            p_expected_version, v_current_version
            USING ERRCODE = 'serialization_failure';
    END IF;

    -- Append events
    v_new_version := v_current_version;
    FOREACH v_event IN ARRAY p_events LOOP
        v_new_version := v_new_version + 1;

        INSERT INTO domain_event (
            stream_id,
            event_type,
            event_version,
            aggregate_version,
            event_data,
            metadata,
            causation_id,
            correlation_id,
            user_id
        ) VALUES (
            v_stream_id,
            v_event->>'event_type',
            COALESCE((v_event->>'event_version')::INTEGER, 1),
            v_new_version,
            v_event->'event_data',
            v_event->'metadata',
            (v_event->>'causation_id')::UUID,
            (v_event->>'correlation_id')::UUID,
            v_event->>'user_id'
        );
    END LOOP;

    -- Update stream version
    UPDATE event_stream
    SET version = v_new_version
    WHERE stream_id = v_stream_id;

    RETURN v_stream_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION append_events IS 'Atomically append events to aggregate stream with optimistic locking';
```

**R: Materialized Views**
```sql
-- R__event_stream_views.sql

SET search_path TO event_store;

-- Drop existing views
DROP VIEW IF EXISTS v_recent_events CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_event_statistics CASCADE;

-- Recent events view
CREATE OR REPLACE VIEW v_recent_events AS
SELECT
    de.event_id,
    de.event_type,
    es.aggregate_type,
    es.aggregate_id,
    de.occurred_at,
    de.correlation_id,
    de.user_id
FROM domain_event de
JOIN event_stream es ON de.stream_id = es.stream_id
ORDER BY de.occurred_at DESC
LIMIT 1000;

COMMENT ON VIEW v_recent_events IS 'Most recent 1000 events across all aggregates';

-- Event statistics materialized view
CREATE MATERIALIZED VIEW mv_event_statistics AS
SELECT
    es.aggregate_type,
    COUNT(DISTINCT es.aggregate_id) as aggregate_count,
    COUNT(de.event_id) as event_count,
    MAX(de.occurred_at) as last_event_time,
    AVG(es.version) as avg_version
FROM event_stream es
LEFT JOIN domain_event de ON es.stream_id = de.stream_id
GROUP BY es.aggregate_type;

CREATE UNIQUE INDEX idx_mv_event_stats_type ON mv_event_statistics(aggregate_type);

COMMENT ON MATERIALIZED VIEW mv_event_statistics IS 'Aggregate statistics for monitoring and reporting';
```

### 5. Flyway Java Configuration

```java
package com.example.cqrs.infrastructure.database.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
public class FlywayMigrationLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final Flyway flyway;

    public FlywayMigrationLogger(DataSource dataSource) {
        this.flyway = Flyway.configure()
            .dataSource(dataSource)
            .load();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=" .repeat(80));
        log.info("DATABASE MIGRATION STATUS");
        log.info("=" .repeat(80));

        var info = flyway.info();
        var current = info.current();

        if (current != null) {
            log.info("Current schema version: {}", current.getVersion());
            log.info("Current description: {}", current.getDescription());
            log.info("Applied on: {}", current.getInstalledOn());
        } else {
            log.warn("No migrations have been applied yet");
        }

        log.info("Total migrations: {}", info.all().length);
        log.info("Pending migrations: {}", info.pending().length);
        log.info("=" .repeat(80));
    }
}
```

### 6. Migration History Tracking

**Custom callback for migration events:**
```java
package com.example.cqrs.infrastructure.database.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FlywayCallback implements Callback {

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE ||
               event == Event.AFTER_MIGRATE_ERROR ||
               event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        switch (event) {
            case BEFORE_MIGRATE:
                log.info("Starting database migration...");
                break;
            case AFTER_MIGRATE:
                log.info("Database migration completed successfully");
                log.info("Migrations applied: {}",
                    context.getMigrationInfo() != null ?
                    context.getMigrationInfo().getVersion() : "unknown");
                break;
            case AFTER_MIGRATE_ERROR:
                log.error("Database migration failed!");
                break;
        }
    }
}
```

### 7. Developer Workflow

**Creating New Migrations:**

```bash
# Script: scripts/create-migration.sh
#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: ./scripts/create-migration.sh <description>"
  echo "Example: ./scripts/create-migration.sh add_user_table"
  exit 1
fi

DESCRIPTION=$1
TIMESTAMP=$(date +%s)
VERSION=$(ls -1 src/main/resources/db/migration/V*.sql 2>/dev/null | wc -l | xargs)
NEXT_VERSION=$((VERSION + 1))

FILENAME="V${NEXT_VERSION}__${DESCRIPTION}.sql"
FILEPATH="src/main/resources/db/migration/${FILENAME}"

cat > "$FILEPATH" <<EOF
-- Migration: ${DESCRIPTION}
-- Version: ${NEXT_VERSION}
-- Created: $(date)

SET search_path TO command_model;

-- TODO: Add your migration SQL here

EOF

echo "Created migration: ${FILEPATH}"
echo "Edit the file and add your migration SQL"
```

**Make script executable:**
```bash
chmod +x scripts/create-migration.sh
```

### 8. Rollback Support

**Undo Migrations (Flyway Teams only, alternative approach for Community):**

```sql
-- V6__add_user_preferences.sql
CREATE TABLE command_model.user_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    preferences JSONB NOT NULL
);

-- For rollback, create a compensating migration:
-- V7__rollback_user_preferences.sql (if needed)
DROP TABLE IF EXISTS command_model.user_preferences;
```

**Best Practice: Reversible Migrations**
```sql
-- V8__add_email_column.sql
ALTER TABLE command_model.users ADD COLUMN email VARCHAR(255);

-- If rollback needed, create:
-- V9__remove_email_column.sql
ALTER TABLE command_model.users DROP COLUMN IF EXISTS email;
```

## Testing Strategy

### 1. Migration Execution Tests

```java
@SpringBootTest
@ActiveProfiles("test")
public class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void shouldApplyAllMigrations() {
        var info = flyway.info();
        var applied = info.applied();

        assertTrue(applied.length > 0, "No migrations applied");
        assertEquals(0, info.pending().length, "Pending migrations found");
    }

    @Test
    void shouldHaveCorrectSchemaVersion() {
        var current = flyway.info().current();
        assertNotNull(current, "No current version");
        assertTrue(current.getVersion().getVersion().matches("\\d+"),
            "Invalid version format");
    }

    @Test
    void shouldValidateMigrationChecksums() {
        var info = flyway.info();
        for (var migration : info.all()) {
            if (migration.getState().isApplied()) {
                assertNotNull(migration.getChecksum(),
                    "Applied migration missing checksum: " + migration.getDescription());
            }
        }
    }
}
```

### 2. Schema Validation Tests

```java
@Test
void shouldHaveRequiredSchemas() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
        Set<String> schemas = new HashSet<>();
        ResultSet rs = conn.getMetaData().getSchemas();
        while (rs.next()) {
            schemas.add(rs.getString("TABLE_SCHEM"));
        }

        assertTrue(schemas.contains("event_store"));
        assertTrue(schemas.contains("read_model"));
        assertTrue(schemas.contains("command_model"));
    }
}

@Test
void shouldHaveEventStoreTables() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
        ResultSet rs = conn.getMetaData().getTables(
            null, "event_store", "%", new String[]{"TABLE"});

        Set<String> tables = new HashSet<>();
        while (rs.next()) {
            tables.add(rs.getString("TABLE_NAME"));
        }

        assertTrue(tables.contains("event_stream"));
        assertTrue(tables.contains("domain_event"));
    }
}
```

## Rollout Steps

1. **Add Flyway dependencies**
   - Update pom.xml
   - Run Maven build

2. **Configure Flyway in application.yml**
   - Set migration locations
   - Configure schemas
   - Set validation rules

3. **Create migration directory structure**
   ```bash
   mkdir -p src/main/resources/db/migration
   ```

4. **Move init scripts to Flyway migrations**
   - Convert init scripts to versioned migrations
   - Follow naming convention
   - Test migrations

5. **Create migration helper script**
   - Write create-migration.sh
   - Make executable
   - Test script

6. **Update JPA configuration**
   - Set `ddl-auto: validate`
   - Disable schema auto-creation

7. **Test migrations**
   - Clean database
   - Start application
   - Verify migrations applied

8. **Create callback handlers**
   - Implement FlywayCallback
   - Add migration logger
   - Test callbacks

9. **Write integration tests**
   - Test migration execution
   - Validate schema
   - Test rollback scenarios

10. **Document migration process**
    - Create migration guide
    - Document naming conventions
    - Provide examples

## Verification Checklist

- [ ] Flyway dependencies added to pom.xml
- [ ] Flyway configured in application.yml
- [ ] Migration directory created
- [ ] Baseline migrations created
- [ ] Migrations execute on application startup
- [ ] Migration history tracked in flyway_schema_history table
- [ ] JPA ddl-auto set to validate
- [ ] Migration helper script created and tested
- [ ] All migrations have proper version numbers
- [ ] Migration checksums validated
- [ ] Callback handlers implemented
- [ ] Integration tests passing
- [ ] Documentation complete

## Troubleshooting Guide

### Issue: Migration checksum mismatch
**Solution:**
- Never modify applied migrations
- Create new migration to fix issues
- Use `flyway.repair()` for development only

### Issue: Migration fails mid-execution
**Solution:**
- Check Flyway schema history: `SELECT * FROM flyway_schema_history`
- Fix the migration SQL
- Use `flyway.repair()` to mark as failed
- Re-run migration

### Issue: Out-of-order migration detected
**Solution:**
- Set `out-of-order: true` temporarily
- Better: renumber migrations to maintain order
- Best: plan migrations carefully

### Issue: Baseline needed for existing database
**Solution:**
```bash
# Set baseline version to skip existing schema
spring.flyway.baseline-version=0
spring.flyway.baseline-on-migrate=true
```

## Related Documentation

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [PostgreSQL Migration Best Practices](https://www.postgresql.org/docs/current/ddl.html)

## Dependencies

- **Blocks:** AC7 (Data Seeding and Reset)
- **Blocked By:** AC3 (Relational Database)
- **Related:** AC2 (Secrets Management Integration)
