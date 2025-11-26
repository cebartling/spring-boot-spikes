# Implementation Plan: AC7 - Data Seeding and Reset

**Feature:** [Local Development Services Infrastructure](../../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC7 - Data Seeding and Reset

## Overview

Provide tooling and scripts for seeding the database with test data, resetting the database to a clean state, and loading different data scenarios to support various testing and development needs.

## Prerequisites

- AC3 (Relational Database) completed
- AC4 (Database Migration Support) completed
- AC5 (Infrastructure Orchestration) completed
- Flyway migrations applied

## Technical Implementation

### 1. Seed Data Directory Structure

```
infrastructure/
└── postgres/
    └── seed-data/
        ├── scenarios/
        │   ├── minimal/
        │   │   ├── 01_seed_events.sql
        │   │   └── 02_seed_read_models.sql
        │   ├── standard/
        │   │   ├── 01_seed_events.sql
        │   │   ├── 02_seed_read_models.sql
        │   │   └── 03_seed_users.sql
        │   ├── full/
        │   │   ├── 01_seed_events.sql
        │   │   ├── 02_seed_read_models.sql
        │   │   ├── 03_seed_users.sql
        │   │   └── 04_seed_analytics.sql
        │   └── performance/
        │       └── 01_large_dataset.sql
        ├── fixtures/
        │   ├── events.json
        │   ├── aggregates.json
        │   └── users.json
        └── scripts/
            ├── seed.sh
            ├── reset.sh
            └── load-scenario.sh
```

### 2. Seed Data SQL Scripts

**Minimal Scenario - Event Store Data:**
```sql
-- infrastructure/postgres/seed-data/scenarios/minimal/01_seed_events.sql

SET search_path TO event_store;

-- Clean existing data (in reverse dependency order)
TRUNCATE TABLE domain_event CASCADE;
TRUNCATE TABLE event_stream CASCADE;

-- Seed event streams
INSERT INTO event_stream (stream_id, aggregate_type, aggregate_id, version, created_at, updated_at)
VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'Order', '650e8400-e29b-41d4-a716-446655440001', 2, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),
    ('550e8400-e29b-41d4-a716-446655440002', 'Order', '650e8400-e29b-41d4-a716-446655440002', 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    ('550e8400-e29b-41d4-a716-446655440003', 'Customer', '750e8400-e29b-41d4-a716-446655440001', 1, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

-- Seed domain events for Order 1
INSERT INTO domain_event (
    event_id,
    stream_id,
    event_type,
    event_version,
    aggregate_version,
    event_data,
    metadata,
    occurred_at,
    correlation_id,
    user_id
) VALUES
    (
        '850e8400-e29b-41d4-a716-446655440001',
        '550e8400-e29b-41d4-a716-446655440001',
        'OrderCreated',
        1,
        1,
        '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "P001", "quantity": 2, "price": 29.99}], "totalAmount": 59.98}'::jsonb,
        '{"ipAddress": "192.168.1.100", "userAgent": "Mozilla/5.0"}'::jsonb,
        NOW() - INTERVAL '2 days',
        '950e8400-e29b-41d4-a716-446655440001',
        'user-001'
    ),
    (
        '850e8400-e29b-41d4-a716-446655440002',
        '550e8400-e29b-41d4-a716-446655440001',
        'OrderShipped',
        1,
        2,
        '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "shippingAddress": {"street": "123 Main St", "city": "Springfield", "zip": "12345"}, "trackingNumber": "TRACK001"}'::jsonb,
        '{"carrier": "FedEx"}'::jsonb,
        NOW() - INTERVAL '1 day',
        '950e8400-e29b-41d4-a716-446655440001',
        'system'
    );

-- Seed domain events for Order 2
INSERT INTO domain_event (
    event_id,
    stream_id,
    event_type,
    event_version,
    aggregate_version,
    event_data,
    metadata,
    occurred_at,
    correlation_id,
    user_id
) VALUES
    (
        '850e8400-e29b-41d4-a716-446655440003',
        '550e8400-e29b-41d4-a716-446655440002',
        'OrderCreated',
        1,
        1,
        '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "P002", "quantity": 1, "price": 149.99}], "totalAmount": 149.99}'::jsonb,
        '{"ipAddress": "192.168.1.101"}'::jsonb,
        NOW() - INTERVAL '1 day',
        '950e8400-e29b-41d4-a716-446655440002',
        'user-001'
    );

-- Seed domain events for Customer
INSERT INTO domain_event (
    event_id,
    stream_id,
    event_type,
    event_version,
    aggregate_version,
    event_data,
    metadata,
    occurred_at,
    correlation_id,
    user_id
) VALUES
    (
        '850e8400-e29b-41d4-a716-446655440004',
        '550e8400-e29b-41d4-a716-446655440003',
        'CustomerRegistered',
        1,
        1,
        '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "email": "customer@example.com", "name": "John Doe", "registeredAt": "2024-01-15T10:00:00Z"}'::jsonb,
        '{"source": "web"}'::jsonb,
        NOW() - INTERVAL '3 days',
        '950e8400-e29b-41d4-a716-446655440003',
        'system'
    );

-- Verify seeded data
SELECT 'Event streams seeded: ' || COUNT(*) FROM event_stream;
SELECT 'Domain events seeded: ' || COUNT(*) FROM domain_event;
```

**Minimal Scenario - Read Model Data:**
```sql
-- infrastructure/postgres/seed-data/scenarios/minimal/02_seed_read_models.sql

SET search_path TO read_model;

-- Create a simple read model table (if not exists via migration)
CREATE TABLE IF NOT EXISTS order_summary (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE order_summary CASCADE;

-- Seed read model (projected from events)
INSERT INTO order_summary (order_id, customer_id, total_amount, status, created_at, updated_at)
VALUES
    ('650e8400-e29b-41d4-a716-446655440001', '750e8400-e29b-41d4-a716-446655440001', 59.98, 'SHIPPED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),
    ('650e8400-e29b-41d4-a716-446655440002', '750e8400-e29b-41d4-a716-446655440001', 149.99, 'PENDING', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Verify
SELECT 'Order summaries seeded: ' || COUNT(*) FROM order_summary;
```

**Standard Scenario - Additional User Data:**
```sql
-- infrastructure/postgres/seed-data/scenarios/standard/03_seed_users.sql

SET search_path TO command_model;

-- Create users table (if not exists via migration)
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE users CASCADE;

-- Seed users
INSERT INTO users (user_id, username, email)
VALUES
    ('user-001', 'johndoe', 'john.doe@example.com'),
    ('user-002', 'janedoe', 'jane.doe@example.com'),
    ('system', 'system', 'system@example.com');

-- Verify
SELECT 'Users seeded: ' || COUNT(*) FROM users;
```

**Performance Scenario - Large Dataset:**
```sql
-- infrastructure/postgres/seed-data/scenarios/performance/01_large_dataset.sql

SET search_path TO event_store;

-- Generate 1000 event streams with 10 events each
DO $$
DECLARE
    i INTEGER;
    j INTEGER;
    stream_uuid UUID;
    aggregate_uuid UUID;
    event_uuid UUID;
    correlation_uuid UUID;
BEGIN
    FOR i IN 1..1000 LOOP
        stream_uuid := uuid_generate_v4();
        aggregate_uuid := uuid_generate_v4();
        correlation_uuid := uuid_generate_v4();

        -- Create stream
        INSERT INTO event_stream (stream_id, aggregate_type, aggregate_id, version, created_at, updated_at)
        VALUES (stream_uuid, 'TestAggregate', aggregate_uuid, 10, NOW() - (i || ' hours')::INTERVAL, NOW());

        -- Create 10 events per stream
        FOR j IN 1..10 LOOP
            event_uuid := uuid_generate_v4();

            INSERT INTO domain_event (
                event_id,
                stream_id,
                event_type,
                event_version,
                aggregate_version,
                event_data,
                metadata,
                occurred_at,
                correlation_id,
                user_id
            ) VALUES (
                event_uuid,
                stream_uuid,
                'TestEvent' || j,
                1,
                j,
                ('{"data": "test-' || i || '-' || j || '", "timestamp": "' || NOW() || '"}')::jsonb,
                ('{"meta": "value-' || i || '"}')::jsonb,
                NOW() - (i || ' hours')::INTERVAL + (j || ' minutes')::INTERVAL,
                correlation_uuid,
                'perf-test-user'
            );
        END LOOP;

        IF i % 100 = 0 THEN
            RAISE NOTICE 'Progress: % streams created', i;
        END IF;
    END LOOP;
END $$;

-- Verify
SELECT 'Performance test streams seeded: ' || COUNT(*) FROM event_stream WHERE aggregate_type = 'TestAggregate';
SELECT 'Performance test events seeded: ' || COUNT(*) FROM domain_event WHERE user_id = 'perf-test-user';
```

### 3. Seed Data Loading Script

```bash
#!/bin/bash
# infrastructure/postgres/seed-data/scripts/seed.sh

set -e

SCENARIO="${1:-minimal}"
SEED_DIR="$(dirname "$0")/../scenarios/$SCENARIO"

if [ ! -d "$SEED_DIR" ]; then
    echo "Error: Scenario '$SCENARIO' not found"
    echo "Available scenarios:"
    ls -1 "$(dirname "$0")/../scenarios/"
    exit 1
fi

echo "========================================="
echo "Seeding Database: $SCENARIO scenario"
echo "========================================="

# Check if PostgreSQL is accessible
if ! docker exec cqrs-postgres pg_isready -U cqrs_user -d cqrs_db > /dev/null 2>&1; then
    echo "Error: PostgreSQL is not accessible"
    exit 1
fi

# Execute seed scripts in order
for script in "$SEED_DIR"/*.sql; do
    if [ -f "$script" ]; then
        echo "Executing: $(basename "$script")"
        docker exec -i cqrs-postgres psql -U cqrs_user -d cqrs_db < "$script"
    fi
done

echo "========================================="
echo "Seeding Complete!"
echo "========================================="

# Show summary
docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -c "
SELECT
    'Event Streams: ' || COUNT(*) as summary
FROM event_store.event_stream
UNION ALL
SELECT
    'Domain Events: ' || COUNT(*)
FROM event_store.domain_event
UNION ALL
SELECT
    'Order Summaries: ' || COALESCE(COUNT(*), 0)
FROM read_model.order_summary;
"
```

### 4. Database Reset Script

```bash
#!/bin/bash
# infrastructure/postgres/seed-data/scripts/reset.sh

set -e

echo "========================================="
echo "Database Reset"
echo "========================================="
echo "WARNING: This will delete ALL data and re-run migrations!"
echo ""
read -p "Are you sure you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Reset cancelled"
    exit 0
fi

# Option 1: Drop and recreate database (clean slate)
echo "Dropping and recreating database..."
docker exec cqrs-postgres psql -U postgres -c "DROP DATABASE IF EXISTS cqrs_db;"
docker exec cqrs-postgres psql -U postgres -c "CREATE DATABASE cqrs_db OWNER cqrs_user;"

echo "Database reset complete"
echo ""
echo "Restart the application to re-run migrations:"
echo "  docker-compose restart app"
echo ""
echo "Or seed data with:"
echo "  ./infrastructure/postgres/seed-data/scripts/seed.sh [scenario]"
```

**Alternative: Truncate-based reset (preserves schema):**
```bash
#!/bin/bash
# infrastructure/postgres/seed-data/scripts/reset-data-only.sh

set -e

echo "========================================="
echo "Database Data Reset (Schema Preserved)"
echo "========================================="
echo "WARNING: This will delete ALL data but preserve schema!"
echo ""
read -p "Are you sure? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Reset cancelled"
    exit 0
fi

# Truncate all tables in reverse dependency order
docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db <<'EOF'
SET search_path TO event_store, read_model, command_model;

-- Disable triggers temporarily
SET session_replication_role = replica;

-- Truncate event store
TRUNCATE TABLE event_store.domain_event CASCADE;
TRUNCATE TABLE event_store.event_stream CASCADE;

-- Truncate read models
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'read_model') LOOP
        EXECUTE 'TRUNCATE TABLE read_model.' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END $$;

-- Truncate command model
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'command_model') LOOP
        EXECUTE 'TRUNCATE TABLE command_model.' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END $$;

-- Re-enable triggers
SET session_replication_role = DEFAULT;

SELECT 'All data truncated successfully';
EOF

echo "Data reset complete!"
```

### 5. Scenario Loading Script

```bash
#!/bin/bash
# infrastructure/postgres/seed-data/scripts/load-scenario.sh

set -e

SCENARIO="$1"

if [ -z "$SCENARIO" ]; then
    echo "Available scenarios:"
    ls -1 infrastructure/postgres/seed-data/scenarios/
    echo ""
    read -p "Select scenario: " SCENARIO
fi

echo "Loading scenario: $SCENARIO"
echo ""

# Reset data first
./infrastructure/postgres/seed-data/scripts/reset-data-only.sh

# Load scenario
./infrastructure/postgres/seed-data/scripts/seed.sh "$SCENARIO"

echo ""
echo "Scenario '$SCENARIO' loaded successfully!"
```

### 6. Makefile Integration

```makefile
# Add to existing Makefile

.PHONY: db-seed db-reset db-scenario

db-seed: ## Seed database with minimal scenario
	@./infrastructure/postgres/seed-data/scripts/seed.sh minimal

db-seed-standard: ## Seed database with standard scenario
	@./infrastructure/postgres/seed-data/scripts/seed.sh standard

db-seed-full: ## Seed database with full scenario
	@./infrastructure/postgres/seed-data/scripts/seed.sh full

db-seed-perf: ## Seed database with performance test data
	@./infrastructure/postgres/seed-data/scripts/seed.sh performance

db-reset: ## Reset database (WARNING: Deletes all data!)
	@./infrastructure/postgres/seed-data/scripts/reset.sh

db-reset-data: ## Reset data only (preserves schema)
	@./infrastructure/postgres/seed-data/scripts/reset-data-only.sh

db-scenario: ## Load a specific scenario (interactive)
	@./infrastructure/postgres/seed-data/scripts/load-scenario.sh
```

### 7. Java-Based Seeding (Alternative/Supplement)

```java
package com.example.cqrs.infrastructure.database.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DatabaseSeeder implements CommandLineRunner {

    private final EventStoreSeeder eventStoreSeeder;
    private final ReadModelSeeder readModelSeeder;

    @Override
    public void run(String... args) {
        String scenario = System.getProperty("app.seed.scenario", "minimal");

        log.info("Seeding database with scenario: {}", scenario);

        switch (scenario) {
            case "minimal":
                seedMinimal();
                break;
            case "standard":
                seedStandard();
                break;
            case "full":
                seedFull();
                break;
            default:
                log.warn("Unknown scenario: {}", scenario);
        }

        log.info("Database seeding complete");
    }

    private void seedMinimal() {
        eventStoreSeeder.seedMinimalEvents();
        readModelSeeder.seedMinimalReadModels();
    }

    private void seedStandard() {
        seedMinimal();
        // Additional standard seeding
    }

    private void seedFull() {
        seedStandard();
        // Additional full seeding
    }
}
```

**Configuration:**
```yaml
# application-local.yml
app:
  seed:
    enabled: false  # Set to true to enable seeding on startup
    scenario: minimal
```

## Testing Strategy

### 1. Seed Script Tests

```bash
#!/bin/bash
# scripts/test-seeding.sh

echo "Testing database seeding..."

# Reset database
./infrastructure/postgres/seed-data/scripts/reset-data-only.sh

# Test minimal scenario
./infrastructure/postgres/seed-data/scripts/seed.sh minimal

# Verify data exists
EVENT_COUNT=$(docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -t -c "SELECT COUNT(*) FROM event_store.domain_event")

if [ "$EVENT_COUNT" -gt 0 ]; then
    echo "✓ Minimal scenario seeded successfully"
else
    echo "✗ Minimal scenario failed"
    exit 1
fi

# Test reset
./infrastructure/postgres/seed-data/scripts/reset-data-only.sh

EVENT_COUNT=$(docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -t -c "SELECT COUNT(*) FROM event_store.domain_event")

if [ "$EVENT_COUNT" -eq 0 ]; then
    echo "✓ Reset successful"
else
    echo "✗ Reset failed"
    exit 1
fi

echo "✓ All seeding tests passed"
```

### 2. Scenario Validation Tests

```sql
-- tests/validate-minimal-scenario.sql

DO $$
DECLARE
    event_count INTEGER;
    stream_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO event_count FROM event_store.domain_event;
    SELECT COUNT(*) INTO stream_count FROM event_store.event_stream;

    ASSERT event_count >= 4, 'Expected at least 4 events in minimal scenario';
    ASSERT stream_count >= 3, 'Expected at least 3 streams in minimal scenario';

    RAISE NOTICE 'Minimal scenario validation passed';
END $$;
```

## Rollout Steps

1. **Create seed data directory structure**
   ```bash
   mkdir -p infrastructure/postgres/seed-data/{scenarios/{minimal,standard,full,performance},fixtures,scripts}
   ```

2. **Write seed SQL scripts**
   - Create minimal scenario
   - Create standard scenario
   - Create full scenario
   - Create performance scenario

3. **Write seed management scripts**
   - seed.sh
   - reset.sh
   - reset-data-only.sh
   - load-scenario.sh

4. **Make scripts executable**
   ```bash
   chmod +x infrastructure/postgres/seed-data/scripts/*.sh
   ```

5. **Update Makefile**
   - Add db-seed targets
   - Add db-reset targets
   - Add db-scenario target

6. **Test each scenario**
   - Load minimal
   - Load standard
   - Load full
   - Load performance

7. **Test reset functionality**
   - Test full reset
   - Test data-only reset
   - Verify migrations re-run

8. **Create validation tests**
   - Write scenario validators
   - Test script automation

9. **Document usage**
   - Add to main README
   - Create seeding guide
   - Document scenarios

10. **Optional: Java-based seeding**
    - Implement if needed
    - Configure conditionally

## Verification Checklist

- [ ] Seed data directory structure created
- [ ] Minimal scenario scripts written and tested
- [ ] Standard scenario scripts written and tested
- [ ] Full scenario scripts written and tested
- [ ] Performance scenario scripts written and tested
- [ ] Seed script loads data correctly
- [ ] Reset script cleans database completely
- [ ] Data-only reset preserves schema
- [ ] Scenario loading script works
- [ ] Makefile targets functional
- [ ] Multiple scenarios can be loaded
- [ ] Reset is safe (requires confirmation)
- [ ] Documentation complete

## Troubleshooting Guide

### Issue: Seed script fails with foreign key violations
**Solution:**
- Ensure data loaded in correct order (streams before events)
- Check referential integrity
- Use CASCADE on truncate operations

### Issue: Reset doesn't clean all data
**Solution:**
- Check for tables in other schemas
- Verify truncate cascade works
- Consider DROP DATABASE approach

### Issue: Performance scenario too slow
**Solution:**
- Reduce dataset size
- Use COPY instead of INSERT
- Batch inserts in transactions
- Add timing output for monitoring

### Issue: Scenarios conflict with each other
**Solution:**
- Always reset before loading new scenario
- Use unique IDs per scenario
- Document scenario dependencies

## Dependencies

- **Blocks:** None
- **Blocked By:** AC3 (Relational Database), AC4 (Database Migration Support), AC5 (Infrastructure Orchestration)
- **Related:** AC6 (Development Experience)
