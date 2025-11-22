# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot spike project exploring resiliency patterns, built with Kotlin and Spring WebFlux. The project demonstrates reactive programming patterns with Resilience4j circuit breakers, Apache Pulsar integration, and Spring Boot Actuator monitoring.

**Tech Stack:**
- Kotlin 1.9.25
- Spring Boot 3.5.7
- Spring WebFlux (reactive web)
- Spring Cloud Circuit Breaker with Resilience4j
- Apache Pulsar (reactive)
- Spring Boot Actuator
- OpenTelemetry with Jaeger (distributed tracing)
- Java 21
- Gradle (Kotlin DSL)

## Common Commands

### Build and Run
```bash
# Run the application (local development)
./gradlew bootRun

# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Build Docker image (Spring Boot buildpack)
./gradlew bootBuildImage

# Build custom container image
docker build -f Containerfile -t resiliency-spike:latest .

# Run fully containerized (app + all services)
docker-compose --profile app up -d --build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests with test runtime classpath
./gradlew bootTestRun

# Run a single test class
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.ResiliencySpikeApplicationTests"

# Run a specific test method
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.ResiliencySpikeApplicationTests.contextLoads"
```

### Development
```bash
# Compile Kotlin code
./gradlew compileKotlin

# Compile test code
./gradlew compileTestKotlin

# Run all checks (tests + other verification tasks)
./gradlew check

# View all tasks
./gradlew tasks

# View dependencies
./gradlew dependencies
```

### Docker Compose (Local Development Infrastructure Only)
```bash
# Start infrastructure services (Pulsar + PostgreSQL + Vault)
# Note: This does NOT include the Spring Boot application
docker-compose --profile infra up -d

# View logs from all services
docker-compose --profile infra logs -f

# View logs from specific service
docker-compose --profile infra logs -f pulsar
docker-compose --profile infra logs -f postgres
docker-compose --profile infra logs -f vault

# Check service health status
docker-compose --profile infra ps

# Stop all services
docker-compose --profile infra down

# Stop and remove volumes (clean slate)
docker-compose --profile infra down -v

# Restart a specific service
docker-compose --profile infra restart pulsar
```

### Docker Compose (Full Containerized Stack)
```bash
# Start EVERYTHING in containers (app + infrastructure)
# This is the recommended way to test the full containerized deployment
docker-compose --profile app up -d --build

# View all logs
docker-compose --profile app logs -f

# View application logs only
docker-compose --profile app logs -f resiliency-spike-app

# View specific service logs
docker-compose --profile app logs -f postgres
docker-compose --profile app logs -f vault
docker-compose --profile app logs -f pulsar

# Check service health status
docker-compose --profile app ps

# Stop all services (keeps volumes)
docker-compose --profile app down

# Stop and remove volumes (clean slate)
docker-compose --profile app down -v

# Restart specific service
docker-compose --profile app restart resiliency-spike-app
```

### Using Environment Variables for Profiles
```bash
# Set default profile for the session (infrastructure only)
export COMPOSE_PROFILES=infra
docker-compose up -d
docker-compose logs -f

# Or for full stack (app + infrastructure)
export COMPOSE_PROFILES=app
docker-compose up -d --build
docker-compose logs -f
```

### Container Deployment
```bash
# Build optimized container image
docker build -f Containerfile -t resiliency-spike:latest .

# Build without cache
docker build --no-cache -f Containerfile -t resiliency-spike:latest .

# Verify image was created
docker images resiliency-spike

# Run standalone container (requires external services)
docker run -d \
  --name resiliency-spike \
  -p 8080:8080 \
  -e SPRING_R2DBC_URL=r2dbc:postgresql://host.docker.internal:5432/resiliency_spike \
  -e SPRING_R2DBC_USERNAME=resiliency_user \
  -e SPRING_R2DBC_PASSWORD=resiliency_password \
  resiliency-spike:latest
```

**Container Image Details:**
- **Builder Stage**: gradle:8.14.3-jdk21-alpine (~800MB, not in final image)
- **Runtime Image**: eclipse-temurin:21-jre-alpine (~423MB)
- **Features**: Multi-stage build, non-root user (spring:spring), health checks, JVM tuning
- **Health Check**: Monitors Spring Boot Actuator endpoint every 30s
- **JVM Options**: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`

See [CONTAINER.md](CONTAINER.md) for comprehensive container deployment documentation.

## Local Development Infrastructure

The project includes a single Docker Compose configuration (`docker-compose.yml`) with profile support for flexible deployment:

**Profiles:**
- **`infra`** - Infrastructure services only (for local Spring Boot development)
- **`app`** - Full stack including containerized Spring Boot application

**Services:**

**Jaeger (Distributed Tracing):**
- UI port: `16686` - Jaeger web interface for trace visualization
- OTLP HTTP port: `4318` - OpenTelemetry trace ingestion endpoint
- OTLP gRPC port: `4317` - Alternative OpenTelemetry ingestion
- Access UI: http://localhost:16686
- Purpose: Distributed tracing backend with automatic instrumentation via OpenTelemetry
- Traces: HTTP requests, database queries, Pulsar operations, circuit breakers, rate limiters, retries
- Integration: Micrometer Tracing Bridge with OTLP exporter
- See [OBSERVABILITY.md](OBSERVABILITY.md) for comprehensive documentation

**SigNoz (APM & Observability Platform):**
- Frontend UI port: `3301` - SigNoz web interface for traces, metrics, and APM
- OTLP HTTP port: `4320` - OpenTelemetry ingestion (mapped to avoid conflict with Jaeger)
- OTLP gRPC port: `4319` - Alternative OpenTelemetry ingestion (mapped to avoid conflict)
- Query Service port: `8085` - SigNoz backend API
- ClickHouse port: `9000` - Native protocol for trace/metrics storage
- Access UI: http://localhost:3301
- Purpose: All-in-one observability platform (APM, traces, metrics, logs)
- Features: Service maps, trace analysis, metrics dashboards, alerts, exceptions tracking
- Storage: ClickHouse columnar database for high-performance analytics
- See [SIGNOZ.md](SIGNOZ.md) for comprehensive SigNoz documentation

**Apache Pulsar (Standalone Mode):**
- Broker port: `6650` - Used by application to connect to Pulsar
- Admin/HTTP port: `8081` - Pulsar admin API and web console (mapped from container port 8080 to avoid conflict with Spring Boot)
- Healthcheck: Runs every 10s using `pulsar-admin brokers healthcheck`
- Data persisted in named volume `pulsar-data`
- Memory configured: 512MB heap, 256MB direct memory

**PostgreSQL 18:**
- Port: `5432`
- Database: `resiliency_spike`
- Username: `resiliency_user`
- Password: Managed by Vault (see below)
- Healthcheck: Runs every 10s using `pg_isready`
- Data persisted in named volume `postgres-data`
- Schema initialization: One-shot init container (`db-init`) runs SQL scripts from `docker/init-scripts/`

**HashiCorp Vault (Development Mode):**
- Port: `8200` - Vault API
- Dev Root Token: `dev-root-token` (for local development only!)
- KV v2 secrets engine enabled at `secret/` path
- Secrets initialization: One-shot init container (`vault-init`) runs `docker/vault-init/init-vault.sh`
- Stores sensitive configuration: database credentials, R2DBC connection settings, Pulsar configuration
- **Note:** Running in dev mode for local development - NOT suitable for production

**Database Schema (auto-initialized):**

Resiliency Tracking:
- `resilience_events` - Tracks resilience events (circuit breaker triggers, rate limiter actions, etc.)
- `circuit_breaker_state` - Stores circuit breaker state and metrics (failure/success counts, state transitions)
- `rate_limiter_metrics` - Rate limiter statistics per time window

Product Catalog:
- `categories` - Product categories with hierarchical parent-child relationships (25 categories)
- `products` - Product catalog with SKU, pricing, inventory, and JSONB metadata (51 products seeded)

Inventory Management:
- `inventory_locations` - Warehouses, stores, distribution centers (4 locations seeded)
- `inventory_stock` - Current stock levels per product per location with automatic availability calculation
- `inventory_transactions` - All inventory movements (receipts, shipments, adjustments, transfers)
- `inventory_reservations` - Reserved stock for orders with expiration tracking

Shopping Cart:
- `shopping_carts` - Active shopping carts with session tracking, user association, and status management
- `cart_items` - Items in carts with pricing stored in cents (integer), quantities, and discounts
- `cart_state_history` - Complete audit trail of all cart events and status changes

SQL scripts in `docker/init-scripts/` are executed alphabetically during first startup:
- `01-init-schema.sql` - Creates resiliency tracking tables
- `02-product-catalog-schema.sql` - Creates product catalog tables and indexes
- `03-product-catalog-seed-data.sql` - Seeds comprehensive product data across multiple categories
- `04-inventory-schema.sql` - Creates inventory tables with triggers and sample data
- `05-shopping-cart-schema.sql` - Creates shopping cart tables with automatic total calculation triggers

The init container waits for PostgreSQL to be healthy before running scripts and exits after completion (restart: "no").

All services are connected via a custom bridge network (`resiliency-spike-network`) and include healthchecks to ensure they're ready before the application connects.

## Observability with OpenTelemetry

The project includes comprehensive distributed tracing and observability using OpenTelemetry with two backend options:

**OpenTelemetry Integration:**
- Micrometer Tracing Bridge for seamless Spring Boot integration
- OTLP exporter for sending traces to observability backends
- W3C Trace Context propagation standard
- Automatic instrumentation of HTTP, R2DBC, Pulsar, Resilience4j

**Backend Options:**

1. **Jaeger (Default)** - Lightweight distributed tracing
   - All-in-one image with collector, query service, and UI
   - In-memory storage for development (configurable for production)
   - Web UI at http://localhost:16686
   - OTLP receivers on ports 4317 (gRPC) and 4318 (HTTP)

2. **SigNoz (Alternative)** - Full-featured APM and observability platform
   - ClickHouse-based storage for high-performance analytics
   - Web UI at http://localhost:3301
   - OTLP receivers on ports 4319 (gRPC) and 4320 (HTTP)
   - Additional features: service maps, metrics dashboards, alerts, exceptions tracking
   - See [SIGNOZ.md](SIGNOZ.md) for detailed SigNoz documentation

**What Gets Traced:**
- All HTTP requests through WebFlux endpoints
- R2DBC database queries with timing information
- Pulsar message publish/consume operations
- Circuit breaker state changes and decisions
- Rate limiter accept/reject decisions
- Retry attempts with exponential backoff

**Trace Context in Logs:**
All log messages include trace ID and span ID for correlation:
```
INFO [resiliency-spike,a1b2c3d4e5f6g7h8,i9j0k1l2m3n4o5p6] Processing request...
```

**Configuration (application.properties):**
```properties
# Enable tracing
management.tracing.enabled=true
management.tracing.sampling.probability=1.0

# OTLP endpoint - Choose backend:
# Jaeger (default):
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
# SigNoz (alternative - uncomment to use):
# management.otlp.tracing.endpoint=http://localhost:4320/v1/traces

management.otlp.tracing.compression=gzip

# W3C Trace Context propagation
management.tracing.propagation.type=w3c

# Log pattern with trace context
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

**Switching Between Backends:**

To use **Jaeger** (default):
```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
```

To use **SigNoz**:
```properties
management.otlp.tracing.endpoint=http://localhost:4320/v1/traces
```

**Accessing UIs:**
```bash
# Jaeger UI
open http://localhost:16686

# SigNoz UI
open http://localhost:3301
```

**Common Use Cases:**
1. **Debug Slow Requests** - Examine trace timeline to find bottlenecks (DB queries, circuit breakers)
2. **Investigate Errors** - Filter traces with `error=true` to see exception details
3. **Monitor Circuit Breakers** - Track state changes and fallback invocations
4. **Analyze Database Performance** - Identify slow queries and N+1 problems
5. **Track Rate Limiter Impact** - See which requests are throttled or rejected
6. **Correlate Logs** - Use trace ID from logs to find corresponding trace in Jaeger

**Verified Working:**
- ✅ Traces exported to Jaeger successfully
- ✅ Traces exported to SigNoz successfully
- ✅ HTTP requests generate full trace hierarchy
- ✅ Database queries captured with timing
- ✅ Circuit breaker and rate limiter spans visible
- ✅ Trace context in logs
- ✅ Jaeger UI functional
- ✅ SigNoz UI functional with APM features

**Documentation:**
- [OBSERVABILITY.md](OBSERVABILITY.md) - Comprehensive Jaeger documentation
- [SIGNOZ.md](SIGNOZ.md) - Comprehensive SigNoz documentation including APM features, service maps, alerts

## Code Architecture

### Package Structure
The codebase follows a standard Spring Boot Kotlin structure under the base package `com.pintailconsultingllc.resiliencyspike`:

- `src/main/kotlin/` - Main application code
  - `controller/` - REST API controllers
  - `domain/` - Entity classes (R2DBC entities)
  - `dto/` - Data Transfer Objects for API requests/responses
  - `repository/` - Reactive repository interfaces
  - `service/` - Service layer with business logic
- `src/main/resources/` - Application properties and configuration
- `src/test/kotlin/` - Test code
  - `controller/` - REST API contract tests
  - `fixtures/` - Test data fixtures and helper utilities
  - `service/` - Service layer unit tests
- `docker/init-scripts/` - Database initialization SQL scripts

### Key Technologies and Patterns

**Reactive Stack:**
This is a fully reactive application using Spring WebFlux, not traditional Spring MVC. All HTTP handling uses reactive types (Mono/Flux) and the application runs on Netty by default, not Tomcat.

**Resilience4j Integration:**
The project uses Spring Cloud Circuit Breaker with Resilience4j for implementing comprehensive resilience patterns. Rate limiters, circuit breakers, and retry mechanisms are applied to all major service methods using `@RateLimiter`, `@CircuitBreaker`, and `@Retry` annotations with fallback methods. Four resilience instances are configured:
- `shoppingCart` - Protects shopping cart operations
- `cartItem` - Protects cart item operations
- `product` - Protects product catalog operations
- `category` - Protects category operations

Each rate limiter is configured with:
- 100 requests per period limit (prevents abuse and throttling)
- 1-second refresh period (rolling time window)
- 0-second timeout (fail immediately if limit exceeded)
- Health indicators for monitoring via Actuator
- Event buffer for tracking rate limit events

Each circuit breaker is configured with:
- 50% failure rate threshold (opens after 50% failures)
- 10-call sliding window with minimum 5 calls
- 5-second wait in open state before testing recovery
- 2-second slow call threshold
- Automatic transition from OPEN to HALF_OPEN state
- Health indicators for monitoring via Actuator

Each retry instance is configured with:
- 3 maximum attempts (initial call + 2 retries)
- 500ms initial wait duration between attempts
- Exponential backoff with 2x multiplier (500ms → 1000ms → 2000ms)
- Automatic retry for transient exceptions (IOException, TimeoutException, TransientDataAccessException)
- Event monitoring via Actuator endpoints

**Apache Pulsar:**
Reactive Pulsar integration is configured for message streaming. This is reactive messaging, so use reactive Pulsar templates and reactive consumers/producers.

**Actuator:**
Spring Boot Actuator is enabled for monitoring, health checks, and metrics. Exposed endpoints include:
- `/actuator/health` - Overall application health including circuit breaker and rate limiter states
- `/actuator/circuitbreakers` - List all circuit breakers with current states
- `/actuator/circuitbreakerevents` - Recent circuit breaker state transitions and events
- `/actuator/retries` - List all retry instances with current metrics
- `/actuator/retryevents` - Recent retry events and attempts
- `/actuator/ratelimiters` - List all rate limiters with current metrics
- `/actuator/ratelimiterevents` - Recent rate limiter events (accepted/rejected requests)
- `/actuator/metrics` - Application metrics including circuit breaker, retry, and rate limiter statistics
- `/actuator/info` - Application information

**Spring Data R2DBC:**
Uses reactive database access with Spring Data R2DBC and PostgreSQL. All database operations return `Mono<T>` or `Flux<T>` for non-blocking I/O. R2DBC repositories extend `ReactiveCrudRepository` and support custom queries with `@Query` annotation.

### Important Configuration Notes

- **Java Version:** Project targets Java 21 (configured in build.gradle.kts)
- **Kotlin Configuration:** Uses strict JSR-305 compiler flags for null-safety
- **Spring Cloud Version:** 2025.0.0 (managed via BOM)
- **Test Framework:** JUnit 5 (Jupiter) with Kotlin test support

### Dependencies of Note

- `spring-boot-starter-data-r2dbc` - Reactive database access with R2DBC
- `r2dbc-postgresql` - PostgreSQL R2DBC driver
- `spring-cloud-starter-vault-config` - HashiCorp Vault integration for secrets management
- `spring-cloud-starter-circuitbreaker-reactor-resilience4j` - Circuit breaker implementation
- `springdoc-openapi-starter-webflux-ui` - OpenAPI 3.0 documentation with Swagger UI for WebFlux
- `micrometer-tracing-bridge-otel` - OpenTelemetry integration via Micrometer
- `opentelemetry-exporter-otlp` - OTLP exporter for sending traces to Jaeger
- `reactor-kotlin-extensions` - Kotlin-friendly extensions for Project Reactor
- `kotlinx-coroutines-reactor` - Coroutine support for reactive code
- `jackson-module-kotlin` - JSON serialization for Kotlin data classes
- `reactor-test` - Testing support for reactive streams
- `kotlinx-coroutines-test` - Testing support for coroutines

### Domain Model

**Entity Classes (R2DBC):**

Resiliency Tracking:
- `ResilienceEvent` - Tracks resilience events with metadata (JSONB)
- `CircuitBreakerState` - Circuit breaker state and metrics
- `RateLimiterMetrics` - Rate limiter statistics per time window

Product Catalog:
- `Category` - Product categories with hierarchical parent-child relationships
- `Product` - Products with SKU, pricing, stock quantity, and JSONB metadata

Inventory Management:
- `InventoryLocation` - Physical/logical locations (warehouses, stores, distribution centers)
- `InventoryStock` - Stock levels per product per location with automatic availability calculation
- `InventoryTransaction` - Audit trail of all inventory movements
- `InventoryReservation` - Reserved stock with expiration and status tracking

Shopping Cart:
- `ShoppingCart` - Shopping carts with session/user tracking, status (ACTIVE, ABANDONED, CONVERTED, EXPIRED), and monetary amounts in cents
- `CartItem` - Cart line items with product references, quantities, pricing in cents, and optional discounts
- `CartStateHistory` - Event sourcing for cart lifecycle (CREATED, ITEM_ADDED, ITEM_REMOVED, ABANDONED, CONVERTED, etc.)

**Repository Interfaces:**
All repositories extend `ReactiveCrudRepository` and return reactive types:

Resiliency Tracking:
- `ResilienceEventRepository` - Query events by type, name, status; find recent events
- `CircuitBreakerStateRepository` - Find by name or state; query by failure threshold
- `RateLimiterMetricsRepository` - Query by time range; find high rejection rates

Product Catalog:
- `CategoryRepository` - Find by name, parent, active status; support hierarchical queries; search by name
- `ProductRepository` - Find by SKU, category, price range; search by name; low stock queries; complex filtering

Inventory Management:
- `InventoryLocationRepository` - Find by code, type, location; search locations
- `InventoryStockRepository` - Find by product/location; low stock alerts; availability checks; aggregate quantities
- `InventoryTransactionRepository` - Find by product/location/type/date; transaction history
- `InventoryReservationRepository` - Find by product/location/status; expired reservations; aggregate reserved quantities

Shopping Cart:
- `ShoppingCartRepository` - Find by session/user/UUID/status; expired and abandoned cart queries; cart-with-items queries
- `CartItemRepository` - Find by cart/product; calculate totals; find discounted/high-value/bulk items
- `CartStateHistoryRepository` - Find events by cart/type/date range; support conversion and abandonment analytics

**Service Classes:**
All services use reactive repositories and return `Mono<T>` or `Flux<T>`. Critical operations are protected with rate limiters, circuit breakers, and retry mechanisms.

Resiliency Tracking:
- `ResilienceEventService` - Manage resilience events

Product Catalog (Rate Limiter + Circuit Breaker + Retry Protected):
- `CategoryService` - CRUD operations, hierarchical category management, soft delete
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (category instance)
  - Protected methods: `createCategory()`, `updateCategory()`, `findCategoryById()`
  - Rate limit: 100 requests per second
  - Retry strategy: 3 attempts with exponential backoff (500ms → 1000ms → 2000ms)
  - Fallback: User-friendly error messages with logging
- `ProductService` - CRUD operations, stock management, product filtering/searching, soft delete
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (product instance)
  - Protected methods: `createProduct()`, `updateProduct()`, `findProductById()`
  - Rate limit: 100 requests per second
  - Retry strategy: 3 attempts with exponential backoff (500ms → 1000ms → 2000ms)
  - Fallback: User-friendly error messages with logging

Inventory Management:
- `InventoryLocationService` - CRUD operations, location filtering by type, activate/deactivate
- `InventoryStockService` - Stock level management, adjustments, reservations, availability checks, low stock alerts

Shopping Cart (Rate Limiter + Circuit Breaker + Retry Protected):
- `ShoppingCartService` - Cart lifecycle (create, find, abandon, convert, expire), status management, expired cart processing
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (shoppingCart instance)
  - Protected methods: `createCart()`, `findOrCreateCart()`, `findCartById()`, `findCartByUuid()`, `updateCartStatus()`
  - Rate limit: 100 requests per second
  - Retry strategy: 3 attempts with exponential backoff (500ms → 1000ms → 2000ms)
  - Fallback: User-friendly error messages with logging
- `CartItemService` - Add/remove/update items, apply discounts, validate availability, calculate totals
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (cartItem instance)
  - Protected methods: `addItemToCart()`
  - Rate limit: 100 requests per second
  - Retry strategy: 3 attempts with exponential backoff (500ms → 1000ms → 2000ms)
  - Fallback: User-friendly error messages with logging
- `CartStateHistoryService` - Record events, track status changes, calculate conversion/abandonment rates

### OpenAPI Documentation

The application includes comprehensive OpenAPI 3.0 documentation for all REST endpoints using Springdoc OpenAPI.

**Access Points:**
- **Swagger UI**: `http://localhost:8080/swagger-ui.html` - Interactive API documentation and testing
- **OpenAPI JSON**: `http://localhost:8080/api-docs` - OpenAPI 3.0 specification in JSON format

**Configuration:**
All OpenAPI settings are configured in `application.properties`:
- API title: "Spring Boot Resiliency Spike Solution"
- Description: "Spring Boot WebFlux API demonstrating resilience patterns with Circuit Breakers, Retries, and Rate Limiters"
- Version: 0.0.1-SNAPSHOT
- Contact: Pintail Consulting LLC
- Operations sorted by HTTP method
- Tags sorted alphabetically
- "Try it out" functionality enabled

**Documentation Features:**
- Complete endpoint descriptions for all 69 REST endpoints
- Parameter documentation with types, formats, and descriptions
- Request/response body schemas with examples
- HTTP status codes (200, 201, 204, 400, 404) with descriptions
- Query parameter specifications (defaults, required/optional)
- Path parameter descriptions
- Organized by tags: Product Catalog, Shopping Cart, Cart Items, Cart History, Cart Analytics
- ISO 8601 date format specifications for date parameters
- Interactive testing via Swagger UI

**Annotations Used:**
- `@Tag` - Controller-level tag and description
- `@Operation` - Endpoint summary and description
- `@ApiResponses` / `@ApiResponse` - HTTP response codes and content types
- `@Parameter` - Path and query parameter descriptions
- `@Schema` - Response body schema references

**REST API Controllers:**
All controllers expose reactive REST APIs with OpenAPI 3.0 documentation and return reactive types:

Product Catalog APIs:
- `ProductController` - `/api/v1/products` - 18 endpoints for product management
  - OpenAPI Tag: "Product Catalog"
  - Create, update, delete products
  - Get products by ID or SKU
  - Get all products or active products only
  - Get products by category (with active filter)
  - Search products by name
  - Filter by price range or category with price range
  - Find low stock products (configurable threshold)
  - Activate/deactivate products (soft delete)
  - Update stock quantities
  - Count products by category or count active products

Shopping Cart APIs:
- `ShoppingCartController` - `/api/v1/carts` - 24 endpoints for complete cart management
  - OpenAPI Tag: "Shopping Cart"
  - Create carts, get by ID/UUID/session/user/status
  - Associate carts with users, update expiration
  - Cart lifecycle operations (abandon, convert, expire, restore)
  - Find expired/abandoned carts, process batch operations
  - Cart statistics and analytics
- `CartItemController` - `/api/v1/carts/{cartId}/items` - 15 endpoints for cart item operations
  - OpenAPI Tag: "Cart Items"
  - Get all items or specific items
  - Add/remove/update items with quantity, discount, metadata
  - Calculate cart totals and counts
  - Find discounted, high-value, and bulk items
  - Validate item and cart availability
- `CartStateHistoryController` - `/api/v1/carts/{cartId}/history` - 7 endpoints for cart history
  - OpenAPI Tag: "Cart History"
  - Get full history or recent events
  - Filter events by type
  - Count events and get activity summaries
- `CartAnalyticsController` - `/api/v1/analytics/carts` - 5 endpoints for analytics
  - OpenAPI Tag: "Cart Analytics"
  - Get events, conversions, and abandonments in date ranges
  - Calculate conversion and abandonment rates

**DTOs (Data Transfer Objects):**
All DTOs support JSON serialization with Jackson Kotlin module:

Product Catalog DTOs:
- `ProductResponse` - Product representation with all details (ID, SKU, name, description, category, price, stock, metadata)
- `CreateProductRequest` - Create new product (SKU, name, description, categoryId, price, stockQuantity, metadata)
- `UpdateProductRequest` - Update product (partial updates supported for name, description, categoryId, price, stockQuantity, metadata)
- `UpdateProductStockRequest` - Update stock quantity
- `ProductSearchResponse` - Search results wrapper with products list and total count

Shopping Cart DTOs:
- `ShoppingCartResponse` - Cart representation with all details
- `CreateCartRequest` - Create new cart (sessionId, userId, expiresAt)
- `AssociateCartWithUserRequest` - Associate cart with user
- `UpdateCartExpirationRequest` - Update cart expiration time
- `CartStatisticsResponse` - Cart counts by status

Cart Item DTOs:
- `CartItemResponse` - Item representation with product details
- `AddItemToCartRequest` - Add item (productId, quantity)
- `UpdateItemQuantityRequest` - Update quantity
- `ApplyItemDiscountRequest` - Apply discount
- `UpdateItemMetadataRequest` - Update item metadata
- `CartTotalsResponse` - Subtotal, tax, discount, total, item count
- `ItemAvailabilityResponse` - Product availability validation
- `CartValidationResponse` - Full cart validation results

Cart History DTOs:
- `CartStateHistoryResponse` - Event representation
- `ConversionRateResponse` - Conversion rate analytics
- `AbandonmentRateResponse` - Abandonment rate analytics
- `CartActivitySummaryResponse` - Event counts by type

### OpenTelemetry and Observability

**Dependencies:**
- `micrometer-tracing-bridge-otel` - Bridges Micrometer to OpenTelemetry
- `opentelemetry-exporter-otlp` - Exports traces via OTLP to Jaeger

**Automatic Instrumentation:**
Spring Boot automatically instruments:
- WebFlux HTTP endpoints
- R2DBC database queries
- Pulsar messaging operations
- Resilience4j patterns (circuit breakers, rate limiters, retries)

**Configuration:**
All tracing configuration in `application.properties`:
- Sampling: 100% (configurable)
- OTLP endpoint: http://localhost:4318/v1/traces
- Propagation: W3C Trace Context
- Log pattern includes trace ID and span ID

**Accessing Traces:**
- Jaeger UI: http://localhost:16686
- Service: `resiliency-spike`
- Search by operation, tags, duration, time range

**Key Features:**
- Visual trace timelines showing operation sequence
- Service dependency graphs
- Error trace identification
- Performance bottleneck analysis
- Log correlation with trace IDs

See [OBSERVABILITY.md](OBSERVABILITY.md) for detailed documentation.

### Secrets Management with Vault

The application uses HashiCorp Vault for secrets management:

**Configuration Files:**
- `bootstrap.properties` - Vault connection settings (processed before application.properties)
- `application.properties` - Uses property placeholders that are resolved from Vault

**Vault Integration:**
- Spring Cloud Vault fetches secrets on application startup
- Secrets are stored in KV v2 secrets engine at path: `secret/resiliency-spike/`
- All database credentials and sensitive configuration retrieved from Vault
- Local development uses root token `dev-root-token` (configured in bootstrap.properties)

**Secret Paths:**
- `secret/resiliency-spike/database` - Database connection details
- `secret/resiliency-spike/r2dbc` - R2DBC configuration with credentials
- `secret/resiliency-spike/pulsar` - Pulsar service URLs
- `secret/resiliency-spike/application` - Application-level configuration

**Vault CLI Commands:**
```bash
# View database secrets
docker exec -e VAULT_TOKEN=dev-root-token resiliency-spike-vault vault kv get secret/resiliency-spike/database

# Update a secret
docker exec -e VAULT_TOKEN=dev-root-token resiliency-spike-vault vault kv put secret/resiliency-spike/database password=new_password

# List all secrets
docker exec -e VAULT_TOKEN=dev-root-token resiliency-spike-vault vault kv list secret/resiliency-spike
```

### R2DBC Configuration

Database connection configured in `application.properties` with values from Vault:
- URL: Retrieved from Vault (`secret/resiliency-spike/r2dbc`)
- Credentials: Retrieved from Vault (username/password)
- Connection pooling enabled (10 initial, 20 max connections)
- All operations are non-blocking and return `Mono<T>` or `Flux<T>`

### Resilience Configuration (Rate Limiters + Circuit Breakers + Retries)

The application implements comprehensive resilience patterns using Resilience4j with annotation-based configuration. All critical service methods are protected with rate limiters, retry logic, and circuit breakers, working together to prevent abuse, handle transient failures, and prevent cascading failures.

**Resilience Instances:**

Four resilience instances are configured in `application.properties`, each with rate limiter, retry, and circuit breaker configurations:

1. **shoppingCart** - Shopping cart operations
   - Protected methods: `createCart()`, `findOrCreateCart()`, `findCartById()`, `findCartByUuid()`, `updateCartStatus()`
   - Service: `ShoppingCartService`
   - Pattern: `@RateLimiter` → `@Retry` → `@CircuitBreaker` → method execution

2. **cartItem** - Cart item operations
   - Protected methods: `addItemToCart()`
   - Service: `CartItemService`
   - Pattern: `@RateLimiter` → `@Retry` → `@CircuitBreaker` → method execution

3. **product** - Product catalog operations
   - Protected methods: `createProduct()`, `updateProduct()`, `findProductById()`
   - Service: `ProductService`
   - Pattern: `@RateLimiter` → `@Retry` → `@CircuitBreaker` → method execution

4. **category** - Category operations
   - Protected methods: `createCategory()`, `updateCategory()`, `findCategoryById()`
   - Service: `CategoryService`
   - Pattern: `@RateLimiter` → `@Retry` → `@CircuitBreaker` → method execution

**Rate Limiter Configuration (all instances):**
- **Limit for Period**: 100 requests - Maximum requests allowed per time window
- **Limit Refresh Period**: 1 second - Rolling time window for rate limiting
- **Timeout Duration**: 0 seconds - Fail immediately if limit exceeded (no waiting)
- **Health Indicator**: Enabled - Exposed via Spring Boot Actuator
- **Event Consumer Buffer Size**: 100 - Number of events to buffer for monitoring
- **Behavior**: When limit is exceeded, request is rejected and fallback is invoked immediately

**Retry Configuration (all instances):**
- **Max Attempts**: 3 - Initial call plus 2 retries
- **Wait Duration**: 500ms - Initial wait before first retry
- **Exponential Backoff**: Enabled with 2x multiplier
  - 1st retry: waits 500ms
  - 2nd retry: waits 1000ms (500ms × 2)
  - Total max wait: ~1.5 seconds across all retries
- **Retry Exceptions**: Automatic retry only for transient failures
  - `java.io.IOException` - Network/I/O errors
  - `java.util.concurrent.TimeoutException` - Timeout errors
  - `org.springframework.dao.TransientDataAccessException` - Temporary database errors
- **Event Monitoring**: Enabled via Actuator endpoints

**Circuit Breaker Configuration (all instances):**
- **Sliding Window Size**: 10 calls - Tracks last 10 calls to calculate failure rate
- **Minimum Number of Calls**: 5 - Need at least 5 calls before calculating metrics
- **Failure Rate Threshold**: 50% - Opens circuit if 50% or more calls fail
- **Slow Call Rate Threshold**: 50% - Opens circuit if 50% of calls are slow
- **Slow Call Duration Threshold**: 2 seconds - Calls taking longer are considered slow
- **Wait Duration in Open State**: 5 seconds - How long to wait before attempting recovery
- **Permitted Calls in Half-Open**: 3 - Test calls when transitioning to half-open state
- **Automatic Transition**: Enabled - Automatically moves from OPEN → HALF_OPEN
- **Health Indicator**: Enabled - Exposed via Spring Boot Actuator
- **Record Exceptions**: All exceptions trigger circuit breaker (shoppingCart only)

**How Rate Limiter, Retry, and Circuit Breaker Work Together:**

When a method is annotated with `@RateLimiter`, `@Retry`, and `@CircuitBreaker`:
1. **First**: Rate limiter prevents abuse and throttles requests
   - Checks if request quota is available (100 requests per second)
   - If limit exceeded, immediately rejects request and invokes fallback
   - Prevents system overload and ensures fair resource allocation
2. **Second**: Retry attempts to recover from transient failures automatically
   - Only executes if rate limiter allows the request
   - Retries up to 3 times with exponential backoff
   - Only retries specific transient exceptions
   - If all retries fail, passes failure to circuit breaker
3. **Third**: Circuit breaker prevents cascading failures
   - Tracks success/failure rates across all retry attempts
   - Opens circuit if too many failures occur (even after retries)
   - When open, fails fast without attempting retries
4. **Finally**: Fallback method provides graceful degradation
   - Called when any resilience pattern cannot recover
   - Returns user-friendly error message
   - Logs full exception context for debugging

This layered approach provides:
- **Request throttling** to prevent abuse and overload (rate limiter)
- **Immediate recovery** from transient failures (retry)
- **Fast failure** when service is degraded (circuit breaker)
- **Graceful degradation** with meaningful error messages (fallback)

**Fallback Behavior:**

All fallback methods follow a consistent pattern:
- Log error with full context (method name, parameters, exception)
- Return `Mono.error()` with user-friendly message
- Preserve original exception in the error chain
- Use SLF4J logger for structured logging
- Fallback message indicates "Rate limiter/Retry/Circuit breaker fallback" for clarity
- Same fallback method handles all three resilience patterns (rate limiter, retry, circuit breaker)

**Monitoring Resilience Patterns:**

```bash
# Check overall health (includes circuit breaker and rate limiter states)
curl http://localhost:8080/actuator/health

# Rate Limiter Monitoring
curl http://localhost:8080/actuator/ratelimiters
curl http://localhost:8080/actuator/ratelimiterevents
curl http://localhost:8080/actuator/metrics/resilience4j.ratelimiter.available.permissions
curl http://localhost:8080/actuator/metrics/resilience4j.ratelimiter.waiting.threads

# Circuit Breaker Monitoring
curl http://localhost:8080/actuator/circuitbreakers
curl http://localhost:8080/actuator/circuitbreakerevents
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls

# Retry Monitoring
curl http://localhost:8080/actuator/retries
curl http://localhost:8080/actuator/retryevents
curl http://localhost:8080/actuator/metrics/resilience4j.retry.calls

# Specific instance metrics
curl http://localhost:8080/actuator/metrics/resilience4j.ratelimiter.available.permissions?tag=name:shoppingCart
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:shoppingCart
curl http://localhost:8080/actuator/metrics/resilience4j.retry.calls?tag=name:shoppingCart
```

**Circuit Breaker States:**
- **CLOSED**: Normal operation, all calls pass through
- **OPEN**: Too many failures, calls fail fast with fallback
- **HALF_OPEN**: Testing recovery, limited calls allowed
- **DISABLED**: Circuit breaker bypassed (for testing)

**Best Practices:**
- Rate limiter, retry, and circuit breaker annotations are stacked on the same methods for layered resilience
- Annotation order: `@RateLimiter` → `@Retry` → `@CircuitBreaker` (outer to inner)
- All three annotations share the same fallback method for consistency
- Rate limiter fails immediately (0s timeout) to provide fast feedback
- Retry only on transient exceptions (IOException, TimeoutException, TransientDataAccessException)
- Circuit breakers track all failures, including those after exhausted retries
- Fallback methods have identical signatures to protected methods plus `Exception` parameter
- All fallback methods log errors at ERROR level with contextual information
- Health indicators and event endpoints allow real-time monitoring via Actuator
- Resilience4j debug logging enabled for troubleshooting all resilience patterns
- Rate limits can be adjusted per instance based on actual load requirements (default: 100 req/sec)

## Testing

The project includes comprehensive unit tests using JUnit 5, Mockito, and Reactor Test.

**Test Structure:**
- `src/test/kotlin/controller/` - REST API contract tests (WebFluxTest)
- `src/test/kotlin/fixtures/` - Test data fixtures and helper utilities
- `src/test/kotlin/service/` - Service layer unit tests with MockitoExtension
- `src/test/kotlin/repository/` - Repository unit tests with mocked dependencies

**Testing Dependencies:**
- `mockito-kotlin` - Kotlin-friendly Mockito DSL
- `mockito-junit-jupiter` - Mockito JUnit 5 integration
- `reactor-test` - StepVerifier for testing reactive streams
- `kotlin-test-junit5` - Kotlin test assertions
- `spring-boot-test` - WebTestClient for API contract testing

**Test Patterns:**

Service/Repository Tests:
- All tests use `@ExtendWith(MockitoExtension::class)` for dependency mocking
- Use `@DisplayName` annotations for clear test descriptions
- Reactive assertions with `StepVerifier` from reactor-test
- Test fixtures in `TestFixtures` object for consistent test data

Controller Tests:
- Use `@WebFluxTest` for lightweight controller testing
- Mock service layer dependencies with `@MockBean`
- Use `WebTestClient` for reactive endpoint testing
- Verify HTTP status codes, response bodies, and JSON structure
- Test query parameters and request body validation

**Test Coverage (214 total tests):**

Resiliency Tracking (44 tests):
- `ResilienceEventServiceTest` - 11 tests for resilience event operations
- `CircuitBreakerStateRepositoryTest` - 11 tests for circuit breaker state management
- `RateLimiterMetricsRepositoryTest` - 11 tests for rate limiter metrics
- `ResilienceEventRepositoryTest` - 11 tests for event repository operations

Product Catalog (44 tests):
- `ProductServiceTest` - 22 tests covering all product operations
- `CategoryServiceTest` - 22 tests covering all category operations

Shopping Cart Services (42 tests):
- `ShoppingCartServiceTest` - 16 tests for cart lifecycle management
- `CartItemServiceTest` - 19 tests for item operations and validation
- `CartStateHistoryServiceTest` - 7 tests for event tracking and analytics

REST API Contract Tests (83 tests):
- `ProductControllerTest` - 22 tests for product catalog endpoints
  - Create, update, delete products
  - Get products by ID, SKU, category
  - Search and filter products (name, price range, category + price range)
  - Low stock queries with configurable threshold
  - Activate/deactivate products
  - Update stock quantities
  - Count operations (by category, active products)
- `ShoppingCartControllerTest` - 25 tests for cart management endpoints
  - Create, get, find carts by various criteria
  - Associate with user, update expiration
  - Lifecycle operations (abandon, convert, expire, restore)
  - Batch operations and statistics
- `CartItemControllerTest` - 17 tests for item management endpoints
  - Get, add, update, remove items
  - Apply discounts and update metadata
  - Calculate totals and validate availability
  - Find discounted, high-value, and bulk items
- `CartStateHistoryControllerTest` - 10 tests for cart history endpoints
  - Get history, recent events, events by type
  - Count events and get activity summaries
- `CartAnalyticsControllerTest` - 9 tests for analytics endpoints
  - Get events, conversions, abandonments in date ranges
  - Calculate conversion and abandonment rates
  - Handle edge cases (zero conversions, 100% conversion)

Integration Tests (1 test):
- `ResiliencySpikeApplicationTests` - Spring context loads successfully

**Testing Best Practices:**

Service Tests:
- Use `anyOrNull()` from mockito-kotlin for nullable parameters with default values
- Add `@MockitoSettings(strictness = Strictness.LENIENT)` when needed for complex mocking scenarios
- For reactive chains with `switchIfEmpty`, add fallback stubs to handle eager evaluation
- All monetary values in tests use integer cents (e.g., 9999 = $99.99)

Controller Tests:
- Use `any()` matchers when exact parameter matching is not critical
- Verify service method calls with `verify()` to ensure proper delegation
- Test both success and error scenarios
- Include tests for query parameters with default values
- Validate JSON response structure and content

## Development Notes

When adding new features or making changes:

1. **Reactive Programming:** Use Mono/Flux for asynchronous operations, not blocking code
2. **Database Access:** All database operations are reactive - repositories return `Mono<T>` for single results or `Flux<T>` for streams
3. **Kotlin Coroutines:** The project supports coroutines with reactor integration
4. **Circuit Breaker:** Use Spring Cloud Circuit Breaker annotations/configurations for resilience patterns
5. **Testing:** Write unit tests with MockitoExtension and use `StepVerifier` for reactive assertions
6. **Entity Mapping:** R2DBC uses `@Table` and `@Column` annotations (not JPA's `@Entity`)
