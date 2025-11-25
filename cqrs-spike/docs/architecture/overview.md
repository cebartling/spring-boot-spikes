# Architecture Overview

This document provides a high-level overview of the CQRS Spike application architecture.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Applications                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring WebFlux (Reactive)                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ Command Handler │  │ Query Handler   │  │ Event Handler   │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
└───────────┼─────────────────────┼─────────────────────┼─────────┘
            │                     │                     │
            ▼                     ▼                     ▼
┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
│   Command Model   │  │    Read Model     │  │    Event Store    │
│   (Write Side)    │  │   (Query Side)    │  │  (Event Sourcing) │
└─────────┬─────────┘  └─────────┬─────────┘  └─────────┬─────────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │    PostgreSQL 18        │
                    │  ┌───────┐ ┌───────┐    │
                    │  │Events │ │Views  │    │
                    │  └───────┘ └───────┘    │
                    └─────────────────────────┘
```

## Core Patterns

### CQRS (Command Query Responsibility Segregation)

The application separates read and write operations:

**Command Side (Writes):**
- Validates business rules
- Produces domain events
- Stores events in event store
- Optimized for write consistency

**Query Side (Reads):**
- Reads from denormalized projections
- Optimized for query performance
- Eventually consistent with command side

### Event Sourcing

All state changes are captured as immutable events:

1. **Commands** trigger business operations
2. **Domain Events** record what happened
3. **Event Store** persists all events
4. **Projections** build read models from events

Benefits:
- Complete audit trail
- Temporal queries
- Event replay
- Debugging via event history

## Technology Stack

### Application Layer

| Technology | Purpose | Why |
|------------|---------|-----|
| Kotlin 2.2.21 | Language | Modern, concise, null-safe |
| Spring Boot 4.0 | Framework | Production-ready, extensive ecosystem |
| Spring WebFlux | Web Layer | Reactive, non-blocking |
| R2DBC | Data Access | Reactive database connectivity |
| Project Reactor | Reactive | Mono/Flux for async operations |

### Infrastructure Layer

| Technology | Purpose | Why |
|------------|---------|-----|
| PostgreSQL 18 | Database | Robust, JSONB support for events |
| HashiCorp Vault | Secrets | Secure credential management |
| Docker | Containers | Consistent environments |
| Flyway | Migrations | Version-controlled schema |

### Resilience Layer

| Technology | Purpose |
|------------|---------|
| Resilience4j | Circuit breakers, retries, rate limiting |
| OpenTelemetry | Distributed tracing |

## Schema Design

### Event Store Schema

```sql
-- Event stream metadata
CREATE TABLE event_store.event_stream (
    stream_id       UUID PRIMARY KEY,
    aggregate_type  VARCHAR(255) NOT NULL,
    aggregate_id    UUID NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Domain events
CREATE TABLE event_store.domain_event (
    event_id        UUID PRIMARY KEY,
    stream_id       UUID REFERENCES event_store.event_stream(stream_id),
    event_type      VARCHAR(255) NOT NULL,
    event_data      JSONB NOT NULL,
    metadata        JSONB,
    version         INTEGER NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL
);
```

### Read Model Schema

Denormalized projections optimized for queries:

```sql
-- Example: Product view
CREATE TABLE read_model.products (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price_cents     INTEGER NOT NULL,
    status          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
```

### Command Model Schema

Current state for command validation:

```sql
-- Example: Product state for commands
CREATE TABLE command_model.products (
    id              UUID PRIMARY KEY,
    version         INTEGER NOT NULL,
    status          VARCHAR(50) NOT NULL
);
```

## Data Flow

### Command Flow

```
1. HTTP Request (Command)
       │
       ▼
2. CommandController
       │
       ▼
3. CommandService (Validation)
       │
       ▼
4. Domain Logic (Business Rules)
       │
       ▼
5. Event Creation
       │
       ▼
6. Event Store (Persist)
       │
       ▼
7. Event Publisher (Async)
       │
       ▼
8. Projection Updates (Read Model)
```

### Query Flow

```
1. HTTP Request (Query)
       │
       ▼
2. QueryController
       │
       ▼
3. QueryService
       │
       ▼
4. Read Model Repository
       │
       ▼
5. PostgreSQL (Read Schema)
       │
       ▼
6. HTTP Response
```

## Service Architecture

### Package Structure

```
com.example.cqrs/
├── config/           # Spring configuration
│   ├── R2dbcConfig.kt
│   ├── VaultConfig.kt
│   └── ResilienceConfig.kt
├── controller/       # REST endpoints
│   ├── CommandController.kt
│   └── QueryController.kt
├── domain/           # Domain models
│   ├── aggregate/
│   ├── event/
│   └── command/
├── dto/              # Data Transfer Objects
├── exception/        # Custom exceptions
├── infrastructure/   # External integrations
│   ├── database/
│   └── vault/
├── repository/       # Data access
│   ├── EventRepository.kt
│   └── ProjectionRepository.kt
└── service/          # Business logic
    ├── CommandService.kt
    ├── QueryService.kt
    └── ProjectionService.kt
```

### Layer Responsibilities

**Controller Layer:**
- HTTP endpoint handling
- Request/response mapping
- Input validation
- Authentication/Authorization

**Service Layer:**
- Business logic
- Transaction management
- Event publishing
- Cross-cutting concerns

**Repository Layer:**
- Data access abstraction
- R2DBC operations
- Query construction

**Domain Layer:**
- Business rules
- Domain events
- Aggregate roots
- Value objects

## Reactive Model

### Non-Blocking Operations

All I/O operations are non-blocking:

```kotlin
// Reactive repository
interface ProductRepository : ReactiveCrudRepository<Product, UUID>

// Reactive service
@Service
class ProductService(private val repo: ProductRepository) {
    fun findById(id: UUID): Mono<Product> = repo.findById(id)
    fun findAll(): Flux<Product> = repo.findAll()
}

// Reactive controller
@RestController
class ProductController(private val service: ProductService) {
    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): Mono<Product> = service.findById(id)
}
```

### Backpressure Handling

```kotlin
fun processLargeDataset(): Flux<Result> {
    return eventRepository.findAll()
        .buffer(100)  // Process in batches
        .flatMap { batch ->
            processService.processBatch(batch)
        }
        .onBackpressureBuffer(1000)  // Buffer up to 1000
}
```

## Security Model

### Secrets Management

```
┌─────────────────┐
│  Application    │
│  (Spring Boot)  │
└────────┬────────┘
         │
         ▼ (Spring Cloud Vault)
┌─────────────────┐
│  HashiCorp      │
│  Vault          │
└────────┬────────┘
         │
         ▼ (KV Secrets Engine)
┌─────────────────┐
│  Secrets        │
│  - Database     │
│  - API Keys     │
│  - Encryption   │
└─────────────────┘
```

### Authentication Flow

1. Application starts
2. Connects to Vault with token
3. Retrieves secrets
4. Injects into Spring properties
5. Uses for database/API connections

## Scalability Considerations

### Horizontal Scaling

- Stateless application design
- Database connection pooling
- Event store supports multiple writers
- Read models can be replicated

### Performance Optimization

- Read model denormalization
- Database indexes
- Connection pooling (HikariCP)
- Reactive non-blocking I/O

## See Also

- [Infrastructure Components](infrastructure-components.md) - Service details
- [Networking](networking.md) - Network configuration
- [Security](security.md) - Security model
- [CONSTITUTION.md](../../documentation/CONSTITUTION.md) - Coding standards
