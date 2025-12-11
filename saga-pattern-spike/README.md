# Saga Pattern Spike

A Spring Boot 4.0 spike project exploring the **saga pattern** for distributed transactions. Built with Kotlin and reactive/coroutine support via WebFlux.

## Overview

This project demonstrates a comprehensive implementation of the saga orchestration pattern for managing distributed transactions across multiple services. The saga pattern ensures data consistency by coordinating a series of local transactions, with automatic compensation (rollback) when any step fails.

### Key Features

- **Multi-step Order Processing** - Orchestrated saga execution across inventory, payment, and shipping services
- **Automatic Rollback** - Compensation logic executes in reverse order when failures occur
- **Real-time Status Tracking** - Server-Sent Events (SSE) for live order progress updates
- **Retry Support** - Resume failed orders from the point of failure
- **Order History** - Complete timeline of saga execution with step-by-step details
- **Distributed Tracing** - End-to-end observability with OpenTelemetry and SigNoz
- **Dynamic Secrets** - HashiCorp Vault integration for secure credential management

## Architecture

```mermaid
flowchart TB
    subgraph API["Order API"]
        POST["POST /api/orders"]
        GET["GET /api/orders/{id}/status"]
        SSE["SSE stream"]
    end

    subgraph Orchestrator["Saga Orchestrator"]
        direction LR
        INV["Inventory<br/>Reservation"]
        PAY["Payment<br/>Processing"]
        SHIP["Shipping<br/>Arrangement"]

        INV -->|"success"| PAY
        PAY -->|"success"| SHIP

        INV -.->|"compensate<br/>on failure"| COMP_INV["Release Items"]
        PAY -.->|"compensate<br/>on failure"| COMP_PAY["Void Payment"]
        SHIP -.->|"compensate<br/>on failure"| COMP_SHIP["Cancel Shipment"]
    end

    subgraph Infrastructure["Infrastructure Services"]
        PG[("PostgreSQL<br/>(R2DBC)")]
        VAULT["Vault<br/>(Secrets)"]
        WIRE["WireMock<br/>(Mocks)"]
    end

    API --> Orchestrator
    Orchestrator --> PG
    Orchestrator --> VAULT
    Orchestrator --> WIRE
```

## Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 2.2 | Primary language with coroutines |
| Spring Boot | 4.0 | Application framework |
| Spring WebFlux | - | Reactive web layer |
| R2DBC PostgreSQL | - | Reactive database access |
| Spring Cloud Vault | 2025.1.0 | Secret management |
| OpenTelemetry | (native) | Distributed tracing |
| Micrometer | - | Metrics and observations |
| Cucumber | 7.20 | Acceptance testing |
| WireMock | 3.9 | External service mocking |
| Gradle | 9.2 | Build system (Kotlin DSL) |
| JVM | 24 | Amazon Corretto |

## Getting Started

### Prerequisites

- Docker and Docker Compose
- JDK 24 (recommend using SDKMAN: `sdk env`)
- Gradle 9.2+

### Quick Start

1. **Start infrastructure services:**

   ```bash
   docker compose up -d
   ```

   This starts PostgreSQL, HashiCorp Vault, and WireMock.

2. **Run the application:**

   ```bash
   ./gradlew bootRun
   ```

3. **Create a test order:**

   ```bash
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": "550e8400-e29b-41d4-a716-446655440000",
       "items": [
         {
           "productId": "550e8400-e29b-41d4-a716-446655440001",
           "productName": "Widget Pro",
           "quantity": 2,
           "unitPriceInCents": 2999
         }
       ],
       "paymentMethodId": "valid-card",
       "shippingAddress": {
         "street": "123 Main St",
         "city": "Springfield",
         "state": "IL",
         "postalCode": "62701",
         "country": "US"
       }
     }'
   ```

### With Observability Stack

To enable distributed tracing and metrics visualization:

```bash
docker compose --profile observability up -d
```

Access the SigNoz dashboard at http://localhost:3301

## API Reference

### Orders

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orders` | POST | Create and process a new order |
| `/api/orders/{id}` | GET | Get order details |
| `/api/orders/{id}/status` | GET | Get current processing status |
| `/api/orders/{id}/status/stream` | GET | SSE stream for real-time updates |
| `/api/orders/customer/{customerId}` | GET | List orders for a customer |

### Retry

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orders/{id}/retry` | POST | Retry a failed order |
| `/api/orders/{id}/retry/eligibility` | GET | Check if order can be retried |

### History

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orders/{id}/history` | GET | Get full order processing timeline |

## Saga Steps

The order processing saga consists of three sequential steps:

| Order | Step | Description | Compensation |
|-------|------|-------------|--------------|
| 1 | Inventory Reservation | Reserve items from inventory | Release reserved items |
| 2 | Payment Processing | Authorize and capture payment | Void authorization/refund |
| 3 | Shipping Arrangement | Create shipping label and schedule | Cancel shipment |

### Failure Scenarios

- **Step 1 fails**: No compensation needed (nothing to undo)
- **Step 2 fails**: Inventory reservation is released
- **Step 3 fails**: Payment is voided, then inventory is released

### Triggering WireMock Failures

WireMock is configured with special trigger values that cause specific saga steps to fail, allowing you to test compensation behavior. Use these values in your order request to simulate failures:

#### Payment Failures

| `paymentMethodId` Value | Error Response | HTTP Status |
|-------------------------|----------------|-------------|
| `valid-card` | Success | 201 |
| `declined-card` | PAYMENT_DECLINED | 402 |
| `fraud-card` | FRAUD_DETECTED | 403 |

**Example - Trigger payment declined:**

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{"productId": "550e8400-e29b-41d4-a716-446655440001", "productName": "Widget", "quantity": 1, "unitPriceInCents": 999}],
    "paymentMethodId": "declined-card",
    "shippingAddress": {"street": "123 Main St", "city": "Springfield", "state": "IL", "postalCode": "62701", "country": "US"}
  }'
```

#### Inventory Failures

| `productId` Value | Error Response | HTTP Status |
|-------------------|----------------|-------------|
| Any valid UUID | Success | 201 |
| `out-of-stock-product` | INVENTORY_UNAVAILABLE | 409 |

**Example - Trigger out of stock:**

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{"productId": "out-of-stock-product", "productName": "Sold Out Item", "quantity": 1, "unitPriceInCents": 999}],
    "paymentMethodId": "valid-card",
    "shippingAddress": {"street": "123 Main St", "city": "Springfield", "state": "IL", "postalCode": "62701", "country": "US"}
  }'
```

#### Shipping Failures

| Field | Trigger Value | Error Response | HTTP Status |
|-------|---------------|----------------|-------------|
| `postalCode` | `00000` | INVALID_ADDRESS | 400 |
| `country` | `XX` | SHIPPING_UNAVAILABLE | 422 |

**Example - Trigger invalid address:**

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{"productId": "550e8400-e29b-41d4-a716-446655440001", "productName": "Widget", "quantity": 1, "unitPriceInCents": 999}],
    "paymentMethodId": "valid-card",
    "shippingAddress": {"street": "123 Main St", "city": "Springfield", "state": "IL", "postalCode": "00000", "country": "US"}
  }'
```

**Example - Trigger undeliverable location:**

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{"productId": "550e8400-e29b-41d4-a716-446655440001", "productName": "Widget", "quantity": 1, "unitPriceInCents": 999}],
    "paymentMethodId": "valid-card",
    "shippingAddress": {"street": "123 Main St", "city": "Nowhere", "state": "ZZ", "postalCode": "12345", "country": "XX"}
  }'
```

#### Expected Compensation Behavior

| Failed Step | Compensated Steps |
|-------------|-------------------|
| Inventory (Step 1) | None |
| Payment (Step 2) | Inventory released |
| Shipping (Step 3) | Payment voided, Inventory released |

## Configuration

### Application Properties

Key configuration in `application.yaml`:

```yaml
spring:
  application:
    name: sagapattern
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/saga_db

saga:
  services:
    inventory:
      base-url: http://localhost:8081/api/inventory
    payment:
      base-url: http://localhost:8081/api/payments
    shipping:
      base-url: http://localhost:8081/api/shipments
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector endpoint | `http://localhost:4318` |
| `DEPLOYMENT_ENV` | Deployment environment tag | `development` |
| `VAULT_ROLE_ID` | Vault AppRole role ID (production) | - |
| `VAULT_SECRET_ID` | Vault AppRole secret ID (production) | - |

## Infrastructure

### Docker Services

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5432 | Order and saga persistence |
| Vault | 8200 | Secret management |
| WireMock | 8081 | Mock external services |
| SigNoz | 3301 | Observability UI (optional) |
| OTel Collector | 4317/4318 | Telemetry collection (optional) |

### Vault Integration

The application uses HashiCorp Vault for:

- **KV Secrets**: API keys, encryption keys at `secret/sagapattern/application`
- **Dynamic Database Credentials**: Auto-rotating PostgreSQL credentials with 1-hour TTL

Development mode uses a root token (`dev-root-token`). Production should use AppRole authentication.

```bash
# Check Vault status
curl http://localhost:8200/v1/sys/health

# Read KV secrets
docker exec saga-vault vault kv get secret/sagapattern/application

# Generate database credentials
docker exec saga-vault vault read database/creds/sagapattern-readwrite
```

## Testing

### Unit Tests

```bash
./gradlew test
```

### Acceptance Tests (Cucumber)

```bash
# Run all acceptance tests
./gradlew test --tests "*.CucumberTestRunner"

# Run by user story
./gradlew test -Dcucumber.filter.tags="@saga-001"  # Multi-step process
./gradlew test -Dcucumber.filter.tags="@saga-002"  # Automatic rollback
./gradlew test -Dcucumber.filter.tags="@saga-003"  # Order status
./gradlew test -Dcucumber.filter.tags="@saga-004"  # Retry functionality
./gradlew test -Dcucumber.filter.tags="@saga-005"  # Order history

# Run by scenario type
./gradlew test -Dcucumber.filter.tags="@happy-path"
./gradlew test -Dcucumber.filter.tags="@compensation"
./gradlew test -Dcucumber.filter.tags="@observability"
```

Test reports are generated at `build/reports/cucumber/cucumber-report.html`

### Integration Tests

```bash
./gradlew test -Dcucumber.filter.tags="@integration"
```

## Observability

### Distributed Tracing

Every saga execution creates a distributed trace with:

- Parent span for the complete saga
- Child spans for each step (inventory, payment, shipping)
- Compensation spans when rollback occurs
- HTTP client spans for external service calls

### Custom Metrics

| Metric | Description |
|--------|-------------|
| `saga.started` | Counter of sagas initiated |
| `saga.completed` | Counter of successful sagas |
| `saga.compensated` | Counter of compensated sagas |
| `saga.duration` | Histogram of saga execution time |
| `saga.step.duration` | Histogram of individual step times |
| `saga.step.failed` | Counter of step failures by step name |

### Viewing Traces

1. Start the observability stack: `docker compose --profile observability up -d`
2. Open SigNoz: http://localhost:3301
3. Navigate to Traces and filter by service "sagapattern"

## Project Structure

```
saga-pattern-spike/
├── src/main/kotlin/com/pintailconsultingllc/sagapattern/
│   ├── api/                    # REST controllers and DTOs
│   ├── config/                 # Spring configuration
│   ├── domain/                 # Domain entities (Order, SagaExecution)
│   ├── event/                  # Domain events and publishers
│   ├── history/                # Order history and timeline
│   ├── metrics/                # Custom saga metrics
│   ├── notification/           # Failure notifications
│   ├── progress/               # Order progress tracking
│   ├── repository/             # R2DBC repositories
│   ├── retry/                  # Retry orchestration
│   ├── saga/                   # Saga orchestrator and steps
│   │   ├── steps/              # Individual saga step implementations
│   │   └── compensation/       # Compensation handling
│   ├── service/                # External service clients
│   └── util/                   # Utilities
├── src/test/kotlin/            # Unit and integration tests
├── src/test/resources/features/ # Cucumber feature files
├── docker/                     # Docker configurations
│   ├── clickhouse/             # ClickHouse config
│   ├── otel-collector/         # OpenTelemetry collector config
│   ├── postgres/               # PostgreSQL init scripts
│   ├── vault/                  # Vault init scripts
│   └── wiremock/               # WireMock stubs
└── docs/
    ├── features/               # Feature specifications
    └── implementation-plans/   # Implementation planning docs
```

## User Stories

This spike implements the following user stories:

| ID | Story | Description |
|----|-------|-------------|
| SAGA-001 | Multi-Step Order Process | Process orders through inventory, payment, and shipping |
| SAGA-002 | Automatic Rollback | Compensate completed steps when a failure occurs |
| SAGA-003 | View Order Status | Real-time visibility into saga progress |
| SAGA-004 | Retry Failed Orders | Resume processing from the failed step |
| SAGA-005 | Order History | Complete timeline of all saga events |

## Development

### Build Commands

```bash
# Full build with tests
./gradlew build

# Compile only
./gradlew compileKotlin

# Run application
./gradlew bootRun

# Clean build
./gradlew clean build
```

### Infrastructure Commands

```bash
# Start services
docker compose up -d

# Stop services
docker compose down

# Reset all data
docker compose down -v && docker compose up -d

# View logs
docker compose logs -f [service-name]

# Check WireMock mappings
curl http://localhost:8081/__admin/mappings
```

### SDK Management

This project uses SDKMAN for Java/Gradle version management:

```bash
sdk env
```

This activates Java 24.0.2-amzn and Gradle 9.2.1.

## Documentation

- [Feature Specification](docs/features/001-basic-saga-pattern.md) - User stories and acceptance criteria
- [Infrastructure Plan](docs/implementation-plans/INFRA-001-infrastructure.md) - Docker setup details
- [Acceptance Testing](docs/implementation-plans/INFRA-002-acceptance-testing.md) - Cucumber configuration
- [Vault Integration](docs/implementation-plans/INFRA-003-vault-integration.md) - Secret management setup
- [Observability Integration](docs/implementation-plans/INFRA-004-observability-integration.md) - OpenTelemetry + SigNoz

## License

This is a spike/proof-of-concept project for exploring the saga pattern.
