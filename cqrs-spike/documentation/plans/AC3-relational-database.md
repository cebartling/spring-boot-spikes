# Implementation Plan: AC3 - Relational Database

**Feature:** [Local Development Services Infrastructure](../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC3 - Relational Database

## Overview

Set up a PostgreSQL relational database for local development that provides persistent storage for the CQRS application,
integrates with the secrets management system, and supports CQRS/Event Sourcing patterns.

## Prerequisites

- AC1 (Secrets Management) completed
- AC2 (Secrets Management Integration) completed
- Docker and Docker Compose installed

## Technology Selection

### Recommended: PostgreSQL 18

**Rationale:**

- Robust support for JSONB data types (excellent for event sourcing)
- ACID compliance essential for event store consistency
- Excellent performance for both transactional and analytical workloads
- Strong Spring Boot/JPA support
- Built-in support for UUID generation
- Advanced indexing capabilities for event queries
- Materialized views for read model optimization

**Alternatives Considered:**

- MySQL: Less robust JSON support, weaker for event sourcing patterns
- MariaDB: Good alternative but PostgreSQL JSONB superior for events
- H2: Not suitable for development environment that mirrors production

## Database Design Considerations

### CQRS/Event Sourcing Schema Strategy

**Event Store Schema:**

- Dedicated schema for event sourcing: `event_store`
- Aggregate-based event streams with optimistic locking
- Immutable event records

**Read Model Schema:**

- Separate schema for projections: `read_model`
- Denormalized views optimized for queries
- Can be rebuilt from event store

**Command Model Schema:**

- Transaction-focused schema: `command_model`
- Traditional normalized design where needed

## Technical Implementation

### 1. Docker Configuration

**PostgreSQL Container Setup:**

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:18-alpine
    container_name: cqrs-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-cqrs_db}
      POSTGRES_USER: ${POSTGRES_USER:-cqrs_user}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-local_dev_password}
      PGDATA: /var/lib/postgresql/data/pgdata
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init:/docker-entrypoint-initdb.d
      - ./infrastructure/postgres/config/postgresql.conf:/etc/postgresql/postgresql.conf
    command: postgres -c config_file=/etc/postgresql/postgresql.conf
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-cqrs_user} -d ${POSTGRES_DB:-cqrs_db}" ]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - cqrs-network
    depends_on:
      vault:
        condition: service_healthy

volumes:
  postgres-data:
    driver: local
    name: cqrs-postgres-data

networks:
  cqrs-network:
    driver: bridge
```

**Key Configuration Details:**

- Port 5432: Standard PostgreSQL port
- Alpine image: Smaller footprint for faster startup
- Health check: Ensures database is ready before application starts
- Named volume: Persists data across container restarts
- Init scripts: Automatic schema initialization
- Custom postgresql.conf: Performance tuning for development

### 2. PostgreSQL Configuration

**Custom postgresql.conf:**

```conf
# infrastructure/postgres/config/postgresql.conf

# Connection Settings
listen_addresses = '*'
max_connections = 100

# Memory Settings (tuned for local development)
shared_buffers = 256MB
effective_cache_size = 1GB
maintenance_work_mem = 64MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 4MB
min_wal_size = 1GB
max_wal_size = 4GB

# Logging (verbose for development)
log_destination = 'stderr'
logging_collector = on
log_directory = 'pg_log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_statement = 'mod'
log_duration = on
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on
log_temp_files = 0

# Locale Settings
lc_messages = 'en_US.utf8'
lc_monetary = 'en_US.utf8'
lc_numeric = 'en_US.utf8'
lc_time = 'en_US.utf8'

# Default locale for new databases
default_text_search_config = 'pg_catalog.english'

# Time Zone
timezone = 'UTC'
```

### 3. Database Initialization Scripts

**Initial Schema Creation:**

```sql
-- infrastructure/postgres/init/01-create-schemas.sql

-- Create schemas for CQRS pattern
CREATE SCHEMA IF NOT EXISTS event_store;
CREATE SCHEMA IF NOT EXISTS read_model;
CREATE SCHEMA IF NOT EXISTS command_model;

-- Create extensions
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE
EXTENSION IF NOT EXISTS "pgcrypto";

-- Set search path
ALTER
DATABASE cqrs_db SET search_path TO event_store, read_model, command_model, public;

-- Grant permissions to application user
GRANT USAGE ON SCHEMA
event_store TO cqrs_user;
GRANT USAGE ON SCHEMA
read_model TO cqrs_user;
GRANT USAGE ON SCHEMA
command_model TO cqrs_user;

GRANT CREATE
ON SCHEMA event_store TO cqrs_user;
GRANT CREATE
ON SCHEMA read_model TO cqrs_user;
GRANT CREATE
ON SCHEMA command_model TO cqrs_user;

-- Comment on schemas
COMMENT
ON SCHEMA event_store IS 'Event Sourcing - immutable event log';
COMMENT
ON SCHEMA read_model IS 'CQRS Read Side - denormalized projections';
COMMENT
ON SCHEMA command_model IS 'CQRS Write Side - normalized domain model';
```

**Event Store Base Tables:**

```sql
-- infrastructure/postgres/init/02-event-store.sql

SET
search_path TO event_store;

-- Event Stream table
CREATE TABLE IF NOT EXISTS event_stream
(
    stream_id
    UUID
    PRIMARY
    KEY
    DEFAULT
    uuid_generate_v4
(
),
    aggregate_type VARCHAR
(
    255
) NOT NULL,
    aggregate_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             version INTEGER NOT NULL DEFAULT 0,
                             UNIQUE (aggregate_type, aggregate_id)
    );

CREATE INDEX idx_event_stream_aggregate ON event_stream (aggregate_type, aggregate_id);

COMMENT
ON TABLE event_stream IS 'Aggregate event streams';
COMMENT
ON COLUMN event_stream.version IS 'Current version for optimistic locking';

-- Domain Events table
CREATE TABLE IF NOT EXISTS domain_event
(
    event_id
    UUID
    PRIMARY
    KEY
    DEFAULT
    uuid_generate_v4
(
),
    stream_id UUID NOT NULL REFERENCES event_stream
(
    stream_id
),
    event_type VARCHAR
(
    255
) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_version INTEGER NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              causation_id UUID,
                              correlation_id UUID
                              );

CREATE INDEX idx_domain_event_stream ON domain_event (stream_id, aggregate_version);
CREATE INDEX idx_domain_event_type ON domain_event (event_type);
CREATE INDEX idx_domain_event_occurred ON domain_event (occurred_at DESC);
CREATE INDEX idx_domain_event_correlation ON domain_event (correlation_id);
CREATE INDEX idx_domain_event_data ON domain_event USING GIN(event_data);

COMMENT
ON TABLE domain_event IS 'Immutable domain events';
COMMENT
ON COLUMN domain_event.aggregate_version IS 'Version of aggregate after this event';
COMMENT
ON COLUMN domain_event.causation_id IS 'ID of command that caused this event';
COMMENT
ON COLUMN domain_event.correlation_id IS 'Correlation ID for tracking related events';

-- Event Store function for appending events
CREATE
OR REPLACE FUNCTION append_events(
    p_aggregate_type VARCHAR,
    p_aggregate_id UUID,
    p_expected_version INTEGER,
    p_events JSONB[]
) RETURNS UUID AS $$
DECLARE
v_stream_id UUID;
    v_current_version
INTEGER;
    v_event
JSONB;
    v_new_version
INTEGER;
BEGIN
    -- Get or create stream
INSERT INTO event_stream (aggregate_type, aggregate_id, version)
VALUES (p_aggregate_type, p_aggregate_id, 0) ON CONFLICT (aggregate_type, aggregate_id) DO NOTHING
    RETURNING stream_id, version
INTO v_stream_id, v_current_version;

IF
v_stream_id IS NULL THEN
SELECT stream_id, version
INTO v_stream_id, v_current_version
FROM event_stream
WHERE aggregate_type = p_aggregate_type
  AND aggregate_id = p_aggregate_id;
END IF;

    -- Optimistic concurrency check
    IF
v_current_version != p_expected_version THEN
        RAISE EXCEPTION 'Concurrency violation: expected version %, but current version is %',
            p_expected_version, v_current_version;
END IF;

    -- Append events
    v_new_version
:= v_current_version;
    FOREACH
v_event IN ARRAY p_events LOOP
        v_new_version := v_new_version + 1;

INSERT INTO domain_event (stream_id,
                          event_type,
                          event_version,
                          aggregate_version,
                          event_data,
                          metadata,
                          causation_id,
                          correlation_id)
VALUES (v_stream_id,
        v_event ->>'event_type',
        (v_event ->>'event_version'):: INTEGER,
        v_new_version,
        v_event - > 'event_data',
        v_event - > 'metadata',
        (v_event ->>'causation_id')::UUID,
        (v_event ->>'correlation_id')::UUID);
END LOOP;

    -- Update stream version
UPDATE event_stream
SET version = v_new_version
WHERE stream_id = v_stream_id;

RETURN v_stream_id;
END;
$$
LANGUAGE plpgsql;

COMMENT
ON FUNCTION append_events IS 'Atomically append events to an aggregate stream with optimistic locking';
```

### 4. Spring Boot Configuration

**Database Dependencies:**

```kotlin
// build.gradle.kts
dependencies {
    // PostgreSQL Driver
    runtimeOnly("org.postgresql:postgresql")

    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // HikariCP (included with Spring Boot, but explicit for clarity)
    implementation("com.zaxxer:HikariCP")

    // Hibernate Types for JSONB support
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.0")
}
```

**DataSource Configuration:**

```yaml
# application.yml
spring:
  datasource:
    url: ${database.url}
    username: ${database.username}
    password: ${database.password}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: CqrsHikariPool
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      auto-commit: false
      connection-test-query: SELECT 1

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
        query:
          in_clause_parameter_padding: true
        default_schema: command_model
    show-sql: false

  sql:
    init:
      mode: never

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.jdbc.core: DEBUG
```

### 5. Vault Integration for Database Credentials

**Store Database Credentials in Vault:**

```bash
#!/bin/bash
# infrastructure/vault/scripts/init-database-secrets.sh

# Wait for Vault and PostgreSQL to be ready
until vault status > /dev/null 2>&1; do
  echo "Waiting for Vault..."
  sleep 2
done

until pg_isready -h postgres -U cqrs_user > /dev/null 2>&1; do
  echo "Waiting for PostgreSQL..."
  sleep 2
done

# Store database credentials
vault kv put secret/cqrs-spike/database \
  username="cqrs_user" \
  password="local_dev_password" \
  url="jdbc:postgresql://postgres:5432/cqrs_db" \
  driver-class-name="org.postgresql.Driver"

echo "Database credentials stored in Vault"
```

**Update docker-compose.yml:**

```yaml
services:
  vault:
    # ... existing vault config ...
    volumes:
      - ./infrastructure/vault/scripts:/vault/scripts
    entrypoint: /bin/sh
    command: |
      -c "
      vault server -dev -dev-root-token-id=dev-root-token &
      sleep 5
      export VAULT_ADDR='http://localhost:8200'
      export VAULT_TOKEN='dev-root-token'
      /vault/scripts/init-database-secrets.sh
      wait
      "
```

### 6. Data Persistence Configuration

**Volume Management:**

```yaml
# docker-compose.yml
volumes:
  postgres-data:
    driver: local
    name: cqrs-postgres-data
    driver_opts:
      type: none
      device: ${PWD}/infrastructure/postgres/data
      o: bind
```

**Data Directory Structure:**

```
infrastructure/
└── postgres/
    ├── config/
    │   └── postgresql.conf
    ├── init/
    │   ├── 01-create-schemas.sql
    │   ├── 02-event-store.sql
    │   └── 03-read-model.sql
    ├── data/
    │   └── (PostgreSQL data files - gitignored)
    └── backups/
        └── (Database backups - gitignored)
```

**.gitignore entries:**

```
infrastructure/postgres/data/
infrastructure/postgres/backups/
```

### 7. Database Connection Management

**HikariCP Configuration Bean:**

```kotlin
package com.pintailconsultingllc.cqrsspike.infrastructure.database.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Database configuration for PostgreSQL with HikariCP connection pooling.
 *
 * This configuration retrieves database credentials from Vault via Spring Cloud Vault
 * and sets up an optimized HikariCP connection pool for the CQRS application.
 */
@Configuration
class DatabaseConfiguration {

    private val logger = LoggerFactory.getLogger(DatabaseConfiguration::class.java)

    @Value("\${spring.datasource.url}")
    private lateinit var jdbcUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var username: String

    @Value("\${spring.datasource.password}")
    private lateinit var password: String

    @Bean
    @Primary
    fun dataSource(): DataSource {
        logger.info("Configuring HikariCP DataSource")
        logger.info("Database URL: {}", jdbcUrl)
        logger.info("Database User: {}", username)

        val config = HikariConfig().apply {
            this.jdbcUrl = this@DatabaseConfiguration.jdbcUrl
            this.username = this@DatabaseConfiguration.username
            this.password = this@DatabaseConfiguration.password
            driverClassName = "org.postgresql.Driver"

            // Pool settings
            maximumPoolSize = 10
            minimumIdle = 5
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            isAutoCommit = false

            // Performance optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

            // Connection test
            connectionTestQuery = "SELECT 1"
        }

        logger.info("HikariCP configuration complete")
        return HikariDataSource(config)
    }
}
```

## Testing Strategy

### 1. Container Health Tests

**PostgreSQL Availability:**

```bash
# Test database is accessible
docker exec cqrs-postgres pg_isready -U cqrs_user -d cqrs_db

# Expected: accepting connections
```

### 2. Connection Tests

**JDBC Connection Test:**

```kotlin
package com.pintailconsultingllc.cqrsspike.infrastructure.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import javax.sql.DataSource

@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `should connect to database`() {
        dataSource.connection.use { conn ->
            assertTrue(conn.isValid(5))
            assertEquals("cqrs_db", conn.catalog)
        }
    }

    @Test
    fun `should have required schemas`() {
        dataSource.connection.use { conn ->
            val schemas = mutableSetOf<String>()

            conn.metaData.schemas.use { rs ->
                while (rs.next()) {
                    schemas.add(rs.getString("TABLE_SCHEM"))
                }
            }

            assertTrue(schemas.contains("event_store"))
            assertTrue(schemas.contains("read_model"))
            assertTrue(schemas.contains("command_model"))
        }
    }
}
```

### 3. Data Persistence Tests

**Volume Persistence Test:**

```bash
# Write test data
docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -c \
  "CREATE TABLE test (id SERIAL, data TEXT); INSERT INTO test (data) VALUES ('test');"

# Restart container
docker-compose restart postgres

# Verify data persists
docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -c "SELECT * FROM test;"

# Cleanup
docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -c "DROP TABLE test;"
```

## Rollout Steps

1. **Create directory structure**
   ```bash
   mkdir -p infrastructure/postgres/{config,init,data,backups}
   ```

2. **Create PostgreSQL configuration files**
    - Write postgresql.conf
    - Write init SQL scripts

3. **Update docker-compose.yml**
    - Add PostgreSQL service
    - Configure volumes
    - Set up health checks

4. **Configure Vault integration**
    - Create database secrets script
    - Update Vault initialization

5. **Add Spring Boot dependencies**
    - Update build.gradle.kts
    - Run Gradle build

6. **Configure Spring DataSource**
    - Update application.yml
    - Configure JPA properties

7. **Create database configuration classes**
    - Implement DatabaseConfiguration
    - Set up HikariCP

8. **Test database connectivity**
    - Start infrastructure
    - Run connection tests
    - Verify schemas created

9. **Test data persistence**
    - Insert test data
    - Restart container
    - Verify data retained

10. **Update .gitignore**
    - Ignore data directory
    - Ignore backup directory

## Verification Checklist

- [x] PostgreSQL container starts successfully
- [x] Database accessible on port 5432
- [x] Health check passes
- [x] All schemas created (event_store, read_model, command_model)
- [x] PostgreSQL extensions installed (uuid-ossp, pgcrypto)
- [x] Database credentials stored in Vault (via init-secrets.sh)
- [x] Spring Boot dependencies configured (R2DBC + HikariCP)
- [x] HikariCP pool configured via DatabaseConfiguration.kt
- [x] Data persists across container restarts (named volume)
- [x] PostgreSQL logs accessible via docker logs
- [x] Volume mounts working correctly

**Implementation Status:** ✅ Complete (as of 2025-11-23)
**Note:** Database connection tests require running PostgreSQL instance

## Implementation Notes

### What Was Implemented

1. **Directory Structure**
   - Created `infrastructure/postgres/{config,init,data,backups}/`
   - Data and backups directories added to .gitignore

2. **PostgreSQL Configuration**
   - Created `postgresql.conf` (not used in current setup due to mount issues)
   - Using default PostgreSQL 18 Alpine configuration

3. **Database Initialization Scripts**
   - `01-create-schemas.sql` - Creates CQRS schemas and extensions
   - `02-event-store.sql` - Creates event sourcing tables and functions
   - `03-read-model.sql` - Placeholder for read model projections

4. **Docker Configuration**
   - PostgreSQL 18 Alpine image
   - Named volume for data persistence (`cqrs-postgres-data`)
   - Health check using `pg_isready`
   - Initialization scripts mounted at `/docker-entrypoint-initdb.d`

5. **Spring Boot Integration**
   - Added R2DBC PostgreSQL driver for reactive database access
   - Added PostgreSQL JDBC driver for blocking operations
   - Added HikariCP for connection pooling
   - Added Hypersistence Utils for JSONB support
   - Created `DatabaseConfiguration.kt` with HikariCP setup
   - Configured both R2DBC and JDBC in `application.yml`

6. **Testing**
   - Created `DatabaseConnectionTest.kt` with schema and extension verification tests

### Issues Encountered and Resolved

#### Issue 1: Permission Denied on Data Directory

**Symptom:**
```
mkdir: can't create directory '/var/lib/postgresql/data/': Permission denied
```

**Root Cause:**
PGDATA environment variable set to `/var/lib/postgresql/data/pgdata` caused permission issues with the volume mount.

**Solution:**
Removed the `PGDATA` environment variable and let PostgreSQL use its default data directory.

#### Issue 2: Custom postgresql.conf Mount Failed

**Symptom:**
Container wouldn't start with custom configuration file mounted.

**Root Cause:**
Mounting custom postgresql.conf requires additional setup and the file needs to be in the correct format for PostgreSQL 18.

**Solution:**
Removed the custom postgresql.conf mount. Using default PostgreSQL configuration which is sufficient for local development. Custom configuration can be added later if needed via `-c` flags or environment variables.

#### Issue 3: uuid_generate_v4() Function Not Found

**Symptom:**
```
ERROR:  function uuid_generate_v4() does not exist
```

**Root Cause:**
The `uuid-ossp` extension was created in script `01-create-schemas.sql`, but script `02-event-store.sql` used `SET search_path TO event_store;` which removed access to the `public` schema where the extension functions live.

**Solution:**
Changed all table and function definitions to use fully qualified schema names (e.g., `event_store.event_stream` instead of just `event_stream`). Removed `SET search_path` statements.

### Files Created

- `infrastructure/postgres/config/postgresql.conf` (created but not currently used)
- `infrastructure/postgres/init/01-create-schemas.sql`
- `infrastructure/postgres/init/02-event-store.sql`
- `infrastructure/postgres/init/03-read-model.sql`
- `src/main/kotlin/com/pintailconsultingllc/cqrsspike/infrastructure/database/DatabaseConfiguration.kt`
- `src/test/kotlin/com/pintailconsultingllc/cqrsspike/infrastructure/database/DatabaseConnectionTest.kt`

### Files Modified

- `docker-compose.yml` - Added PostgreSQL service and volume
- `build.gradle.kts` - Added database dependencies
- `src/main/resources/application.yml` - Added R2DBC and JDBC configuration
- `.gitignore` - Added PostgreSQL data and backup directories

### Configuration Decisions

1. **R2DBC + JDBC Dual Configuration**: Configured both R2DBC (reactive) and JDBC (blocking) data sources. R2DBC is used for reactive operations via Spring Data R2DBC, while JDBC/HikariCP is available for migrations and admin tools.

2. **Schema-Qualified Names**: All database objects use fully qualified schema names to avoid search_path issues and make dependencies explicit.

3. **Named Volumes**: Using Docker named volumes instead of bind mounts for better cross-platform compatibility and avoiding permission issues.

4. **PostgreSQL 18 Alpine**: Using Alpine variant for smaller image size and faster startup.

## Troubleshooting Guide

### Issue: Container fails to start

**Solution:**

- Check logs: `docker logs cqrs-postgres`
- Verify port 5432 not in use: `lsof -i :5432`
- Check volume permissions

### Issue: Cannot connect from application

**Solution:**

- Verify container running: `docker ps | grep postgres`
- Test connection: `docker exec cqrs-postgres pg_isready`
- Check network: `docker network inspect cqrs-network`
- Verify credentials in Vault

### Issue: Data not persisting

**Solution:**

- Check volume mount: `docker volume inspect cqrs-postgres-data`
- Verify PGDATA setting in environment
- Check filesystem permissions

### Issue: Schema initialization fails

**Solution:**

- Check init script syntax
- Review container logs for SQL errors
- Verify script execution order
- Manually run scripts to debug

## Dependencies

- **Blocks:** AC4 (Database Migration Support), AC7 (Data Seeding and Reset)
- **Blocked By:** AC1 (Secrets Management), AC2 (Secrets Management Integration)
- **Related:** AC5 (Infrastructure Orchestration)
