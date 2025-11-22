# CLAUDE.md

Project guidance for Claude Code (claude.ai/code).

## Project Overview

Spring Boot spike exploring resiliency patterns with Kotlin and Spring WebFlux. Demonstrates reactive programming with Resilience4j (circuit breakers, rate limiters, retries), Apache Pulsar, and comprehensive observability.

**Tech Stack:** Kotlin 1.9.25 • Spring Boot 3.5.7 • Spring WebFlux • Resilience4j • Apache Pulsar • Spring Boot Actuator • OpenTelemetry + Jaeger/SigNoz • Java 21 • Gradle (Kotlin DSL)

## Common Commands

**Build & Run:**
```bash
./gradlew bootRun                                    # Local development
./gradlew build                                      # Build project
./gradlew clean build                                # Clean build
./gradlew bootBuildImage                             # Spring Boot buildpack image
docker build -f Containerfile -t resiliency-spike:latest .  # Custom container
docker-compose --profile app up -d --build           # Full containerized stack
```

**Testing:**
```bash
./gradlew test                                       # All tests
./gradlew test --tests "ClassName"                   # Single test class
./gradlew check                                      # Tests + verification
```

**Docker Compose Profiles:**
- `infra` - Infrastructure only (Postgres, Vault, Pulsar, Jaeger/SigNoz) for local Spring Boot development
- `app` - Full stack (infrastructure + containerized Spring Boot app)

```bash
# Infrastructure only (for local bootRun)
docker-compose --profile infra up -d
docker-compose --profile infra logs -f [service]
docker-compose --profile infra down [-v]

# Full containerized stack
docker-compose --profile app up -d --build
docker-compose --profile app logs -f resiliency-spike-app
docker-compose --profile app down [-v]

# Or use environment variable
export COMPOSE_PROFILES=infra  # or 'app'
docker-compose up -d
```

**Container Image:** Multi-stage build using Gradle 8.14.3 + JDK 21 (builder), eclipse-temurin:21-jre-alpine (~423MB runtime). See [CONTAINER.md](CONTAINER.md) for details.

## Infrastructure Services

**Observability (choose one):**
- **Jaeger** (default): UI at :16686, OTLP HTTP :4318, gRPC :4317 - See [OBSERVABILITY.md](OBSERVABILITY.md)
- **SigNoz** (alternative): UI at :3301, OTLP HTTP :4320, gRPC :4319 - Full APM with service maps, alerts - See [SIGNOZ.md](SIGNOZ.md)
- Both trace HTTP requests, R2DBC queries, Pulsar ops, circuit breakers, rate limiters, retries

**Core Services:**
- **Pulsar**: Broker :6650, Admin :8081, reactive messaging, 512MB heap
- **PostgreSQL 18**: :5432, database `resiliency_spike`, auto-initialized via `docker/init-scripts/*.sql`
- **Vault** (dev mode): :8200, token `dev-root-token`, stores secrets at `secret/resiliency-spike/*`

**Database Schema:** Auto-initialized via `docker/init-scripts/*.sql` (executed alphabetically)
- Resiliency: `resilience_events`, `circuit_breaker_state`, `rate_limiter_metrics`
- Product Catalog: `categories` (hierarchical, 25 seeded), `products` (SKU, pricing, JSONB metadata, 51 seeded)
- Inventory: `inventory_locations`, `inventory_stock`, `inventory_transactions`, `inventory_reservations`
- Shopping Cart: `shopping_carts` (session/user tracking, status), `cart_items` (pricing in cents), `cart_state_history` (audit trail)

## Observability

**Stack:** Micrometer Tracing Bridge + OTLP exporter + W3C Trace Context. Auto-instruments HTTP, R2DBC, Pulsar, Resilience4j. Logs include trace/span IDs.

**Switch backends** in `application.properties`:
- Jaeger: `management.otlp.tracing.endpoint=http://localhost:4318/v1/traces` (default)
- SigNoz: `management.otlp.tracing.endpoint=http://localhost:4320/v1/traces`

See [OBSERVABILITY.md](OBSERVABILITY.md) and [SIGNOZ.md](SIGNOZ.md) for details.

## Architecture

**Package:** `com.pintailconsultingllc.resiliencyspike` - `controller/`, `domain/` (R2DBC entities), `dto/`, `repository/`, `service/`

**Key Patterns:**
- **Fully Reactive:** Spring WebFlux (not MVC), runs on Netty, all operations return `Mono<T>` or `Flux<T>`
- **Resilience4j:** Four instances (shoppingCart, cartItem, product, category) with stacked `@RateLimiter` → `@Retry` → `@CircuitBreaker` annotations
  - Rate Limiter: 100 req/sec, 1s window, fail immediately (0s timeout)
  - Retry: 3 attempts, exponential backoff (500ms, 1000ms, 2000ms), only for IOException/TimeoutException/TransientDataAccessException
  - Circuit Breaker: 50% failure threshold, 10-call window (min 5), 5s open wait, auto-transition to HALF_OPEN
  - All monitored via `/actuator/health`, `/actuator/circuitbreakers`, `/actuator/retries`, `/actuator/ratelimiters`
- **R2DBC:** Reactive PostgreSQL access, repositories extend `ReactiveCrudRepository`
- **Vault:** Secrets at `secret/resiliency-spike/*`, fetched on startup via Spring Cloud Vault
- **OpenAPI:** Swagger UI at `:8080/swagger-ui.html`, 69 endpoints across 5 tags

**Config:** Java 21, Kotlin 1.9.25, Spring Cloud 2025.0.0, JUnit 5

## Domain Model

**Entities:** `ResilienceEvent`, `CircuitBreakerState`, `RateLimiterMetrics` | `Category` (hierarchical), `Product` (SKU, JSONB metadata) | `InventoryLocation`, `InventoryStock`, `InventoryTransaction`, `InventoryReservation` | `ShoppingCart` (status, cents), `CartItem` (cents), `CartStateHistory` (event sourcing)

**Repositories:** All extend `ReactiveCrudRepository`, return `Mono<T>`/`Flux<T>`. Custom queries via `@Query`.

**Services (Resilience-Protected):**
- `CategoryService`, `ProductService` - CRUD, soft delete. Protected: `create*()`, `update*()`, `findById()`
- `ShoppingCartService` - Lifecycle (create, abandon, convert, expire). Protected: `create*()`, `find*()`, `updateStatus()`
- `CartItemService` - Add/remove/update, discounts. Protected: `addItemToCart()`
- All fallbacks: log error, return `Mono.error()` with user-friendly message

## REST API (69 endpoints)

**Controllers:** `ProductController` (18), `ShoppingCartController` (24), `CartItemController` (15), `CartStateHistoryController` (7), `CartAnalyticsController` (5)

**DTOs:** Product (`ProductResponse`, `CreateProductRequest`, `UpdateProductRequest`) | Cart (`ShoppingCartResponse`, `CreateCartRequest`, `CartStatisticsResponse`) | Items (`CartItemResponse`, `AddItemToCartRequest`, `CartTotalsResponse`, `ItemAvailabilityResponse`) | History/Analytics (conversion/abandonment rates)

## Vault Integration

**Secrets:** Fetched on startup from `secret/resiliency-spike/{database,r2dbc,pulsar,application}`. Dev token: `dev-root-token`.

**CLI:**
```bash
docker exec -e VAULT_TOKEN=dev-root-token resiliency-spike-vault vault kv get secret/resiliency-spike/database
```

## Resilience Monitoring

**Actuator endpoints:**
```bash
curl localhost:8080/actuator/{health,ratelimiters,ratelimiterevents,circuitbreakers,circuitbreakerevents,retries,retryevents}
curl localhost:8080/actuator/metrics/resilience4j.{ratelimiter,circuitbreaker,retry}.{available.permissions,calls}?tag=name:shoppingCart
```

**Circuit Breaker States:** CLOSED (normal) | OPEN (fail fast) | HALF_OPEN (testing recovery) | DISABLED (bypassed)

## Testing (214 tests)

**Stack:** JUnit 5, `mockito-kotlin`, `reactor-test` (`StepVerifier`), `@WebFluxTest` + `WebTestClient`

**Coverage:**
- Service Tests (86): Resiliency (44), Product/Category (44), Cart (42) - Use `@ExtendWith(MockitoExtension::class)`, `StepVerifier`
- Controller Tests (83): Product (22), Cart (25), CartItem (17), History (10), Analytics (9) - Use `@WebFluxTest`, `@MockBean`
- Integration (1): Context loads

**Best Practices:**
- Service: Use `anyOrNull()` for nullable params, `@MockitoSettings(strictness = LENIENT)` for complex mocks, monetary values in cents
- Controller: Use `any()` matchers, `verify()` delegation, test success/error paths

## Development Guidelines

1. **Reactive:** Use `Mono<T>`/`Flux<T>`, no blocking code, Netty runtime (not Tomcat)
2. **R2DBC:** Use `@Table`/`@Column` (not JPA's `@Entity`), all DB ops return reactive types
3. **Resilience:** Stack `@RateLimiter` → `@Retry` → `@CircuitBreaker` on critical methods, shared fallback
4. **Testing:** `StepVerifier` for reactive assertions, `WebTestClient` for API tests
5. **Vault:** Secrets fetched on startup from `secret/resiliency-spike/*`
