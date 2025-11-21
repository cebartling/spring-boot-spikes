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
  - `domain/` - Entity classes (R2DBC entities)
  - `repository/` - Reactive repository interfaces
  - `service/` - Service layer with business logic
- `src/main/resources/` - Application properties and configuration
- `src/test/kotlin/` - Test code
  - `fixtures/` - Test data fixtures and helper utilities
  - `service/` - Service layer unit tests
- `docker/init-scripts/` - Database initialization SQL scripts

### Key Technologies and Patterns

**Reactive Stack:**
This is a fully reactive application using Spring WebFlux, not traditional Spring MVC. All HTTP handling uses reactive types (Mono/Flux) and the application runs on Netty by default, not Tomcat.

**Resilience4j Integration:**
The project uses Spring Cloud Circuit Breaker with Resilience4j for implementing resilience patterns (circuit breakers, rate limiters, retries, bulkheads, time limiters).

**Apache Pulsar:**
Reactive Pulsar integration is configured for message streaming. This is reactive messaging, so use reactive Pulsar templates and reactive consumers/producers.

**Actuator:**
Spring Boot Actuator is enabled for monitoring, health checks, and metrics. Actuator endpoints will expose information about circuit breakers, health, and application metrics.

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
All services use reactive repositories and return `Mono<T>` or `Flux<T>`:

Resiliency Tracking:
- `ResilienceEventService` - Manage resilience events

Product Catalog:
- `CategoryService` - CRUD operations, hierarchical category management, soft delete
- `ProductService` - CRUD operations, stock management, product filtering/searching, soft delete

Inventory Management:
- `InventoryLocationService` - CRUD operations, location filtering by type, activate/deactivate
- `InventoryStockService` - Stock level management, adjustments, reservations, availability checks, low stock alerts

Shopping Cart:
- `ShoppingCartService` - Cart lifecycle (create, find, abandon, convert, expire), status management, expired cart processing
- `CartItemService` - Add/remove/update items, apply discounts, validate availability, calculate totals
- `CartStateHistoryService` - Record events, track status changes, calculate conversion/abandonment rates

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

## Testing

The project includes comprehensive unit tests using JUnit 5, Mockito, and Reactor Test.

**Test Structure:**
- `src/test/kotlin/fixtures/` - Test data fixtures and helper utilities
- `src/test/kotlin/service/` - Service layer unit tests with MockitoExtension
- `src/test/kotlin/repository/` - Repository unit tests with mocked dependencies

**Testing Dependencies:**
- `mockito-kotlin` - Kotlin-friendly Mockito DSL
- `mockito-junit-jupiter` - Mockito JUnit 5 integration
- `reactor-test` - StepVerifier for testing reactive streams
- `kotlin-test-junit5` - Kotlin test assertions

**Test Patterns:**
- All tests use `@ExtendWith(MockitoExtension::class)` for dependency mocking
- Use `@DisplayName` annotations for clear test descriptions
- Reactive assertions with `StepVerifier` from reactor-test
- Test fixtures in `TestFixtures` object for consistent test data

**Test Coverage (131 total tests):**

Resiliency Tracking (44 tests):
- `ResilienceEventServiceTest` - 11 tests for resilience event operations
- `CircuitBreakerStateRepositoryTest` - 11 tests for circuit breaker state management
- `RateLimiterMetricsRepositoryTest` - 11 tests for rate limiter metrics
- `ResilienceEventRepositoryTest` - 11 tests for event repository operations

Product Catalog (44 tests):
- `ProductServiceTest` - 22 tests covering all product operations
- `CategoryServiceTest` - 22 tests covering all category operations

Shopping Cart (42 tests):
- `ShoppingCartServiceTest` - 16 tests for cart lifecycle management
- `CartItemServiceTest` - 19 tests for item operations and validation
- `CartStateHistoryServiceTest` - 7 tests for event tracking and analytics

**Testing Best Practices:**
- Use `anyOrNull()` from mockito-kotlin for nullable parameters with default values
- Add `@MockitoSettings(strictness = Strictness.LENIENT)` when needed for complex mocking scenarios
- For reactive chains with `switchIfEmpty`, add fallback stubs to handle eager evaluation
- All monetary values in tests use integer cents (e.g., 9999 = $99.99)

## Development Notes

When adding new features or making changes:

1. **Reactive Programming:** Use Mono/Flux for asynchronous operations, not blocking code
2. **Database Access:** All database operations are reactive - repositories return `Mono<T>` for single results or `Flux<T>` for streams
3. **Kotlin Coroutines:** The project supports coroutines with reactor integration
4. **Circuit Breaker:** Use Spring Cloud Circuit Breaker annotations/configurations for resilience patterns
5. **Testing:** Write unit tests with MockitoExtension and use `StepVerifier` for reactive assertions
6. **Entity Mapping:** R2DBC uses `@Table` and `@Column` annotations (not JPA's `@Entity`)
