# Resiliency Spike

A Spring Boot spike project exploring resiliency patterns using Kotlin, Spring WebFlux, Resilience4j, and Apache Pulsar.

## Overview

This project demonstrates reactive programming patterns with circuit breakers, rate limiting, and other resilience patterns in a modern Spring Boot application.

## Tech Stack

- **Language:** Kotlin 1.9.25
- **Framework:** Spring Boot 3.5.7
- **Web:** Spring WebFlux (reactive)
- **Messaging:** Apache Pulsar (reactive)
- **Resilience:** Spring Cloud Circuit Breaker with Resilience4j
- **Monitoring:** Spring Boot Actuator
- **Database:** PostgreSQL 18
- **Build Tool:** Gradle (Kotlin DSL)
- **Java Version:** 21

## Getting Started

### Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle (wrapper included)

### Local Development Setup

1. **Start infrastructure services:**
   ```bash
   docker-compose up -d
   ```

   This starts:
   - **Apache Pulsar** on ports 6650 (broker) and 8081 (admin/HTTP)
   - **PostgreSQL** on port 5432
   - **HashiCorp Vault** on port 8200 (API)
   - **Vault Init Container** (one-shot) to initialize secrets
   - **Database Init Container** (one-shot) to initialize the schema

2. **Verify services are healthy:**
   ```bash
   docker-compose ps
   ```

3. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

4. **Run tests:**
   ```bash
   ./gradlew test
   ```

### Docker Services

#### Apache Pulsar
- **Broker Port:** 6650 (for application connections)
- **Admin/HTTP Port:** 8081 (web console and admin API - mapped from container port 8080)
- **Mode:** Standalone
- **Data:** Persisted in `pulsar-data` volume
- **Note:** Admin port is mapped to 8081 on the host to avoid conflict with Spring Boot application running on 8080

#### HashiCorp Vault
- **Port:** 8200 (API)
- **Mode:** Development mode (NOT for production!)
- **Root Token:** `dev-root-token`
- **UI:** http://localhost:8200/ui
- **Secrets Engine:** KV v2 at path `secret/`
- **Initialization:** Automatic via one-shot init container (`vault-init`)
- **Purpose:** Manages all application secrets (database credentials, API keys, etc.)

#### PostgreSQL
- **Port:** 5432
- **Database:** `resiliency_spike`
- **Username:** `resiliency_user`
- **Password:** Managed by Vault (stored in `secret/resiliency-spike/database`)
- **Data:** Persisted in `postgres-data` volume
- **Schema Initialization:** Automatic via one-shot init container

#### Database Schema
The database schema is automatically initialized on first startup by the `db-init` container. SQL scripts in `docker/init-scripts/` are executed in alphabetical order:

1. `01-init-schema.sql` - Creates resiliency tracking tables
2. `02-product-catalog-schema.sql` - Creates product catalog tables and indexes
3. `03-product-catalog-seed-data.sql` - Seeds comprehensive product catalog data
4. `04-inventory-schema.sql` - Creates inventory management tables and triggers
5. `05-shopping-cart-schema.sql` - Creates shopping cart tables with automatic total calculation

**Resiliency Tracking Tables:**
- `resilience_events` - Tracks resilience events (circuit breaker, rate limiter, etc.)
- `circuit_breaker_state` - Stores circuit breaker state and metrics
- `rate_limiter_metrics` - Rate limiter statistics

**Product Catalog Tables:**
- `categories` - Product categories with hierarchical parent-child relationships (25 categories)
- `products` - Product catalog with SKU, pricing, inventory, and JSONB metadata (51 products seeded)

**Inventory Management Tables:**
- `inventory_locations` - Warehouses, stores, distribution centers (4 locations seeded)
- `inventory_stock` - Current stock levels per product per location
- `inventory_transactions` - All inventory movements (receipts, shipments, adjustments, transfers)
- `inventory_reservations` - Reserved stock for orders with expiration tracking

**Shopping Cart Tables:**
- `shopping_carts` - Active shopping carts with session tracking, user association, and status management
- `cart_items` - Items in carts with pricing (stored in cents as integers), quantities, and discounts
- `cart_state_history` - Complete audit trail of all cart events and status changes

**Seeded Data:**
The database comes pre-populated with:
- **6 root categories**: Electronics, Books, Home & Kitchen, Sports & Outdoors, Clothing, Toys & Games
- **19 subcategories**: Including Computers, Smartphones, Tablets, Fiction, Non-Fiction, etc.
- **51 products** across various categories with realistic pricing ($12.99 - $2,299.99)
- Rich JSONB metadata for each product (brand, specs, warranty, etc.)

To modify the schema or seed data:
1. Add or edit SQL files in `docker/init-scripts/`
2. Restart with clean volumes: `docker-compose down -v && docker-compose up -d`

### Useful Commands

#### Application
```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.service.ProductServiceTest"

# Run a specific test method
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.service.ProductServiceTest.shouldCreateProductSuccessfully"

# Run tests with detailed output
./gradlew test --info

# Clean build
./gradlew clean build

# Build Docker image
./gradlew bootBuildImage
```

#### Docker Compose
```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f pulsar
docker-compose logs -f postgres
docker-compose logs db-init  # View schema initialization logs

# Stop services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

#### Monitoring Resilience Patterns (Rate Limiters + Circuit Breakers + Retries)
```bash
# Check application health (includes rate limiter and circuit breaker states)
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

## Architecture

This is a fully **reactive application** using Spring WebFlux:
- HTTP handling uses reactive types (Mono/Flux)
- Runs on Netty, not Tomcat
- Uses Project Reactor for reactive streams
- Supports Kotlin coroutines with reactor integration

### Key Components

- **Resilience4j Rate Limiters + Circuit Breakers + Retries** - Comprehensive resilience patterns with triple-layered fault tolerance
  - **4 resilience instances**: `shoppingCart`, `cartItem`, `product`, `category`
  - **Rate limiter**: 100 requests per second with immediate rejection on exceeded limits
  - **Retry configuration**: 3 attempts with exponential backoff (500ms → 1000ms → 2000ms)
  - **Circuit breaker**: 50% failure rate threshold with 10-call sliding window
  - **Layered protection**: Rate Limiter → Retry → Circuit Breaker → Fallback
  - Prevents abuse, automatic recovery from transient failures, and fast failure when degraded
  - Health indicators and event monitoring via Actuator endpoints
- **Apache Pulsar** - Reactive messaging and event streaming
- **Spring Boot Actuator** - Health checks, metrics, and resilience monitoring
  - `/actuator/ratelimiters` - Rate limiter states and metrics
  - `/actuator/ratelimiterevents` - Recent rate limiter events
  - `/actuator/circuitbreakers` - Circuit breaker states
  - `/actuator/circuitbreakerevents` - Recent circuit breaker events
  - `/actuator/retries` - Retry instance metrics
  - `/actuator/retryevents` - Recent retry attempts
  - `/actuator/health` - Overall health including rate limiters and circuit breakers
  - `/actuator/metrics` - Rate limiter, circuit breaker, and retry statistics
- **WebFlux** - Reactive REST APIs
- **Spring Data R2DBC** - Reactive database access with PostgreSQL

### Data Layer

**Entity Classes (R2DBC):**

Resiliency Tracking:
- `ResilienceEvent` - Tracks all resilience events (circuit breaker, rate limiter, etc.)
- `CircuitBreakerState` - Stores circuit breaker state and metrics
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

**Repositories (Reactive):**
All repositories extend `ReactiveCrudRepository` and return `Mono<T>` or `Flux<T>` for non-blocking database operations:

Resiliency Tracking:
- `ResilienceEventRepository` - Query events by type, name, or status
- `CircuitBreakerStateRepository` - Manage circuit breaker state
- `RateLimiterMetricsRepository` - Track rate limiter metrics over time

Product Catalog:
- `CategoryRepository` - Find by name, parent, active status; hierarchical queries; search by name
- `ProductRepository` - Find by SKU, category, price range; search by name; low stock queries

Inventory Management:
- `InventoryLocationRepository` - Find by code, type, location; search locations
- `InventoryStockRepository` - Find by product/location; low stock alerts; availability checks
- `InventoryTransactionRepository` - Find by product/location/type/date; transaction history
- `InventoryReservationRepository` - Find by product/location/status; expired reservations

Shopping Cart:
- `ShoppingCartRepository` - Find by session/user/UUID/status; expired and abandoned cart queries
- `CartItemRepository` - Find by cart/product; calculate totals; find discounted/high-value items
- `CartStateHistoryRepository` - Find events by cart/type/date range; conversion/abandonment analytics

**Service Layer:**
All services use reactive repositories and return `Mono<T>` or `Flux<T>`. Critical operations are protected with triple-layered resilience (rate limiter + retry + circuit breaker).

Resiliency Tracking:
- `ResilienceEventService` - Manage resilience events

Product Catalog (Rate Limiter + Retry + Circuit Breaker Protected):
- `CategoryService` - CRUD operations, hierarchical category management, soft delete
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (category instance)
  - Protected methods: `createCategory()`, `updateCategory()`, `findCategoryById()`
  - Rate limit: 100 requests per second
  - Retry: 3 attempts with exponential backoff
- `ProductService` - CRUD operations, stock management, product filtering/searching, soft delete
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (product instance)
  - Protected methods: `createProduct()`, `updateProduct()`, `findProductById()`
  - Rate limit: 100 requests per second
  - Retry: 3 attempts with exponential backoff

Inventory Management:
- `InventoryLocationService` - CRUD operations, location filtering by type, activate/deactivate
- `InventoryStockService` - Stock level management, adjustments, reservations, availability checks, low stock alerts

Shopping Cart (Rate Limiter + Retry + Circuit Breaker Protected):
- `ShoppingCartService` - Cart lifecycle (create, find, abandon, convert, expire), status management, expired cart processing
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (shoppingCart instance)
  - Protected methods: `createCart()`, `findOrCreateCart()`, `findCartById()`, `findCartByUuid()`, `updateCartStatus()`
  - Rate limit: 100 requests per second
  - Retry: 3 attempts with exponential backoff
- `CartItemService` - Add/remove/update items, apply discounts, validate availability, calculate totals
  - Resilience: `@RateLimiter` + `@Retry` + `@CircuitBreaker` (cartItem instance)
  - Protected methods: `addItemToCart()`
  - Rate limit: 100 requests per second
  - Retry: 3 attempts with exponential backoff
- `CartStateHistoryService` - Record events, track status changes, calculate conversion/abandonment rates

**REST API Layer:**
All controllers expose reactive REST APIs with comprehensive endpoints:

- `ShoppingCartController` - `/api/v1/carts` - 24 endpoints for cart management
- `CartItemController` - `/api/v1/carts/{cartId}/items` - 15 endpoints for item operations
- `CartStateHistoryController` - `/api/v1/carts/{cartId}/history` - 7 endpoints for cart history
- `CartAnalyticsController` - `/api/v1/analytics/carts` - 5 endpoints for analytics and reporting

## Testing

The project includes comprehensive test coverage with **192 test cases** covering:
- REST API contract testing with WebFluxTest
- Service layer logic with business operations
- Repository operations with reactive database access
- Edge cases and error handling
- Reactive stream behavior

**Testing Stack:**
- JUnit 5 with MockitoExtension
- Mockito Kotlin for mocking (with `anyOrNull()` for nullable default parameters)
- Reactor Test (StepVerifier) for reactive assertions
- Spring WebFluxTest with WebTestClient for API testing
- Custom test fixtures (`TestFixtures`) for consistent test data

**Test Coverage (192 total tests):**

Resiliency Tracking (44 tests):
- `ResilienceEventServiceTest` - 11 tests for resilience event operations
- `ResilienceEventRepositoryTest` - 11 tests for event repository operations
- `CircuitBreakerStateRepositoryTest` - 11 tests for circuit breaker state management
- `RateLimiterMetricsRepositoryTest` - 11 tests for rate limiter metrics

Product Catalog (44 tests):
- `ProductServiceTest` - 22 tests covering all CRUD operations, stock management, filtering
- `CategoryServiceTest` - 22 tests covering all CRUD operations, hierarchical queries, soft delete

Shopping Cart Services (42 tests):
- `ShoppingCartServiceTest` - 16 tests for cart lifecycle management (create, find, abandon, convert, expire)
- `CartItemServiceTest` - 19 tests for item operations (add, remove, update, discounts, validation)
- `CartStateHistoryServiceTest` - 7 tests for event tracking and conversion/abandonment analytics

REST API Contract Tests (61 tests):
- `ShoppingCartControllerTest` - 25 tests for cart management endpoints
  - Create, retrieve, and find carts by various criteria
  - Associate carts with users and update expiration
  - Cart lifecycle operations (abandon, convert, expire, restore)
  - Batch operations and cart statistics
- `CartItemControllerTest` - 17 tests for item management endpoints
  - Get, add, update, and remove items
  - Apply discounts and update item metadata
  - Calculate cart totals and validate availability
  - Find discounted, high-value, and bulk items
- `CartStateHistoryControllerTest` - 10 tests for cart history endpoints
  - Retrieve full history and recent events
  - Filter events by type and count operations
  - Generate activity summaries
- `CartAnalyticsControllerTest` - 9 tests for analytics endpoints
  - Retrieve events, conversions, and abandonments by date range
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
- All tests use `@DisplayName` annotations for clear test descriptions
- Follow reactive testing patterns with `StepVerifier` for assertions

Controller Tests:
- Use `@WebFluxTest` for lightweight controller testing without full application context
- Mock service layer dependencies with `@MockBean`
- Use `WebTestClient` for reactive endpoint testing
- Verify HTTP status codes (200 OK, 201 CREATED, 204 NO_CONTENT)
- Validate JSON response structure and content with `jsonPath()`
- Test query parameters with default values
- Use argument matchers for complex object comparisons

## Development Notes

- All HTTP operations should use `Mono<T>` or `Flux<T>` return types
- All database operations are reactive - repositories return `Mono<T>` or `Flux<T>`
- Use `reactor-test` and `StepVerifier` for testing reactive streams
- Write unit tests with MockitoExtension and descriptive @DisplayName annotations
- Leverage Kotlin coroutines with `kotlinx-coroutines-reactor` for cleaner async code
- Circuit breaker metrics available through Actuator endpoints
- R2DBC entities use `@Table` and `@Column` annotations (not JPA's `@Entity`)

### Database Configuration

The application connects to PostgreSQL via R2DBC (reactive driver):
- Connection URL: Retrieved from Vault (`secret/resiliency-spike/r2dbc`)
- Credentials: Retrieved from Vault (username/password)
- Connection pooling enabled (10-20 connections)
- All database operations are non-blocking

### Secrets Management with Vault

The application integrates with HashiCorp Vault for secure secrets management:

**Configuration:**
- `bootstrap.properties` - Vault connection settings
- `application.properties` - Uses property placeholders resolved from Vault
- Spring Cloud Vault automatically fetches secrets on startup

**Managed Secrets:**
- Database credentials (username/password)
- R2DBC connection configuration
- Pulsar service URLs
- Application-level configuration

**Accessing Vault:**
```bash
# View secrets via CLI
docker exec -e VAULT_TOKEN=dev-root-token resiliency-spike-vault vault kv get secret/resiliency-spike/database

# Access Vault UI
open http://localhost:8200/ui
# Login with token: dev-root-token
```

**Vault Secret Paths:**
- `secret/resiliency-spike/database` - Database connection details
- `secret/resiliency-spike/r2dbc` - R2DBC configuration
- `secret/resiliency-spike/pulsar` - Pulsar URLs
- `secret/resiliency-spike/application` - App configuration

## Features Implemented

### Secrets Management
- HashiCorp Vault integration with Spring Cloud Vault
- KV v2 secrets engine for secure storage
- Automatic secret injection via Spring configuration
- Development mode setup with auto-initialization
- Policy-based access control

### Resilience Patterns with Resilience4j
A comprehensive resilience implementation with triple-layered fault tolerance protecting all critical service operations:

**Resilience Instances:**
- **shoppingCart** - Shopping cart operations (5 protected methods)
- **cartItem** - Cart item operations (1 protected method)
- **product** - Product catalog operations (3 protected methods)
- **category** - Category operations (3 protected methods)

**Rate Limiter Configuration:**
- 100 requests per second - Maximum requests allowed per time window
- 1-second refresh period - Rolling time window for rate limiting
- 0-second timeout - Fail immediately if limit exceeded (no waiting)
- Health indicators and event monitoring enabled
- Prevents abuse and ensures fair resource allocation

**Retry Configuration:**
- 3 maximum attempts (initial call + 2 retries)
- 500ms initial wait duration between attempts
- Exponential backoff with 2x multiplier (500ms → 1000ms → 2000ms)
- Automatic retry only for transient exceptions:
  - `IOException` - Network/I/O errors
  - `TimeoutException` - Timeout errors
  - `TransientDataAccessException` - Temporary database errors
- Event monitoring via `/actuator/retries` and `/actuator/retryevents`

**Circuit Breaker Configuration:**
- 50% failure rate threshold - Opens circuit after 50% of calls fail
- 10-call sliding window - Evaluates last 10 calls for failure rate
- 5-second wait in open state - Recovery testing interval
- 2-second slow call threshold - Treats slow calls as potential failures
- Automatic transition from OPEN → HALF_OPEN state
- Health indicators for real-time monitoring

**How Rate Limiter, Retry, and Circuit Breaker Work Together:**
1. **First**: Rate limiter prevents abuse and throttles requests (100 req/sec max)
2. **Second**: Retry attempts to recover from transient failures (up to 3 attempts with backoff)
3. **Third**: Circuit breaker prevents cascading failures (opens after repeated failures)
4. **Finally**: Fallback provides graceful degradation (user-friendly error messages)

This triple-layered approach provides:
- **Request throttling** to prevent abuse and overload (rate limiter)
- **Immediate recovery** from transient failures (retry)
- **Fast failure** when service is degraded (circuit breaker)
- **Graceful degradation** with meaningful error messages (fallback)

**Monitoring:**
- `/actuator/ratelimiters` - Current states of all rate limiters
- `/actuator/ratelimiterevents` - Recent rate limiter events (accepted/rejected)
- `/actuator/circuitbreakers` - Current states of all circuit breakers
- `/actuator/circuitbreakerevents` - Recent state transitions and events
- `/actuator/retries` - Retry instance metrics
- `/actuator/retryevents` - Recent retry attempts
- `/actuator/health` - Overall health including rate limiter and circuit breaker status
- `/actuator/metrics/resilience4j.ratelimiter.*` - Rate limiter metrics
- `/actuator/metrics/resilience4j.circuitbreaker.*` - Circuit breaker metrics
- `/actuator/metrics/resilience4j.retry.*` - Retry metrics

**Benefits:**
- Request throttling - Prevents abuse and system overload with configurable limits
- Transient failure recovery - Automatic retries with backoff for temporary issues
- Fault isolation - Failures don't cascade across services
- Fast failure - Fail quickly when services are unavailable (after retry exhaustion)
- Automatic recovery - Tests service health automatically
- Resource protection - Prevents thread exhaustion from slow calls
- Observability - Real-time visibility into all resilience patterns

### Resiliency Patterns
- Circuit breaker state tracking and metrics
- Rate limiter metrics collection
- Resilience event logging with JSONB metadata
- Spring Boot Actuator integration for monitoring

### Product Catalog System
A fully reactive product catalog implementation demonstrating:

**Category Management:**
- Hierarchical category structure (parent-child relationships)
- Active/inactive status (soft delete)
- Category search and filtering
- Child category counting and validation

**Product Management:**
- SKU-based product identification
- Price range queries
- Stock quantity tracking
- Low stock alerts (configurable threshold)
- Product search by name
- Category-based filtering
- Rich JSONB metadata for extensibility
- Active/inactive status (soft delete)

**Advanced Queries:**
- Find products by category and price range
- Search products with partial name matching
- Find low stock products for reordering
- Hierarchical category traversal
- Count operations for analytics

### Inventory Management System
A comprehensive inventory tracking system with:

**Location Management:**
- Multiple location types (warehouse, store, distribution center)
- Geographic tracking with city/state/country
- Active/inactive status management
- Location search and filtering

**Stock Management:**
- Real-time stock levels per product per location
- Automatic available quantity calculation (on_hand - reserved)
- Low stock alerts with configurable thresholds
- Stock level queries and aggregations

**Transaction Tracking:**
- Complete audit trail of all inventory movements
- Transaction types: receipt, shipment, adjustment, transfer
- Reference numbers for external system integration
- Quantity and location tracking for all movements

**Reservation System:**
- Reserve stock for orders with expiration
- Automatic reservation status management (ACTIVE, EXPIRED, RELEASED, FULFILLED)
- Released-by tracking for audit purposes
- Query reserved quantities and expired reservations

### Shopping Cart System
A fully reactive shopping cart implementation with event sourcing:

**Cart Management:**
- Session-based and user-associated carts
- Cart status lifecycle: ACTIVE → ABANDONED/CONVERTED/EXPIRED
- UUID-based cart identification for security
- Automatic 7-day expiration
- Expired and abandoned cart processing
- Guest-to-user cart association

**Cart Items:**
- Add/remove/update items with quantity management
- Pricing stored in integer cents for precision
- Automatic line total calculation via database triggers
- Product availability validation before adding items
- Item-level discount support
- Metadata support for item customization (color, size, etc.)

**Cart Totals:**
- Automatic subtotal calculation from line items
- Tax amount tracking
- Cart-level discount support
- Total amount calculation (subtotal + tax - discounts)
- Item count tracking

**Event Sourcing:**
- Complete audit trail of all cart events
- Event types: CREATED, ITEM_ADDED, ITEM_REMOVED, ITEM_UPDATED, ABANDONED, CONVERTED, EXPIRED
- Status change tracking with previous/new status
- JSONB event data for extensibility
- Conversion and abandonment rate analytics

**Advanced Features:**
- Find discounted items in cart
- Find high-value items (above threshold)
- Find bulk items (quantity threshold)
- Calculate cart totals and item counts
- Validate all items for availability before checkout

All operations are fully reactive using `Mono<T>` and `Flux<T>` return types, ensuring non-blocking I/O throughout the stack.

### REST APIs for Shopping Cart System
A complete set of reactive REST APIs providing full cart management capabilities:

**Cart Management APIs** (`/api/v1/carts`):
- `POST /api/v1/carts` - Create new cart
- `GET /api/v1/carts/{cartId}` - Get cart by ID
- `GET /api/v1/carts/uuid/{cartUuid}` - Get cart by UUID
- `GET /api/v1/carts/session/{sessionId}` - Get or create cart for session
- `GET /api/v1/carts/session/{sessionId}/current` - Get current cart by session
- `GET /api/v1/carts/user/{userId}` - Get all carts for user
- `GET /api/v1/carts/status/{status}` - Get carts by status (ACTIVE, ABANDONED, CONVERTED, EXPIRED)
- `PUT /api/v1/carts/{cartId}/user` - Associate cart with user
- `PUT /api/v1/carts/{cartId}/expiration` - Update cart expiration
- `POST /api/v1/carts/{cartId}/abandon` - Mark cart as abandoned
- `POST /api/v1/carts/{cartId}/convert` - Mark cart as converted
- `POST /api/v1/carts/{cartId}/expire` - Mark cart as expired
- `POST /api/v1/carts/{cartId}/restore` - Restore cart to active
- `GET /api/v1/carts/expired` - Get all expired carts
- `GET /api/v1/carts/abandoned?hoursInactive=24` - Get abandoned carts
- `POST /api/v1/carts/process-expired` - Batch process expired carts
- `POST /api/v1/carts/process-abandoned?hoursInactive=24` - Batch process abandoned carts
- `GET /api/v1/carts/with-items?status=ACTIVE` - Get carts with items
- `GET /api/v1/carts/empty` - Get empty carts
- `GET /api/v1/carts/count/{status}` - Count carts by status
- `GET /api/v1/carts/statistics` - Get comprehensive cart statistics
- `DELETE /api/v1/carts/{cartId}` - Delete cart

**Cart Item APIs** (`/api/v1/carts/{cartId}/items`):
- `GET /api/v1/carts/{cartId}/items` - Get all items in cart
- `GET /api/v1/carts/{cartId}/items/{productId}` - Get specific item
- `POST /api/v1/carts/{cartId}/items` - Add item to cart
- `PUT /api/v1/carts/{cartId}/items/{productId}/quantity` - Update item quantity
- `PUT /api/v1/carts/{cartId}/items/{productId}/discount` - Apply item discount
- `PUT /api/v1/carts/{cartId}/items/{productId}/metadata` - Update item metadata
- `DELETE /api/v1/carts/{cartId}/items/{productId}` - Remove item from cart
- `DELETE /api/v1/carts/{cartId}/items` - Clear all items from cart
- `GET /api/v1/carts/{cartId}/items/totals` - Get cart totals
- `GET /api/v1/carts/{cartId}/items/count` - Count items in cart
- `GET /api/v1/carts/{cartId}/items/discounted` - Get discounted items
- `GET /api/v1/carts/{cartId}/items/high-value?minPriceCents=10000` - Get high-value items
- `GET /api/v1/carts/{cartId}/items/bulk?minQuantity=10` - Get bulk quantity items
- `GET /api/v1/carts/{cartId}/items/{productId}/validate` - Validate item availability
- `GET /api/v1/carts/{cartId}/items/validate` - Validate all cart items

**Cart History APIs** (`/api/v1/carts/{cartId}/history`):
- `GET /api/v1/carts/{cartId}/history` - Get full cart history
- `GET /api/v1/carts/{cartId}/history/recent?hoursBack=24` - Get recent events
- `GET /api/v1/carts/{cartId}/history/type/{eventType}` - Get events by type
- `GET /api/v1/carts/{cartId}/history/latest` - Get most recent event
- `GET /api/v1/carts/{cartId}/history/count/{eventType}` - Count events by type
- `GET /api/v1/carts/{cartId}/history/count` - Count total events
- `GET /api/v1/carts/{cartId}/history/summary` - Get activity summary

**Analytics APIs** (`/api/v1/analytics/carts`):
- `GET /api/v1/analytics/carts/events?startDate={iso-date}&endDate={iso-date}` - Get events in date range
- `GET /api/v1/analytics/carts/conversions?startDate={iso-date}&endDate={iso-date}` - Get conversion events
- `GET /api/v1/analytics/carts/abandonments?startDate={iso-date}&endDate={iso-date}` - Get abandonment events
- `GET /api/v1/analytics/carts/conversion-rate?startDate={iso-date}&endDate={iso-date}` - Calculate conversion rate
- `GET /api/v1/analytics/carts/abandonment-rate?startDate={iso-date}&endDate={iso-date}` - Calculate abandonment rate

All APIs return reactive types (`Mono<T>` or `Flux<T>`), use standard HTTP status codes (200 OK, 201 CREATED, 204 NO_CONTENT), and support JSON request/response bodies with proper error handling.

## Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/3.5.7/reference/)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/6.2.12/web/webflux.html)
- [Resilience4j](https://resilience4j.readme.io/)
- [Apache Pulsar](https://pulsar.apache.org/docs/)
- [Project Reactor](https://projectreactor.io/docs/core/release/reference/)
- [Spring Data R2DBC](https://spring.io/projects/spring-data-r2dbc)
