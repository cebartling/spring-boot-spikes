# Data Seeding Guide

This guide covers loading test data into the CQRS Spike application for development and testing.

## Overview

The seeding system provides multiple scenarios for different testing needs:

| Scenario | Description | Use Case |
|----------|-------------|----------|
| `minimal` | Basic data for quick testing | Daily development |
| `standard` | Realistic data set | Feature development |
| `full` | Comprehensive test data | Integration testing |
| `performance` | Large data volume | Performance testing |

## Quick Start

### Load Minimal Data

```bash
make db-seed
```

### Load Standard Data

```bash
make db-seed-standard
```

### Load Full Data

```bash
make db-seed-full
```

### Load Performance Data

```bash
make db-seed-perf
```

## Seed Scenarios

### Minimal Scenario

Best for quick development cycles.

```bash
make db-seed
# or
./infrastructure/postgres/seed-data/scripts/seed.sh minimal
```

Creates:
- 5 sample aggregates
- 10-20 events per aggregate
- Basic read model projections

### Standard Scenario

Realistic data for feature development.

```bash
make db-seed-standard
# or
./infrastructure/postgres/seed-data/scripts/seed.sh standard
```

Creates:
- 50+ aggregates
- 100+ events
- Complete read model state
- Sample relationships

### Full Scenario

Comprehensive data for integration testing.

```bash
make db-seed-full
# or
./infrastructure/postgres/seed-data/scripts/seed.sh full
```

Creates:
- 200+ aggregates
- 500+ events
- All edge cases covered
- Complex relationships

### Performance Scenario

Large volume for performance testing.

```bash
make db-seed-perf
# or
./infrastructure/postgres/seed-data/scripts/seed.sh performance
```

Creates:
- 1000+ aggregates
- 10000+ events
- High cardinality data
- Stress test scenarios

## Using the Seed Script

### Interactive Mode

```bash
make db-scenario
# or
./infrastructure/postgres/seed-data/scripts/load-scenario.sh
```

This presents an interactive menu to select scenarios.

### Direct Invocation

```bash
./infrastructure/postgres/seed-data/scripts/seed.sh <scenario>
```

Options:
- `minimal` - Minimal test data
- `standard` - Standard scenario
- `full` - Full test data
- `performance` - Performance test data

### Script Options

```bash
# Seed with verbose output
./infrastructure/postgres/seed-data/scripts/seed.sh standard --verbose

# Seed without confirmation
./infrastructure/postgres/seed-data/scripts/seed.sh standard --yes

# Seed to specific database
./infrastructure/postgres/seed-data/scripts/seed.sh standard --database cqrs_test_db
```

## Resetting Before Seeding

### Reset Data Only

Clears data but preserves schema:

```bash
make db-reset-data
# then
make db-seed
```

### Full Reset

Drops and recreates everything:

```bash
make db-reset
# then
make db-seed
```

## Seed Data Structure

### File Organization

```
infrastructure/postgres/seed-data/
├── scenarios/
│   ├── minimal.sql       # Minimal data
│   ├── standard.sql      # Standard scenario
│   ├── full.sql          # Full test data
│   └── performance.sql   # Performance data
├── scripts/
│   ├── seed.sh           # Main seed script
│   ├── reset.sh          # Full reset
│   ├── reset-data-only.sh # Data-only reset
│   └── load-scenario.sh  # Interactive loader
└── README.md             # Seed documentation
```

### Scenario Files

Each scenario file contains SQL statements organized by schema:

```sql
-- Event Store seed data
INSERT INTO event_store.event_stream (stream_id, aggregate_type, aggregate_id, version)
VALUES (...);

INSERT INTO event_store.domain_event (event_id, stream_id, event_type, event_data, occurred_at)
VALUES (...);

-- Read Model seed data
INSERT INTO read_model.products (...)
VALUES (...);

-- Command Model seed data
INSERT INTO command_model.product_commands (...)
VALUES (...);
```

## Creating Custom Scenarios

### Create New Scenario File

1. Create file in `infrastructure/postgres/seed-data/scenarios/`:

```bash
touch infrastructure/postgres/seed-data/scenarios/custom.sql
```

2. Add seed data:

```sql
-- infrastructure/postgres/seed-data/scenarios/custom.sql

-- Event Store
INSERT INTO event_store.event_stream (stream_id, aggregate_type, aggregate_id, version)
VALUES
    ('stream-1', 'Product', 'prod-001', 1),
    ('stream-2', 'Product', 'prod-002', 1);

INSERT INTO event_store.domain_event (event_id, stream_id, event_type, event_data, occurred_at)
VALUES
    (gen_random_uuid(), 'stream-1', 'ProductCreated',
     '{"name": "Custom Product", "price": 1999}'::jsonb,
     NOW());
```

3. Load custom scenario:

```bash
./infrastructure/postgres/seed-data/scripts/seed.sh custom
```

### Scenario Guidelines

1. **Use realistic data** - Names, values, dates should be realistic
2. **Cover edge cases** - Include boundary conditions
3. **Maintain referential integrity** - Ensure foreign keys are valid
4. **Use JSONB properly** - Event data should be valid JSON
5. **Set appropriate timestamps** - Use relative dates when possible

## Verifying Seed Data

### Check Event Store

```bash
./scripts/db-query.sh "SELECT COUNT(*) as stream_count FROM event_store.event_stream"
./scripts/db-query.sh "SELECT COUNT(*) as event_count FROM event_store.domain_event"
```

### Check Read Model

```bash
./scripts/db-query.sh "SELECT COUNT(*) FROM read_model.products"
```

### View Sample Data

```bash
./scripts/db-query.sh "SELECT * FROM event_store.domain_event LIMIT 5"
```

### Verify Event Types

```bash
./scripts/db-query.sh "SELECT event_type, COUNT(*) FROM event_store.domain_event GROUP BY event_type"
```

## Troubleshooting Seeding

### Seed Fails with Constraint Violation

```bash
# Reset data first
make db-reset-data

# Try seeding again
make db-seed
```

### Foreign Key Errors

Ensure seed data maintains referential integrity:
1. Seed parent tables first
2. Then seed child tables
3. Use existing IDs in foreign keys

### Duplicate Key Errors

```bash
# Check existing data
./scripts/db-query.sh "SELECT stream_id FROM event_store.event_stream"

# Reset and reseed
make db-reset-data
make db-seed
```

### Performance Issues with Large Seeds

```bash
# Disable indexes during seed
./scripts/db-query.sh "SET session_replication_role = replica"

# Run seed
make db-seed-perf

# Re-enable indexes
./scripts/db-query.sh "SET session_replication_role = DEFAULT"

# Rebuild indexes
./scripts/db-query.sh "REINDEX DATABASE cqrs_db"
```

## Integration with Testing

### Test Setup

```kotlin
@BeforeEach
fun setUp() {
    // Reset to known state
    seedService.resetData()
    seedService.loadScenario("minimal")
}
```

### Using Seed Data in Tests

```kotlin
@Test
fun `should find seeded product`() {
    // Given: minimal seed data is loaded
    val knownProductId = UUID.fromString("known-product-id-from-seed")

    // When
    val result = productService.findById(knownProductId)

    // Then
    StepVerifier.create(result)
        .expectNextMatches { it.name == "Seeded Product" }
        .verifyComplete()
}
```

## See Also

- [Database Operations](database-operations.md) - Database management
- [Daily Workflow](daily-workflow.md) - Development workflow
- [Commands Reference](../reference/commands.md) - All seeding commands
