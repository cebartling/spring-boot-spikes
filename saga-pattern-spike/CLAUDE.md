# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 4.0 spike project exploring the **saga pattern** for distributed transactions. Built with Kotlin and reactive/coroutine support via WebFlux.

## Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.pintailconsultingllc.sagapattern.SagapatternApplicationTests"

# Run a single test method
./gradlew test --tests "com.pintailconsultingllc.sagapattern.SagapatternApplicationTests.contextLoads"

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

## Infrastructure Commands

```bash
# Start all services (PostgreSQL, Vault, WireMock, Jaeger, Prometheus, Grafana, Loki)
docker compose up -d

# Stop services
docker compose down

# Reset database and Vault data (destroys all data)
docker compose down -v && docker compose up -d

# View logs
docker compose logs -f

# Check WireMock mappings
curl http://localhost:8081/__admin/mappings
```

## HashiCorp Vault (Secret Management)

The application uses HashiCorp Vault for centralized secret management with dynamic database credentials.

### Key Ports

| Service | Port | Purpose |
|---------|------|---------|
| Vault | 8200 | Vault API and UI |

### Vault Commands

```bash
# Check Vault status
curl http://localhost:8200/v1/sys/health

# Read KV secrets (using dev root token)
docker exec saga-vault vault kv get secret/sagapattern/application

# Generate dynamic database credentials
docker exec saga-vault vault read database/creds/sagapattern-readwrite

# Get AppRole Role ID (for production use)
docker exec saga-vault vault read auth/approle/role/sagapattern/role-id

# Generate a Secret ID
docker exec saga-vault vault write -f auth/approle/role/sagapattern/secret-id
```

### Configuration

- **Development**: Uses token authentication with `dev-root-token`
- **Production**: Uses AppRole authentication (set `VAULT_ROLE_ID` and `VAULT_SECRET_ID` env vars)
- **Testing**: Vault is disabled via `spring.cloud.vault.enabled=false`

### Secret Paths

| Path | Description |
|------|-------------|
| `secret/sagapattern/application` | Common application secrets (API keys, encryption keys) |
| `secret/sagapattern/dev` | Development profile secrets |
| `secret/sagapattern/prod` | Production profile secrets |
| `database/creds/sagapattern-readwrite` | Dynamic PostgreSQL credentials (1h TTL) |
| `database/creds/sagapattern-readonly` | Read-only PostgreSQL credentials (1h TTL) |

See `docs/implementation-plans/INFRA-003-vault-integration.md` for full implementation details.

## Acceptance Tests (Cucumber)

```bash
# Run all acceptance tests
./gradlew test --tests "*.CucumberTestRunner"

# Run by user story tag
./gradlew test -Dcucumber.filter.tags="@saga-001"
./gradlew test -Dcucumber.filter.tags="@saga-002"

# Run by scenario type
./gradlew test -Dcucumber.filter.tags="@happy-path"
./gradlew test -Dcucumber.filter.tags="@compensation"

# Run observability scenarios
./gradlew test -Dcucumber.filter.tags="@observability"

# Exclude integration tests
./gradlew test -Dcucumber.filter.tags="@saga and not @integration"
```

Reports generated at `build/reports/cucumber/cucumber-report.html`

## Tech Stack

- **Kotlin 2.2** with JVM 24 (Amazon Corretto)
- **Spring Boot 4.0** with WebFlux (reactive)
- **Gradle 9.2** (Kotlin DSL)
- **Coroutines** via kotlinx-coroutines-reactor
- **Jackson** for JSON serialization
- **PostgreSQL 17** for persistence (via Docker)
- **HashiCorp Vault 1.15** for secret management (via Docker)
- **Spring Cloud Vault** for Vault integration
- **WireMock 3.9** for external service mocks (via Docker)
- **Cucumber 7.20** for acceptance testing
- **OpenTelemetry** for distributed tracing
- **Jaeger** for trace visualization
- **Prometheus** for metrics collection
- **Loki** for log aggregation
- **Grafana** for metrics, logs, and traces visualization
- **Micrometer** for metrics and observation API

## Architecture

The project uses Spring WebFlux for non-blocking, reactive HTTP handling. Key patterns:

- Reactive streams with Project Reactor (`Mono`, `Flux`)
- Kotlin coroutines integration for cleaner async code
- WebClient for reactive HTTP client calls

## Documentation Structure

- `README.md` - Project overview, getting started, and API reference
- `docs/features/` - Feature specifications and user stories
- `docs/implementation-plans/` - Implementation planning documents
- `docs/prompts.md` - Claude Code prompt templates

## Observability (Tracing + Metrics + Logs)

The application uses Spring Boot 4.0's native OpenTelemetry support with Jaeger for distributed tracing, Prometheus/Grafana for metrics, and Loki for log aggregation.

### Key Ports

| Service | Port | Purpose |
|---------|------|---------|
| Jaeger UI | 16686 | Trace visualization |
| OTLP gRPC | 4317 | OTLP receiver (gRPC) |
| OTLP HTTP | 4318 | OTLP receiver (HTTP) |
| Prometheus | 9090 | Metrics database and query UI |
| Loki | 3100 | Log aggregation |
| Grafana | 3000 | Unified visualization (metrics, logs, traces) |

### Observability Commands

```bash
# Start all services
docker compose up -d

# Access Jaeger UI (traces)
open http://localhost:16686

# Access Grafana (metrics, logs, traces)
open http://localhost:3000  # no login required

# Access Prometheus (metrics queries)
open http://localhost:9090

# Check Loki ready status
curl http://localhost:3100/ready

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'

# Check Spring Boot metrics endpoint
curl http://localhost:8080/actuator/prometheus | head -50
```

### Configuration

- **Development**: OTLP exports to localhost:4318, Prometheus scrapes localhost:8080, Loki receives logs on localhost:3100
- **Production**: Set `OTEL_EXPORTER_OTLP_ENDPOINT` and `LOKI_URL` environment variables
- **Testing**: Tracing and Loki appender disabled via Spring profiles

### Pre-configured Grafana Dashboards

Four dashboards are auto-provisioned in Grafana:

| Dashboard | Description |
|-----------|-------------|
| JVM Metrics | Memory, GC, threads, class loading |
| Spring Boot HTTP | Request rate, latency, errors |
| Saga Pattern Metrics | Saga execution, compensation, step timing |
| Application Logs | Log volume, errors, warnings, log stream |

### Custom Metrics

The application defines custom saga metrics via Micrometer:

| Metric | Description |
|--------|-------------|
| `saga_started_total` | Number of sagas started |
| `saga_completed_total` | Number of sagas completed successfully |
| `saga_compensated_total` | Number of sagas requiring compensation |
| `saga_duration_seconds` | Time to complete saga (success or compensation) |
| `saga_step_duration_seconds` | Duration of individual saga steps |
| `saga_step_failed_total` | Step failure count by step name |

### Using @Observed Annotation

Add observability to any method using the `@Observed` annotation:

```kotlin
@Observed(name = "saga.step", contextualName = "execute-step")
suspend fun executeStep() { ... }
```

See `docs/implementation-plans/INFRA-004-observability-integration.md` for tracing details.
See `docs/implementation-plans/INFRA-006-prometheus-grafana.md` for metrics details.
See `docs/implementation-plans/INFRA-007-loki-log-aggregation.md` for log aggregation details.

## Load Testing (k6)

The project includes k6 load testing scripts for performance validation.

### Key Ports

| Service | Port | Purpose |
|---------|------|---------|
| Pushgateway | 9091 | k6 metrics collection |

### Load Testing Commands

```bash
# Run smoke test (1-2 VUs, 1 min)
make load-test-smoke

# Run load test (10-50 VUs, 5 min)
make load-test-load

# Run stress test (100-200 VUs, 10 min)
make load-test-stress

# Run soak test (30 VUs, 30 min)
make load-test-soak

# Run with Docker (no k6 install required)
make load-test-docker

# View all available make targets
make help
```

### Test Scenarios

| Scenario | VUs | Duration | Purpose |
|----------|-----|----------|---------|
| Smoke | 1-2 | 1 min | Quick validation |
| Load | 10-50 | 5 min | Normal production load |
| Stress | 100-200 | 10 min | Find breaking points |
| Soak | 30 | 30 min | Detect memory leaks |

### Grafana Dashboard

The "k6 Load Testing" dashboard is auto-provisioned in Grafana showing:
- Active VUs and request rate
- Response time percentiles (p50, p90, p95, p99)
- Error rate over time
- Request rate by endpoint
- Custom saga metrics

**Direct URL**: http://localhost:3000/d/k6-load-testing/k6-load-testing?orgId=1&refresh=5s&from=now-5m&to=now

See `docs/implementation-plans/LOAD-001-k6-load-testing.md` for full implementation details.
See `load-tests/README.md` for detailed usage guide.

## SDK Management

Uses SDKMAN for Java/Gradle version management. Run `sdk env` to activate configured versions (Java 24.0.2-amzn, Gradle 9.2.1).
