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
- Java 21
- Gradle (Kotlin DSL)

## Common Commands

### Build and Run
```bash
# Run the application
./gradlew bootRun

# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Build Docker image
./gradlew bootBuildImage
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

### Docker Compose (Local Development)
```bash
# Start all services (Pulsar + PostgreSQL)
docker-compose up -d

# View logs from all services
docker-compose logs -f

# View logs from specific service
docker-compose logs -f pulsar
docker-compose logs -f postgres

# Check service health status
docker-compose ps

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v

# Restart a specific service
docker-compose restart pulsar
```

## Local Development Infrastructure

The project includes a Docker Compose configuration (`docker-compose.yml`) for local development with the following services:

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
The project uses Spring Cloud Circuit Breaker with Resilience4j for implementing resilience patterns. Circuit breakers are applied to all major service methods using `@CircuitBreaker` annotations with fallback methods. Four circuit breaker instances are configured:
- `shoppingCart` - Protects shopping cart operations
- `cartItem` - Protects cart item operations
- `product` - Protects product catalog operations
- `category` - Protects category operations

Each circuit breaker is configured with:
- 50% failure rate threshold (opens after 50% failures)
- 10-call sliding window with minimum 5 calls
- 5-second wait in open state before testing recovery
- 2-second slow call threshold
- Automatic transition from OPEN to HALF_OPEN state
- Health indicators for monitoring via Actuator

**Apache Pulsar:**
Reactive Pulsar integration is configured for message streaming. This is reactive messaging, so use reactive Pulsar templates and reactive consumers/producers.

**Actuator:**
Spring Boot Actuator is enabled for monitoring, health checks, and metrics. Exposed endpoints include:
- `/actuator/health` - Overall application health including circuit breaker states
- `/actuator/circuitbreakers` - List all circuit breakers with current states
- `/actuator/circuitbreakerevents` - Recent circuit breaker state transitions and events
- `/actuator/metrics` - Application metrics including circuit breaker statistics
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
All services use reactive repositories and return `Mono<T>` or `Flux<T>`. Critical operations are protected with circuit breakers.

Resiliency Tracking:
- `ResilienceEventService` - Manage resilience events

Product Catalog (Circuit Breaker Protected):
- `CategoryService` - CRUD operations, hierarchical category management, soft delete
  - Circuit breaker: `category`
  - Protected methods: `createCategory()`, `updateCategory()`, `findCategoryById()`
  - Fallback: User-friendly error messages with logging
- `ProductService` - CRUD operations, stock management, product filtering/searching, soft delete
  - Circuit breaker: `product`
  - Protected methods: `createProduct()`, `updateProduct()`, `findProductById()`
  - Fallback: User-friendly error messages with logging

Inventory Management:
- `InventoryLocationService` - CRUD operations, location filtering by type, activate/deactivate
- `InventoryStockService` - Stock level management, adjustments, reservations, availability checks, low stock alerts

Shopping Cart (Circuit Breaker Protected):
- `ShoppingCartService` - Cart lifecycle (create, find, abandon, convert, expire), status management, expired cart processing
  - Circuit breaker: `shoppingCart`
  - Protected methods: `createCart()`, `findOrCreateCart()`, `findCartById()`, `findCartByUuid()`, `updateCartStatus()`
  - Fallback: User-friendly error messages with logging
- `CartItemService` - Add/remove/update items, apply discounts, validate availability, calculate totals
  - Circuit breaker: `cartItem`
  - Protected methods: `addItemToCart()`
  - Fallback: User-friendly error messages with logging
- `CartStateHistoryService` - Record events, track status changes, calculate conversion/abandonment rates

**REST API Controllers:**
All controllers expose reactive REST APIs and return reactive types:

Shopping Cart APIs:
- `ShoppingCartController` - `/api/v1/carts` - 24 endpoints for complete cart management
  - Create carts, get by ID/UUID/session/user/status
  - Associate carts with users, update expiration
  - Cart lifecycle operations (abandon, convert, expire, restore)
  - Find expired/abandoned carts, process batch operations
  - Cart statistics and analytics
- `CartItemController` - `/api/v1/carts/{cartId}/items` - 15 endpoints for cart item operations
  - Get all items or specific items
  - Add/remove/update items with quantity, discount, metadata
  - Calculate cart totals and counts
  - Find discounted, high-value, and bulk items
  - Validate item and cart availability
- `CartStateHistoryController` - `/api/v1/carts/{cartId}/history` - 7 endpoints for cart history
  - Get full history or recent events
  - Filter events by type
  - Count events and get activity summaries
- `CartAnalyticsController` - `/api/v1/analytics/carts` - 5 endpoints for analytics
  - Get events, conversions, and abandonments in date ranges
  - Calculate conversion and abandonment rates

**DTOs (Data Transfer Objects):**
All DTOs support JSON serialization with Jackson Kotlin module:

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

### Circuit Breaker Configuration

The application implements circuit breakers using Resilience4j with the `@CircuitBreaker` annotation pattern. All critical service methods are protected with fallback mechanisms.

**Circuit Breaker Instances:**

Four circuit breaker instances are configured in `application.properties`:

1. **shoppingCart** - Shopping cart operations
   - Protected methods: `createCart()`, `findOrCreateCart()`, `findCartById()`, `findCartByUuid()`, `updateCartStatus()`
   - Service: `ShoppingCartService`

2. **cartItem** - Cart item operations
   - Protected methods: `addItemToCart()`
   - Service: `CartItemService`

3. **product** - Product catalog operations
   - Protected methods: `createProduct()`, `updateProduct()`, `findProductById()`
   - Service: `ProductService`

4. **category** - Category operations
   - Protected methods: `createCategory()`, `updateCategory()`, `findCategoryById()`
   - Service: `CategoryService`

**Configuration Parameters (all instances):**
- **Sliding Window Size**: 10 calls - Tracks last 10 calls to calculate failure rate
- **Minimum Number of Calls**: 5 - Need at least 5 calls before calculating metrics
- **Failure Rate Threshold**: 50% - Opens circuit if 50% or more calls fail
- **Slow Call Rate Threshold**: 50% - Opens circuit if 50% of calls are slow
- **Slow Call Duration Threshold**: 2 seconds - Calls taking longer are considered slow
- **Wait Duration in Open State**: 5 seconds - How long to wait before attempting recovery
- **Permitted Calls in Half-Open**: 3 - Test calls when transitioning to half-open state
- **Automatic Transition**: Enabled - Automatically moves from OPEN → HALF_OPEN
- **Health Indicator**: Enabled - Exposed via Spring Boot Actuator

**Fallback Behavior:**

All fallback methods follow a consistent pattern:
- Log error with full context (method name, parameters, exception)
- Return `Mono.error()` with user-friendly message
- Preserve original exception in the error chain
- Use SLF4J logger for structured logging

**Monitoring Circuit Breakers:**

```bash
# Check circuit breaker health
curl http://localhost:8080/actuator/health

# List all circuit breakers and their states
curl http://localhost:8080/actuator/circuitbreakers

# View recent circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents

# Get circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls
```

**Circuit Breaker States:**
- **CLOSED**: Normal operation, all calls pass through
- **OPEN**: Too many failures, calls fail fast with fallback
- **HALF_OPEN**: Testing recovery, limited calls allowed
- **DISABLED**: Circuit breaker bypassed (for testing)

**Best Practices:**
- Circuit breakers protect database operations and external service calls
- Fallback methods have identical signatures to protected methods plus `Exception` parameter
- All fallback methods log errors at ERROR level with contextual information
- Health indicators allow monitoring via Actuator endpoints
- Resilience4j debug logging enabled for troubleshooting

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

**Test Coverage (192 total tests):**

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

REST API Contract Tests (61 tests):
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
