# Project Constitution

**Version:** 1.0
**Last Updated:** 2025-11-22
**Purpose:** Codified principles, patterns, and practices governing development of the Spring Boot Resiliency Spike

---

## I. Core Principles

### 1.1 Reactive First
- **MUST** use reactive types (`Mono<T>`, `Flux<T>`) for all asynchronous operations
- **MUST NOT** introduce blocking code in the reactive pipeline
- **MUST** run on Netty runtime (Spring WebFlux), not Tomcat
- **MUST** use `reactor-test` and `StepVerifier` for testing reactive code

### 1.2 Resiliency by Design
- **MUST** protect critical operations with Resilience4j patterns
- **MUST** stack annotations in order: `@RateLimiter` → `@Retry` → `@CircuitBreaker`
- **MUST** implement fallback methods that log errors and return user-friendly error messages
- **MUST** return `Mono.error()` from fallbacks, never swallow exceptions

### 1.3 Observability First
- **MUST** ensure all operations are traceable via OpenTelemetry
- **MUST** include trace/span IDs in log output
- **MUST** auto-instrument HTTP, R2DBC, Pulsar, and Resilience4j operations
- **SHOULD** use structured logging with meaningful context

### 1.4 Configuration Over Code
- **MUST** externalize all configuration via `application.properties` or Vault
- **MUST** store secrets in Vault at `secret/resiliency-spike/*`
- **MUST** use environment variables or property placeholders, never hardcode values
- **MUST** provide sensible defaults with `${key:default}` syntax

---

## II. Architectural Patterns

### 2.1 Package Structure
```
com.pintailconsultingllc.resiliencyspike/
├── controller/        # REST API endpoints
├── domain/           # R2DBC entities (@Table, @Column)
├── dto/              # Request/Response DTOs
├── repository/       # ReactiveCrudRepository extensions
├── service/          # Business logic with resiliency patterns
└── config/           # Configuration classes
```

### 2.2 Layered Architecture
- **Controllers** handle HTTP concerns, delegate to services
- **Services** implement business logic, apply resiliency patterns
- **Repositories** handle data access via R2DBC
- **DTOs** separate API contract from domain model
- **Domain entities** map 1:1 to database tables

### 2.3 Separation of Concerns
- **MUST** use DTOs for API contracts, not domain entities directly
- **MUST** implement mapper functions in `DtoMappers.kt`
- **MUST** keep domain entities focused on data structure
- **SHOULD** use extension interfaces for custom repository methods

---

## III. Technology Stack Constraints

### 3.1 Core Technologies
- **Java Version:** 21 (MUST use sealed classes, records where appropriate)
- **Kotlin Version:** 1.9.25 (MUST use `data class`, `copy()`, nullable types)
- **Spring Boot:** 3.5.7+ (MUST use WebFlux, not MVC)
- **Spring Cloud:** 2025.0.0
- **Build Tool:** Gradle with Kotlin DSL

### 3.2 Required Dependencies
- **Reactive Data Access:** Spring Data R2DBC + PostgreSQL R2DBC driver
- **Resiliency:** Resilience4j (circuit breaker, retry, rate limiter)
- **Messaging:** Spring Pulsar Reactive
- **Observability:** Micrometer Tracing + OpenTelemetry + OTLP exporter
- **API Documentation:** SpringDoc OpenAPI (WebFlux variant)
- **Testing:** JUnit 5 + mockito-kotlin + reactor-test

### 3.3 Forbidden Dependencies
- **MUST NOT** use Spring MVC (use WebFlux)
- **MUST NOT** use JPA/Hibernate (use R2DBC)
- **MUST NOT** use blocking JDBC drivers
- **MUST NOT** use Servlet API

---

## IV. Data Persistence

### 4.1 R2DBC Entities
- **MUST** use `@Table` and `@Column` annotations (not JPA's `@Entity`)
- **MUST** use `@Id` for primary keys (UUID preferred)
- **MUST** use `@Transient` for relationships not persisted directly
- **MUST** use `data class` for immutability
- **MUST** use `OffsetDateTime` for timestamps (UTC preferred)
- **SHOULD** use `copy()` method for updates

### 4.2 Repositories
- **MUST** extend `ReactiveCrudRepository<Entity, UUID>`
- **MUST** return `Mono<T>` for single results
- **MUST** return `Flux<T>` for collections
- **MUST** use `@Query` for custom SQL
- **SHOULD** extract complex queries to extension interfaces

### 4.3 Database Schema
- **MUST** version schema changes via numbered SQL files in `docker/init-scripts/`
- **MUST** use snake_case for table and column names
- **MUST** use JSONB for flexible metadata (prefer structured columns when possible)
- **MUST** use `INTEGER` (cents) for monetary values, never `DECIMAL` for currency
- **SHOULD** use indexes on foreign keys and frequently queried columns

---

## V. Resiliency Patterns

### 5.1 Resilience4j Instances
Currently defined: `shoppingCart`, `cartItem`, `product`, `category`

### 5.2 Rate Limiter Configuration
```properties
limitForPeriod=100              # Max 100 requests
limitRefreshPeriod=1s           # Per 1-second window
timeoutDuration=0s              # Fail immediately (no wait)
registerHealthIndicator=true    # Expose via /actuator/health
```

### 5.3 Retry Configuration
```properties
maxAttempts=3                                   # 3 total attempts
waitDuration=500ms                              # Initial backoff
enableExponentialBackoff=true                   # Enable exponential
exponentialBackoffMultiplier=2                  # 500ms, 1000ms, 2000ms
retryExceptions=IOException,TimeoutException,TransientDataAccessException
```

### 5.4 Circuit Breaker Configuration
```properties
slidingWindowSize=10                            # 10-call sliding window
minimumNumberOfCalls=5                          # Min 5 calls before evaluation
failureRateThreshold=50                         # 50% failure opens circuit
waitDurationInOpenState=5s                      # Wait 5s before HALF_OPEN
automaticTransitionFromOpenToHalfOpenEnabled=true
permittedNumberOfCallsInHalfOpenState=3         # Test with 3 calls
```

### 5.5 Annotation Stacking Order
```kotlin
@RateLimiter(name = "instance", fallbackMethod = "methodFallback")
@Retry(name = "instance", fallbackMethod = "methodFallback")
@CircuitBreaker(name = "instance", fallbackMethod = "methodFallback")
fun protectedMethod(param: Type): Mono<Result>
```

### 5.6 Fallback Method Pattern
```kotlin
private fun methodFallback(param: Type, ex: Exception): Mono<Result> {
    logger.error("Fallback triggered - param: $param, error: ${ex.message}", ex)
    return Mono.error(RuntimeException("User-friendly message. Please try again later.", ex))
}
```

### 5.7 Protected Operations
- **MUST** protect all `create*()` service methods
- **MUST** protect all `update*()` service methods
- **MUST** protect all `findById()` lookups in services
- **MAY** leave bulk read operations (`findAll()`, `search*()`) unprotected
- **SHOULD** protect operations with external dependencies (Pulsar, Vault)

---

## VI. REST API Design

### 6.1 Controller Patterns
- **MUST** use `@RestController` and `@RequestMapping`
- **MUST** inject services via constructor
- **MUST** delegate all business logic to services
- **MUST** return DTOs, not domain entities
- **MUST** use appropriate HTTP status codes (200, 201, 204, 400, 404, 500)
- **SHOULD** use `@Tag` and `@Operation` for OpenAPI documentation

### 6.2 HTTP Methods
- **GET:** Read operations, return 200 or 404
- **POST:** Create operations, return 201 with Location header
- **PUT:** Full update operations, return 200 or 204
- **PATCH:** Partial update operations, return 200 or 204
- **DELETE:** Delete operations, return 204

### 6.3 Request/Response DTOs
- **MUST** suffix request DTOs with `Request` (e.g., `CreateProductRequest`)
- **MUST** suffix response DTOs with `Response` (e.g., `ProductResponse`)
- **MUST** use `data class` for DTOs
- **MUST** validate input with Jakarta Bean Validation where appropriate
- **SHOULD** include only necessary fields in responses

### 6.4 Error Handling
- **MUST** implement global exception handler via `@RestControllerAdvice`
- **MUST** return structured error responses with message, timestamp, path
- **MUST** log errors with full stack trace
- **SHOULD** differentiate between client errors (4xx) and server errors (5xx)

---

## VII. Testing Standards

### 7.1 Test Organization
```
src/test/kotlin/
├── controller/     # Controller tests (@WebFluxTest)
├── service/        # Service tests (MockitoExtension)
├── repository/     # Repository tests (if needed)
└── fixtures/       # Shared test data (TestFixtures)
```

### 7.2 Service Tests
- **MUST** use `@ExtendWith(MockitoExtension::class)`
- **MUST** use `@Mock` for dependencies
- **MUST** use `StepVerifier` for reactive assertions
- **MUST** use `mockito-kotlin` helpers (`whenever`, `verify`, `any()`)
- **MUST** use `anyOrNull()` for nullable parameters
- **SHOULD** use `@MockitoSettings(strictness = LENIENT)` for complex scenarios
- **SHOULD** test both success and error paths
- **SHOULD** verify fallback behavior for resiliency-protected methods

### 7.3 Controller Tests
- **MUST** use `@WebFluxTest(ControllerClass::class)`
- **MUST** use `@MockBean` for service dependencies
- **MUST** use `WebTestClient` for HTTP assertions
- **MUST** use `any()` matchers, not `anyOrNull()`
- **MUST** verify service delegation with `verify()`
- **SHOULD** test all HTTP status codes
- **SHOULD** test request validation

### 7.4 Test Data
- **MUST** centralize test fixtures in `TestFixtures` object
- **MUST** use meaningful test data (realistic SKUs, names, prices)
- **MUST** use monetary values in cents (e.g., 199999 = $1999.99)
- **SHOULD** use `UUID.randomUUID()` for test IDs to avoid collisions

### 7.5 Coverage Goals
- **Target:** 214+ tests (Service: 86, Controller: 83, Integration: 1+)
- **MUST** test all CRUD operations
- **MUST** test all resiliency fallbacks
- **SHOULD** test edge cases (null values, empty collections, boundary conditions)

---

## VIII. Observability

### 8.1 Tracing
- **MUST** use Micrometer Tracing Bridge + OpenTelemetry
- **MUST** export traces via OTLP (HTTP or gRPC)
- **MUST** use W3C Trace Context propagation
- **MUST** sample 100% of traces in development (adjust for production)
- **SHOULD** configure trace backend via `management.otlp.tracing.endpoint`

### 8.2 Logging
- **MUST** use SLF4J with Logback
- **MUST** include `[appName,traceId,spanId]` in log pattern
- **MUST** log all errors with stack traces
- **MUST** log fallback activations at ERROR level
- **SHOULD** use appropriate log levels (ERROR, WARN, INFO, DEBUG)
- **SHOULD** avoid sensitive data in logs (PII, passwords, tokens)

### 8.3 Metrics
- **MUST** expose Actuator endpoints: `health`, `metrics`, `circuitbreakers`, `retries`, `ratelimiters`
- **MUST** enable health indicators for circuit breakers and rate limiters
- **SHOULD** monitor Resilience4j metrics via `/actuator/metrics/resilience4j.*`
- **SHOULD** enable Prometheus endpoint for production

### 8.4 Backend Selection
- **Default:** Jaeger (UI :16686, OTLP HTTP :4318)
- **Alternative:** SigNoz (UI :3301, OTLP HTTP :4320)
- **MUST** document how to switch backends in `application.properties`

---

## IX. Security & Secrets Management

### 9.1 Vault Integration
- **MUST** store secrets in Vault at `secret/resiliency-spike/{database,r2dbc,pulsar,application}`
- **MUST** fetch secrets on startup via Spring Cloud Vault
- **MUST** use `bootstrap.properties` for Vault configuration
- **MUST NOT** commit secrets to version control
- **SHOULD** use dev token `dev-root-token` for local development only

### 9.2 Database Credentials
- **MUST** inject via Vault: `${username}`, `${password}`, `${url}`
- **MUST** use connection pooling (R2DBC pool: initial=10, max=20)
- **SHOULD** rotate credentials periodically in production

### 9.3 Sensitive Data
- **MUST NOT** log passwords, tokens, or API keys
- **MUST** use HTTPS/TLS for external communication in production
- **SHOULD** encrypt sensitive fields in database (if required by compliance)

---

## X. Development Workflow

### 10.1 Build & Run
```bash
./gradlew clean build           # Clean build
./gradlew test                  # Run all tests
./gradlew bootRun               # Local development (requires infra)
./gradlew check                 # Tests + verification
```

### 10.2 Docker Compose Profiles
- **`infra`:** Postgres, Vault, Pulsar, Jaeger/SigNoz (for `bootRun`)
- **`app`:** Full stack including containerized Spring Boot app

```bash
# Infrastructure for local development
docker-compose --profile infra up -d

# Full containerized stack
docker-compose --profile app up -d --build
```

### 10.3 Container Image
- **MUST** use multi-stage build (Gradle 8.14.3 + JDK 21 builder)
- **MUST** use eclipse-temurin:21-jre-alpine for runtime (~423MB)
- **MUST** build via `docker build -f Containerfile -t resiliency-spike:latest .`
- **SHOULD** optimize layer caching (dependencies before source)

### 10.4 Code Style
- **MUST** use Kotlin idioms (`data class`, `copy()`, `?.`, `?:`)
- **MUST** use meaningful names (no single-letter variables except loops)
- **MUST** use constructor injection, not field injection
- **SHOULD** use `private val` for injected dependencies
- **SHOULD** use trailing commas in multi-line parameter lists

### 10.5 Git Workflow
- **MUST** commit to feature branches, merge via PR
- **MUST** write meaningful commit messages (imperative mood)
- **SHOULD** squash commits before merging to main
- **SHOULD** reference issue numbers in commit messages

---

## XI. Documentation

### 11.1 Code Documentation
- **MUST** use KDoc for public APIs
- **MUST** document non-obvious business logic
- **MUST** document fallback behavior
- **SHOULD** use `@param`, `@return`, `@throws` tags
- **MAY** omit obvious getters/setters

### 11.2 API Documentation
- **MUST** use SpringDoc OpenAPI annotations
- **MUST** expose Swagger UI at `/swagger-ui.html`
- **MUST** use `@Tag` to group endpoints
- **MUST** use `@Operation` to describe endpoints
- **SHOULD** use `@Parameter` and `@Schema` for detailed parameter docs

### 11.3 Project Documentation
- **MUST** maintain `CLAUDE.md` with project overview
- **MUST** document infrastructure in `OBSERVABILITY.md`, `SIGNOZ.md`, `CONTAINER.md`
- **MUST** update this CONSTITUTION when patterns change
- **SHOULD** include code examples in documentation

---

## XII. Performance & Scalability

### 12.1 Reactive Performance
- **MUST** avoid blocking operations in reactive chains
- **MUST** use `subscribeOn()` and `publishOn()` appropriately when blocking is unavoidable
- **SHOULD** use backpressure-aware operators
- **SHOULD** monitor subscriber demand

### 12.2 Database Optimization
- **MUST** use prepared statements (R2DBC default)
- **MUST** use connection pooling (configured in `application.properties`)
- **SHOULD** use indexes on frequently queried columns
- **SHOULD** paginate large result sets
- **SHOULD** avoid N+1 query problems

### 12.3 Caching Strategy
- **SHOULD** cache static reference data (categories)
- **SHOULD** use TTL for volatile data (product prices, stock)
- **MAY** use Redis for distributed caching in production

---

## XIII. Monetary Values

### 13.1 Storage
- **MUST** store monetary values as `INTEGER` in cents (e.g., 19999 = $199.99)
- **MUST NOT** use `DECIMAL` or floating-point types for currency
- **MUST** use `@Column` mapping to `Int` in Kotlin

### 13.2 Calculation
- **MUST** perform all calculations in cents
- **MUST** round consistently (banker's rounding for .5)
- **MUST** validate totals match sum of line items

### 13.3 Display
- **SHOULD** convert to decimal/string at API boundary only
- **SHOULD** use appropriate formatting (e.g., `$1,999.99`)
- **SHOULD** include currency code in international contexts

---

## XIV. Messaging & Events

### 14.1 Apache Pulsar
- **MUST** use reactive Pulsar templates
- **MUST** configure broker at `pulsar://localhost:6650`
- **MUST** handle backpressure in consumer flows
- **SHOULD** use topic naming convention: `persistent://tenant/namespace/topic`

### 14.2 Event Sourcing
- **MUST** record state transitions in `cart_state_history`
- **MUST** include event type, old state, new state, metadata, timestamp
- **SHOULD** use JSONB for flexible event metadata
- **SHOULD** publish domain events for external systems

---

## XV. Breaking Changes

### 15.1 When to Version
- **MUST** version API when breaking contract (remove field, change type)
- **SHOULD** deprecate old versions before removal
- **SHOULD** support N-1 version for migration period

### 15.2 Database Migrations
- **MUST** version schema changes via numbered SQL files
- **MUST** test migrations on copy of production data
- **MUST** support rollback for risky changes
- **SHOULD** use Flyway or Liquibase in production

---

## XVI. Prohibited Practices

### 16.1 Code Anti-Patterns
- **MUST NOT** use `Thread.sleep()` or blocking I/O in reactive code
- **MUST NOT** catch and swallow exceptions without logging
- **MUST NOT** use `System.out.println()` (use logger)
- **MUST NOT** use `!!` null assertion in Kotlin (handle nulls properly)
- **MUST NOT** use mutable collections in DTOs or entities

### 16.2 Configuration Anti-Patterns
- **MUST NOT** hardcode URLs, ports, credentials
- **MUST NOT** commit `.env` files or `application-local.properties` with secrets
- **MUST NOT** use default passwords in production
- **MUST NOT** disable security features in production

### 16.3 Testing Anti-Patterns
- **MUST NOT** use `Thread.sleep()` in tests (use `StepVerifier.withVirtualTime()`)
- **MUST NOT** depend on test execution order
- **MUST NOT** share mutable state between tests
- **MUST NOT** use production database for tests

---

## XVII. Code Review Checklist

### 17.1 Before Submitting PR
- [ ] All tests pass (`./gradlew test`)
- [ ] Code follows Kotlin style guide
- [ ] Resiliency annotations applied to new critical operations
- [ ] DTOs used for API contracts
- [ ] OpenAPI annotations added to new endpoints
- [ ] Error handling includes user-friendly messages
- [ ] Logging includes appropriate context
- [ ] No secrets committed
- [ ] Documentation updated (if applicable)

### 17.2 During Review
- [ ] Business logic is correct
- [ ] Reactive code is non-blocking
- [ ] Fallback methods implemented correctly
- [ ] Tests cover success and error paths
- [ ] No SQL injection vulnerabilities
- [ ] Performance implications considered
- [ ] Breaking changes documented

---

## XVIII. Enforcement

### 18.1 Automated Checks
- **MUST** pass `./gradlew check` before merge
- **SHOULD** use static analysis (Detekt, ktlint)
- **SHOULD** enforce test coverage thresholds

### 18.2 Peer Review
- **MUST** require at least one approval before merge
- **SHOULD** review for adherence to this constitution
- **SHOULD** reject changes that violate core principles

### 18.3 Continuous Improvement
- **SHOULD** update this constitution as patterns evolve
- **SHOULD** document lessons learned
- **SHOULD** share knowledge via team sessions

---

## XIX. Appendix: Quick Reference

### 19.1 Resilience4j Annotation Order
```kotlin
@RateLimiter(name = "instance", fallbackMethod = "fallback")
@Retry(name = "instance", fallbackMethod = "fallback")
@CircuitBreaker(name = "instance", fallbackMethod = "fallback")
```

### 19.2 Common Imports
```kotlin
// Reactive types
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

// R2DBC
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.data.annotation.{Id, Table, Column, Transient}

// Resilience4j
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.github.resilience4j.ratelimiter.annotation.RateLimiter

// Testing
import reactor.test.StepVerifier
import org.mockito.kotlin.{whenever, verify, any, anyOrNull}
```

### 19.3 Entity Template
```kotlin
@Table("table_name")
data class Entity(
    @Id val id: UUID? = null,
    @Column("column_name") val field: Type,
    @Column("created_at") val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column("updated_at") val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
```

### 19.4 Service Template
```kotlin
@Service
class SomeService(private val repository: SomeRepository) {
    private val logger = LoggerFactory.getLogger(SomeService::class.java)

    @RateLimiter(name = "instance", fallbackMethod = "operationFallback")
    @Retry(name = "instance", fallbackMethod = "operationFallback")
    @CircuitBreaker(name = "instance", fallbackMethod = "operationFallback")
    fun operation(param: Type): Mono<Result> {
        return repository.performOperation(param)
    }

    private fun operationFallback(param: Type, ex: Exception): Mono<Result> {
        logger.error("Fallback - param: $param, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Service temporarily unavailable.", ex))
    }
}
```

---

**End of Constitution**

_This constitution is a living document. When patterns emerge or evolve, update this document to reflect team consensus. All developers and AI agents must adhere to these principles to maintain consistency, quality, and reliability._
