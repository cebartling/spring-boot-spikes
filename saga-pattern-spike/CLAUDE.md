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
# Start all services (PostgreSQL, Vault, WireMock, Jaeger, Prometheus, Grafana)
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
- **Grafana** for metrics visualization
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

## Observability (Tracing + Metrics)

The application uses Spring Boot 4.0's native OpenTelemetry support with Jaeger for distributed tracing and Prometheus/Grafana for metrics.

### Key Ports

| Service | Port | Purpose |
|---------|------|---------|
| Jaeger UI | 16686 | Trace visualization |
| OTLP gRPC | 4317 | OTLP receiver (gRPC) |
| OTLP HTTP | 4318 | OTLP receiver (HTTP) |
| Prometheus | 9090 | Metrics database and query UI |
| Grafana | 3000 | Metrics visualization dashboards |

### Observability Commands

```bash
# Start all services
docker compose up -d

# Access Jaeger UI (traces)
open http://localhost:16686

# Access Grafana (metrics)
open http://localhost:3000  # admin/admin

# Access Prometheus (metrics queries)
open http://localhost:9090

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'

# Check Spring Boot metrics endpoint
curl http://localhost:8080/actuator/prometheus | head -50
```

### Configuration

- **Development**: OTLP exports to localhost:4318, Prometheus scrapes localhost:8080
- **Production**: Set `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable
- **Testing**: Tracing disabled via `management.tracing.enabled=false`

### Pre-configured Grafana Dashboards

Three dashboards are auto-provisioned in Grafana:

| Dashboard | Description |
|-----------|-------------|
| JVM Metrics | Memory, GC, threads, class loading |
| Spring Boot HTTP | Request rate, latency, errors |
| Saga Pattern Metrics | Saga execution, compensation, step timing |

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

## SDK Management

Uses SDKMAN for Java/Gradle version management. Run `sdk env` to activate configured versions (Java 24.0.2-amzn, Gradle 9.2.1).
