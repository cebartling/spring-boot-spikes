# Implementation Plans

This directory contains detailed implementation plans for the CDC spike features.

## FEATURE-001: CDC Spike (Completed)

The foundational CDC implementation with PostgreSQL, Kafka, and OpenTelemetry observability.

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-001](PLAN-001-docker-compose-infrastructure.md) | Docker Compose Base Infrastructure | None | Low |
| [PLAN-002](PLAN-002-postgresql-schema.md) | PostgreSQL Schema and Seed Data | PLAN-001 | Low |
| [PLAN-003](PLAN-003-debezium-connector.md) | Debezium Connector Configuration | PLAN-001, PLAN-002 | Medium |
| [PLAN-004](PLAN-004-spring-kafka-consumer.md) | Spring Boot Kafka Consumer Foundation | PLAN-001, PLAN-003 | Medium |
| [PLAN-005](PLAN-005-idempotent-processing.md) | Idempotent Upsert/Delete Processing | PLAN-004 | Medium |
| [PLAN-006](PLAN-006-opentelemetry-tracing.md) | OpenTelemetry Tracing Integration | PLAN-004, PLAN-009 | Medium-High |
| [PLAN-007](PLAN-007-opentelemetry-metrics.md) | OpenTelemetry Metrics Integration | PLAN-006, PLAN-009 | Medium |
| [PLAN-008](PLAN-008-structured-logging.md) | Structured Logging with Trace Correlation | PLAN-006 | Low-Medium |
| [PLAN-009](PLAN-009-observability-infrastructure.md) | Observability Infrastructure | PLAN-001 | Low-Medium |
| [PLAN-010](PLAN-010-failure-recovery-testing.md) | Failure and Recovery Testing | All | Medium |

## FEATURE-002: Enhancement Suite

Comprehensive enhancements including MongoDB migration, extended schema support, Grafana LGTM observability, and k6 load testing.

### MongoDB Migration (2.1-2.2)

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-011](PLAN-011-mongodb-infrastructure.md) | MongoDB Infrastructure Setup | PLAN-001 | Low |
| [PLAN-012](PLAN-012-mongodb-spring-configuration.md) | MongoDB Spring Data Configuration | PLAN-011 | Medium |
| [PLAN-013](PLAN-013-mongodb-consumer-migration.md) | MongoDB Consumer Service Migration | PLAN-012, PLAN-005 | Medium |

### Data Validation (2.2)

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-014](PLAN-014-data-validation-framework.md) | Data Validation Framework | PLAN-013 | Medium |

### Extended Schema (2.3)

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-015](PLAN-015-extended-schema-address.md) | Extended Schema - Address Entity | PLAN-013, PLAN-003 | Medium |
| [PLAN-016](PLAN-016-extended-schema-orders.md) | Extended Schema - Order Entities | PLAN-015 | Medium |
| [PLAN-017](PLAN-017-multi-table-consumer.md) | Multi-Table CDC Consumer Architecture | PLAN-016 | Medium-High |

### Schema Change Handling (2.4)

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-018](PLAN-018-schema-change-handling.md) | Schema Change Handling | PLAN-017, PLAN-014 | Medium |

### Grafana LGTM Observability (2.5-2.6)

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-019](PLAN-019-grafana-lgtm-infrastructure.md) | Grafana LGTM Infrastructure | PLAN-009 | Medium |
| [PLAN-020](PLAN-020-grafana-dashboards.md) | Grafana Dashboards | PLAN-019 | Medium |
| [PLAN-021](PLAN-021-grafana-alerting.md) | Grafana Alerting | PLAN-019, PLAN-020 | Medium |

### k6 Load Testing (2.7)

| Plan | Name | Dependencies | Complexity |
|------|------|--------------|------------|
| [PLAN-022](PLAN-022-k6-load-testing-infrastructure.md) | k6 Load Testing Infrastructure | PLAN-011, PLAN-019 | Medium-High |
| [PLAN-023](PLAN-023-k6-load-test-scenarios.md) | k6 Load Test Scenarios | PLAN-022 | High |
| [PLAN-024](PLAN-024-k6-reporting-cicd.md) | k6 Reporting and CI/CD Integration | PLAN-022, PLAN-023 | High |
| [PLAN-025](PLAN-025-chaos-engineering.md) | Chaos Engineering Integration | PLAN-022, PLAN-023 | High |

## Dependency Graph

### FEATURE-001 Dependencies

```
PLAN-001 (Docker Infrastructure)
    │
    ├── PLAN-002 (PostgreSQL Schema)
    │       │
    │       └── PLAN-003 (Debezium Connector)
    │               │
    │               └── PLAN-004 (Kafka Consumer)
    │                       │
    │                       └── PLAN-005 (Idempotent Processing)
    │                               │
    │                               └── PLAN-006 (Tracing)
    │                                       │
    │                                       ├── PLAN-007 (Metrics)
    │                                       │
    │                                       └── PLAN-008 (Logging)
    │
    └── PLAN-009 (Observability Infrastructure) ───┐
                                                   │
                                                   └── PLAN-010 (Testing)
```

### FEATURE-002 Dependencies

```
PLAN-001 (Docker Infrastructure)
    │
    ├── PLAN-011 (MongoDB Infrastructure)
    │       │
    │       └── PLAN-012 (MongoDB Spring Config)
    │               │
    │               └── PLAN-013 (Consumer Migration)
    │                       │
    │                       ├── PLAN-014 (Data Validation)
    │                       │
    │                       └── PLAN-015 (Address Entity)
    │                               │
    │                               └── PLAN-016 (Order Entities)
    │                                       │
    │                                       └── PLAN-017 (Multi-Table Consumer)
    │                                               │
    │                                               └── PLAN-018 (Schema Change Handling)
    │
    └── PLAN-009 (Observability Infrastructure)
            │
            └── PLAN-019 (Grafana LGTM)
                    │
                    ├── PLAN-020 (Dashboards)
                    │       │
                    │       └── PLAN-021 (Alerting)
                    │
                    └── PLAN-022 (k6 Infrastructure)
                            │
                            └── PLAN-023 (Load Test Scenarios)
                                    │
                                    ├── PLAN-024 (Reporting & CI/CD)
                                    │
                                    └── PLAN-025 (Chaos Engineering)
```

## Recommended Implementation Order

### Phase 1: Foundation (FEATURE-001)
1. **PLAN-001** + **PLAN-009** (parallel)
2. **PLAN-002**
3. **PLAN-003**
4. **PLAN-004**
5. **PLAN-005**
6. **PLAN-006**
7. **PLAN-007** + **PLAN-008** (parallel)
8. **PLAN-010**

### Phase 2: MongoDB Migration
1. **PLAN-011**
2. **PLAN-012**
3. **PLAN-013**
4. **PLAN-014**

### Phase 3: Extended Schema
1. **PLAN-015**
2. **PLAN-016**
3. **PLAN-017**
4. **PLAN-018**

### Phase 4: Observability Enhancement
1. **PLAN-019**
2. **PLAN-020** + **PLAN-021** (parallel)

### Phase 5: Load Testing
1. **PLAN-022**
2. **PLAN-023**
3. **PLAN-024** + **PLAN-025** (parallel)

## Quick Start

```bash
# Start with infrastructure (FEATURE-001)
docker compose up -d postgres kafka

# Add observability
docker compose up -d otel-collector jaeger prometheus

# Add Kafka Connect
docker compose up -d kafka-connect

# Deploy Debezium connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium/connector-config.json

# Run the Spring Boot application
./gradlew bootRun
```

### FEATURE-002 Quick Start

```bash
# Add MongoDB (after FEATURE-001 is working)
docker compose up -d mongodb

# Add Grafana LGTM stack
docker compose up -d grafana tempo loki

# Build k6 for load testing
docker compose -f k6/docker-compose.k6.yml build k6

# Run baseline load test
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/baseline-test.js
```

## Validation

### FEATURE-001 Validation
After implementing PLAN-001 through PLAN-010:
1. **CDC Pipeline**: Changes to `customer` table appear in `customer_materialized`
2. **Observability**: Traces in Jaeger (http://localhost:16686)
3. **Metrics**: Metrics in Prometheus (http://localhost:9090)
4. **Logs**: Structured JSON logs with trace correlation
5. **Resilience**: System recovers from component restarts

### FEATURE-002 Validation
After implementing PLAN-011 through PLAN-025:
1. **MongoDB**: Customer data materializes in MongoDB collections
2. **Multi-Entity**: Customer, Address, and Order entities all replicate
3. **Validation**: Invalid data is caught and logged with metrics
4. **Schema Changes**: New fields are detected and tracked
5. **Grafana**: Full observability with LGTM stack dashboards
6. **Alerting**: Critical issues trigger alerts
7. **Load Testing**: k6 tests run in CI/CD with performance reports
8. **Chaos**: System recovers gracefully from injected failures

## Technology Stack Summary

| Component | FEATURE-001 | FEATURE-002 |
|-----------|-------------|-------------|
| Source Database | PostgreSQL 18 | PostgreSQL 18 |
| Target Database | PostgreSQL (R2DBC) | MongoDB 7.0 |
| Event Streaming | Kafka (KRaft) | Kafka (KRaft) |
| CDC | Debezium 3.4 | Debezium 3.4 |
| Observability | Jaeger + Prometheus | Grafana LGTM |
| Load Testing | - | k6 with xk6-sql/mongo |
| Chaos Testing | - | Pumba |
