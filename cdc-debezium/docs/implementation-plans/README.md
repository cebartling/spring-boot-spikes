# Implementation Plans

This directory contains detailed implementation plans for the CDC spike (FEATURE-001).

## Plan Overview

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

## Dependency Graph

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

## Recommended Implementation Order

1. **PLAN-001** + **PLAN-009** (can be done in parallel)
2. **PLAN-002**
3. **PLAN-003**
4. **PLAN-004**
5. **PLAN-005**
6. **PLAN-006**
7. **PLAN-007** + **PLAN-008** (can be done in parallel)
8. **PLAN-010**

## Quick Start

```bash
# Start with infrastructure
docker compose up -d postgres kafka

# Then add observability
docker compose up -d otel-collector jaeger prometheus

# Then add Kafka Connect
docker compose up -d kafka-connect

# Deploy Debezium connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium/connector-config.json

# Run the Spring Boot application
./gradlew bootRun
```

## Validation

After implementing all plans, the following should work:

1. **CDC Pipeline**: Changes to `customer` table appear in `customer_materialized`
2. **Observability**: Traces in Jaeger (http://localhost:16686)
3. **Metrics**: Metrics in Prometheus (http://localhost:9090)
4. **Logs**: Structured JSON logs with trace correlation
5. **Resilience**: System recovers from component restarts
