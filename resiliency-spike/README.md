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

#### PostgreSQL
- **Port:** 5432
- **Database:** `resiliency_spike`
- **Username:** `resiliency_user`
- **Password:** `resiliency_password`
- **Data:** Persisted in `postgres-data` volume
- **Schema Initialization:** Automatic via one-shot init container

#### Database Schema
The database schema is automatically initialized on first startup by the `db-init` container. SQL scripts in `docker/init-scripts/` are executed in alphabetical order:

1. `01-init-schema.sql` - Creates resiliency tracking tables
2. `02-product-catalog-schema.sql` - Creates product catalog tables and indexes
3. `03-product-catalog-seed-data.sql` - Seeds comprehensive product catalog data

**Resiliency Tracking Tables:**
- `resilience_events` - Tracks resilience events (circuit breaker, rate limiter, etc.)
- `circuit_breaker_state` - Stores circuit breaker state and metrics
- `rate_limiter_metrics` - Rate limiter statistics

**Product Catalog Tables:**
- `categories` - Product categories with hierarchical parent-child relationships (25 categories)
- `products` - Product catalog with SKU, pricing, inventory, and JSONB metadata (51 products seeded)

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

## Architecture

This is a fully **reactive application** using Spring WebFlux:
- HTTP handling uses reactive types (Mono/Flux)
- Runs on Netty, not Tomcat
- Uses Project Reactor for reactive streams
- Supports Kotlin coroutines with reactor integration

### Key Components

- **Resilience4j** - Circuit breakers, rate limiters, retries, bulkheads, and time limiters
- **Apache Pulsar** - Reactive messaging and event streaming
- **Spring Boot Actuator** - Health checks, metrics, and monitoring endpoints
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

**Repositories (Reactive):**
All repositories extend `ReactiveCrudRepository` and return `Mono<T>` or `Flux<T>` for non-blocking database operations:

Resiliency Tracking:
- `ResilienceEventRepository` - Query events by type, name, or status
- `CircuitBreakerStateRepository` - Manage circuit breaker state
- `RateLimiterMetricsRepository` - Track rate limiter metrics over time

Product Catalog:
- `CategoryRepository` - Find by name, parent, active status; hierarchical queries; search by name
- `ProductRepository` - Find by SKU, category, price range; search by name; low stock queries

**Service Layer:**
All services use reactive repositories and return `Mono<T>` or `Flux<T>`:

Resiliency Tracking:
- `ResilienceEventService` - Manage resilience events

Product Catalog:
- `CategoryService` - CRUD operations, hierarchical category management, soft delete (activate/deactivate)
- `ProductService` - CRUD operations, stock management, product filtering/searching, soft delete

## Testing

The project includes comprehensive unit tests with 95+ test cases covering:
- Service layer logic with business operations
- Repository operations with reactive database access
- Edge cases and error handling
- Reactive stream behavior

**Testing Stack:**
- JUnit 5 with MockitoExtension
- Mockito Kotlin for mocking
- Reactor Test (StepVerifier) for reactive assertions
- Custom test fixtures (`TestFixtures`) for consistent test data

**Test Coverage:**

Resiliency Tracking:
- `ResilienceEventServiceTest` - 11 test cases
- `ResilienceEventRepositoryTest` - 11 test cases
- `CircuitBreakerStateRepositoryTest` - 11 test cases
- `RateLimiterMetricsRepositoryTest` - 11 test cases

Product Catalog:
- `ProductServiceTest` - 27 test cases (all CRUD operations, stock management, filtering)
- `CategoryServiceTest` - 24 test cases (all CRUD operations, hierarchical queries, soft delete)

All tests use `@DisplayName` annotations for clear test descriptions and follow reactive testing patterns with `StepVerifier`.

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
- Connection URL: `r2dbc:postgresql://localhost:5432/resiliency_spike`
- Connection pooling enabled (10-20 connections)
- All database operations are non-blocking

## Features Implemented

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

All operations are fully reactive using `Mono<T>` and `Flux<T>` return types, ensuring non-blocking I/O throughout the stack.

## Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/3.5.7/reference/)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/6.2.12/web/webflux.html)
- [Resilience4j](https://resilience4j.readme.io/)
- [Apache Pulsar](https://pulsar.apache.org/docs/)
- [Project Reactor](https://projectreactor.io/docs/core/release/reference/)
- [Spring Data R2DBC](https://spring.io/projects/spring-data-r2dbc)
