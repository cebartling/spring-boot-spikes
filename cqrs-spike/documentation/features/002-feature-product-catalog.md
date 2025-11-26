# Feature: Product Catalog (CQRS Architecture)

## Overview

As a business, we need a product catalog system that allows administrators to manage products while providing fast, scalable read access for customers. This system will be implemented using Command Query Responsibility Segregation (CQRS) with Event Sourcing, separating write operations (commands) from read operations (queries) to optimize each independently.

## Architecture Principles

### CQRS Separation
- **Command Side**: Handles all write operations (create, update, delete) through domain aggregates and events
- **Query Side**: Optimized read models for fast retrieval, built from event projections
- **Event Store**: Immutable log of all domain events for audit, replay, and projection rebuilding

### Event Sourcing
- All state changes are captured as domain events
- Current state is derived by replaying events
- Events are immutable and append-only
- Supports temporal queries and audit trails

## Scope

### Phase 1: Core Product Catalog
- Product aggregate with basic attributes
- Command handlers for CRUD operations
- Event projections for read models
- Basic search and filtering

### Phase 2: Enhanced Features (Future)
- Product categories and hierarchies
- Product variants (size, color, etc.)
- Inventory integration
- Pricing rules and promotions

## Domain Model

### Product Aggregate
- Product ID (UUID)
- SKU (Stock Keeping Unit)
- Name
- Description
- Price (stored in cents)
- Status (DRAFT, ACTIVE, DISCONTINUED)
- Created/Updated timestamps
- Version (for optimistic concurrency)

### Domain Events
- `ProductCreated`
- `ProductUpdated`
- `ProductPriceChanged`
- `ProductActivated`
- `ProductDiscontinued`
- `ProductDeleted`

## Acceptance Criteria

### AC1: Product Command Model

**Implementation Plan:** [AC1 - Product Command Model](../plans/product-catalog/AC1-product-command-model.md)

- A `Product` aggregate root exists in the `command_model` schema
- The aggregate enforces business invariants (e.g., price must be positive, SKU must be unique)
- The aggregate generates domain events for all state changes
- Optimistic concurrency is implemented using version numbers
- The aggregate can be reconstituted from its event stream
- All command operations return `Mono<T>` for reactive compatibility

### AC2: Product Event Store

**Implementation Plan:** [AC2 - Product Event Store](../plans/product-catalog/AC2-product-event-store.md)

- Domain events are persisted to the `event_store` schema
- Events are stored with metadata (event ID, stream ID, event type, timestamp, version)
- Event data is stored as JSONB for flexibility
- Events are immutable once written
- Event streams can be read by aggregate ID
- Events can be queried by type and time range
- Event versioning supports schema evolution

### AC3: Command Handlers

**Implementation Plan:** [AC3 - Command Handlers](../plans/product-catalog/AC3-command-handlers.md)

- `CreateProductCommand` creates a new product and emits `ProductCreated` event
- `UpdateProductCommand` updates product details and emits `ProductUpdated` event
- `ChangePriceCommand` updates price and emits `ProductPriceChanged` event
- `ActivateProductCommand` transitions product to ACTIVE status
- `DiscontinueProductCommand` transitions product to DISCONTINUED status
- `DeleteProductCommand` soft-deletes the product and emits `ProductDeleted` event
- All commands are validated before processing
- Commands return appropriate error responses for invalid operations
- Commands implement idempotency where appropriate

### AC4: Product Read Model

**Implementation Plan:** [AC4 - Product Read Model](../plans/product-catalog/AC4-product-read-model.md)

- A denormalized read model exists in the `read_model` schema optimized for queries
- The read model includes all fields needed for product listing and detail views
- The read model is updated asynchronously from domain events
- Read model queries return `Flux<T>` or `Mono<T>` for reactive streaming
- The read model supports pagination with cursor-based navigation
- Read model updates are idempotent (safe to replay)

### AC5: Event Projections

**Implementation Plan:** [AC5 - Event Projections](../plans/product-catalog/AC5-event-projections.md)

- Event projectors listen to domain events and update read models
- Projections track their position in the event stream
- Projections can be rebuilt from scratch by replaying all events
- Projection errors are logged and can trigger alerts
- Multiple projections can be maintained for different query needs
- Projections handle out-of-order events gracefully

### AC6: Product Query Service

**Implementation Plan:** [AC6 - Product Query Service](../plans/product-catalog/AC6-product-query-service.md)

- Query endpoint returns a single product by ID
- Query endpoint returns products with pagination (offset and cursor-based)
- Query endpoint supports filtering by status (DRAFT, ACTIVE, DISCONTINUED)
- Query endpoint supports filtering by price range
- Query endpoint supports text search on name and description
- Query endpoint supports sorting by name, price, or created date
- All query endpoints return appropriate HTTP status codes (200, 404, 400)

### AC7: Product REST API (Commands)

**Implementation Plan:** [AC7 - Product REST API Commands](../plans/product-catalog/AC7-product-rest-api-commands.md)

- `POST /api/products` creates a new product
- `PUT /api/products/{id}` updates an existing product
- `PATCH /api/products/{id}/price` updates product price
- `POST /api/products/{id}/activate` activates a product
- `POST /api/products/{id}/discontinue` discontinues a product
- `DELETE /api/products/{id}` soft-deletes a product
- All endpoints use DTOs for request/response (never expose domain entities)
- All endpoints include OpenAPI/Swagger documentation
- All endpoints return appropriate HTTP status codes (201, 200, 204, 400, 404, 409)
- Validation errors return structured error responses

### AC8: Product REST API (Queries)

**Implementation Plan:** [AC8 - Product REST API Queries](../plans/product-catalog/AC8-product-rest-api-queries.md)

- `GET /api/products/{id}` returns a single product
- `GET /api/products` returns paginated product list
- `GET /api/products/search` supports full-text search
- `GET /api/products/by-status/{status}` returns products filtered by status
- Query parameters support: `page`, `size`, `sort`, `direction`
- Response includes pagination metadata (total count, page info, links)
- Endpoints support content negotiation (JSON)
- Cache headers are set appropriately for read endpoints

### AC9: Business Rules and Validation

**Implementation Plan:** [AC9 - Business Rules and Validation](../plans/product-catalog/AC9-business-rules-validation.md)

- Product name is required and between 1-255 characters
- Product SKU is required, unique, and follows defined format (alphanumeric, 3-50 chars)
- Product price must be a positive integer (cents)
- Product description is optional but limited to 5000 characters
- Products in DRAFT status can be freely edited
- Products in ACTIVE status require confirmation for price changes over 20%
- Products in DISCONTINUED status cannot be reactivated
- Deleted products are soft-deleted and excluded from queries by default

### AC10: Resiliency and Error Handling

**Implementation Plan:** [AC10 - Resiliency and Error Handling](../plans/product-catalog/AC10-resiliency-error-handling.md)

- Circuit breaker pattern protects database operations
- Retry logic handles transient failures
- Rate limiting prevents abuse of command endpoints
- Fallback methods provide graceful degradation
- All errors are logged with correlation IDs
- Domain exceptions are translated to appropriate HTTP responses
- Concurrent modification conflicts return HTTP 409 with retry guidance

### AC11: Observability

**Implementation Plan:** [AC11 - Observability](../plans/product-catalog/AC11-observability.md)

- All command operations emit trace spans
- All query operations emit trace spans
- Event publication and consumption are traced
- Custom metrics track command success/failure rates
- Custom metrics track query latency percentiles
- Custom metrics track event processing lag
- Logs include correlation IDs for request tracing
- Dashboard exists for monitoring product catalog health

### AC12: Testing

**Implementation Plan:** [AC12 - Testing](../plans/product-catalog/AC12-testing.md)

- Unit tests exist for all command handlers using `StepVerifier`
- Unit tests exist for all query handlers using `StepVerifier`
- Unit tests verify aggregate business rules
- Integration tests verify event store persistence
- Integration tests verify projection updates
- Controller tests use `@WebFluxTest` and `WebTestClient`
- Test coverage meets minimum threshold (80% line coverage)
- Test data builders exist for domain objects

## Future Acceptance Criteria (Phase 2)

### AC13: Product Categories
- Products can be assigned to one or more categories
- Categories support hierarchical structure (parent/child)
- Query endpoints support filtering by category
- Category changes emit appropriate domain events

### AC14: Product Variants
- Products can have variants (e.g., size, color combinations)
- Each variant has its own SKU and price
- Variants share parent product attributes
- Variant-specific events are captured

### AC15: Inventory Integration
- Product read model includes inventory availability
- Inventory changes trigger read model updates
- Low stock alerts are supported
- Out-of-stock products can be flagged in queries

### AC16: Pricing Rules
- Time-based pricing (sale periods) is supported
- Customer segment pricing is supported
- Bulk pricing tiers are supported
- Price history is queryable from event store

## Definition of Done

- [ ] All Phase 1 acceptance criteria are met
- [ ] Command model correctly enforces business invariants
- [ ] Event store reliably persists all domain events
- [ ] Read models are consistently updated from events
- [ ] All REST endpoints are functional and documented
- [ ] Resiliency patterns are implemented and tested
- [ ] Observability is configured and dashboards are available
- [ ] All tests pass with minimum coverage threshold
- [ ] Code review completed by at least one team member
- [ ] API documentation is complete and accurate
- [ ] Performance testing validates acceptable response times (<100ms for queries, <500ms for commands)

## Technical Notes

### Database Schemas

```sql
-- Event Store Schema
CREATE SCHEMA IF NOT EXISTS event_store;

CREATE TABLE event_store.event_stream (
    stream_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(aggregate_type, aggregate_id)
);

CREATE TABLE event_store.domain_event (
    event_id UUID PRIMARY KEY,
    stream_id UUID REFERENCES event_store.event_stream(stream_id),
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ DEFAULT NOW()
);

-- Read Model Schema
CREATE SCHEMA IF NOT EXISTS read_model;

CREATE TABLE read_model.product (
    id UUID PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_product_status ON read_model.product(status) WHERE NOT is_deleted;
CREATE INDEX idx_product_name ON read_model.product(name) WHERE NOT is_deleted;
CREATE INDEX idx_product_price ON read_model.product(price_cents) WHERE NOT is_deleted;
```

### Key Dependencies

- Spring WebFlux for reactive REST endpoints
- R2DBC for reactive database access
- Jackson for JSON serialization of events
- Resilience4j for circuit breaker, retry, rate limiting
- Micrometer for metrics
- OpenTelemetry for distributed tracing

### Package Structure

```
com.example.cqrsspike.product/
   command/
      aggregate/
         ProductAggregate.kt
      handler/
         ProductCommandHandler.kt
      model/
          Commands.kt
   query/
      handler/
         ProductQueryHandler.kt
      model/
         ProductReadModel.kt
      projection/
          ProductProjection.kt
   event/
      ProductCreated.kt
      ProductUpdated.kt
      ...
   api/
      ProductCommandController.kt
      ProductQueryController.kt
      dto/
          CreateProductRequest.kt
          ProductResponse.kt
          ...
   infrastructure/
       EventStoreRepository.kt
       ProductReadModelRepository.kt
```
