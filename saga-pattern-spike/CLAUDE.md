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
# Start all services (PostgreSQL, Vault, WireMock)
docker compose up -d

# Stop services
docker compose down

# Reset database and Vault (destroys data)
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
- **OpenTelemetry** for distributed tracing and metrics (planned)
- **SigNoz** for observability backend (planned)

## Architecture

The project uses Spring WebFlux for non-blocking, reactive HTTP handling. Key patterns:

- Reactive streams with Project Reactor (`Mono`, `Flux`)
- Kotlin coroutines integration for cleaner async code
- WebClient for reactive HTTP client calls

## Documentation Structure

- `docs/features/` - Feature specifications
- `docs/implementation-plans/` - Implementation planning documents

## Observability (Planned)

The application will use OpenTelemetry with SigNoz for comprehensive observability:

### Telemetry Signals

- **Traces** - Distributed tracing across saga steps and external service calls
- **Metrics** - Saga execution metrics (duration, step latency, compensation rate)
- **Logs** - Correlated logs with trace context for debugging

### Key Ports (when observability stack is running)

| Service | Port | Purpose |
|---------|------|---------|
| SigNoz Frontend | 3301 | Observability UI |
| OTel Collector (gRPC) | 4317 | OTLP receiver |
| OTel Collector (HTTP) | 4318 | OTLP receiver |

### Observability Commands

```bash
# Start full stack including SigNoz (after implementation)
docker compose --profile observability up -d

# Access SigNoz dashboard
open http://localhost:3301

# Check OTel Collector health
curl http://localhost:13133/

# Query traces by service
# Navigate to SigNoz UI > Traces > Filter by service "sagapattern"
```

### Configuration

- `otel.sdk.disabled=true` - Disable OpenTelemetry (for tests)
- `management.tracing.enabled=false` - Disable tracing
- See `docs/implementation-plans/INFRA-004-observability-integration.md` for full implementation details

## SDK Management

Uses SDKMAN for Java/Gradle version management. Run `sdk env` to activate configured versions (Java 24.0.2-amzn, Gradle 9.2.1).
