# Claude Code Guide - CQRS Spike

**Kotlin 2.2.21** | **Spring Boot 4.0.0** | **WebFlux** | **R2DBC** | **PostgreSQL 18** | **Vault**

📖 **Read [CONSTITUTION.md](documentation/CONSTITUTION.md) for complete guidelines**

## Stack

- Kotlin 2.2.21, Spring Boot 4.0.0, Spring WebFlux (NOT MVC)
- R2DBC + PostgreSQL 18 (NOT JPA/Hibernate)
- HashiCorp Vault, Resilience4j, OpenTelemetry, HikariCP
- CQRS + Event Sourcing patterns

## Rules

**ALWAYS:** Kotlin 2.2.21 • `data class` • `lateinit var` • `String?` • `?.` • WebFlux • R2DBC • `Mono<T>` • `Flux<T>` • `.use` • `StepVerifier`

**NEVER:** Java • MVC • Servlet • JPA • `@Entity` • JDBC • `!!` • blocking ops • `Thread.sleep()`

```kotlin
// Service
@Service
class ProductService(private val repo: ProductRepository) {
    private val logger = LoggerFactory.getLogger(ProductService::class.java)
    fun findById(id: UUID): Mono<Product> = repo.findById(id)
}

// Entity (R2DBC, not JPA)
@Table("products")
data class Product(
    @Id val id: UUID? = null,
    @Column("name") val name: String,
    @Column("price_cents") val priceCents: Int
)

// Repository
interface ProductRepository : ReactiveCrudRepository<Product, UUID>

// Config
@Configuration
class DbConfig {
    @Value("\${spring.datasource.url}")
    private lateinit var url: String

    @Bean
    fun dataSource() = HikariDataSource(HikariConfig().apply { jdbcUrl = url })
}
```

**Packages:** `config/ controller/ domain/ dto/ exception/ infrastructure/{database,vault}/ repository/ service/`

## Vault

**Paths:** `secret/cqrs-spike/{database,api-keys,encryption,services}`

```kotlin
// Property injection
@Value("\${database.username}") private lateinit var user: String

// Programmatic
secretService.getSecret("cqrs-spike/api-keys", "external-api-key")
```

## Testing

```kotlin
// Service Test
@ExtendWith(MockitoExtension::class)
class ProductServiceTest {
    @Mock private lateinit var repo: ProductRepository
    @InjectMocks private lateinit var service: ProductService

    @Test
    fun `should find product`() {
        val id = UUID.randomUUID()
        whenever(repo.findById(id)).thenReturn(Mono.just(Product(id, "Test", 1999)))

        StepVerifier.create(service.findById(id))
            .expectNextMatches { it.name == "Test" }
            .verifyComplete()
    }
}

// Controller Test
@WebFluxTest(ProductController::class)
class ProductControllerTest {
    @Autowired private lateinit var client: WebTestClient
    @MockBean private lateinit var service: ProductService

    @Test
    fun `should return product`() {
        whenever(service.findById(any())).thenReturn(Mono.just(Product(UUID.randomUUID(), "Test", 1999)))

        client.get().uri("/api/products/{id}", UUID.randomUUID())
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.name").isEqualTo("Test")
    }
}
```

## Resiliency

**Order:** @RateLimiter → @Retry → @CircuitBreaker

```kotlin
@RateLimiter(name = "instance", fallbackMethod = "fallback")
@Retry(name = "instance", fallbackMethod = "fallback")
@CircuitBreaker(name = "instance", fallbackMethod = "fallback")
fun create(req: Request): Mono<Result> = repo.save(req.toEntity())

private fun fallback(req: Request, ex: Exception): Mono<Result> {
    logger.error("Fallback: ${ex.message}", ex)
    return Mono.error(RuntimeException("Service unavailable", ex))
}
```

```kotlin
reactor.core.publisher.{Mono,Flux}
org.springframework.data.repository.reactive.ReactiveCrudRepository
org.springframework.data.annotation.{Id,Table,Column}
io.github.resilience4j.{circuitbreaker,retry,ratelimiter}.annotation.*
reactor.test.StepVerifier
org.mockito.kotlin.{whenever,verify,any}
org.slf4j.LoggerFactory
```

## Schemas

**CQRS:** `event_store` (events) • `read_model` (queries) • `command_model` (writes)

**Event Store:** `event_stream(stream_id, aggregate_type, aggregate_id, version)` • `domain_event(event_id, stream_id, event_type, event_data JSONB, occurred_at)`

## Quick Ref

**Infra:** Vault :8200 • PostgreSQL :5432 • Jaeger :16686

**Start:** `docker-compose up -d vault && docker-compose up vault-init && docker-compose up -d postgres`

**Build:** `./gradlew build` • `./gradlew test` • `./gradlew bootRun`

**URLs:** http://localhost:8200/ui • jdbc:postgresql://localhost:5432/cqrs_db

## SDKMAN Setup

This project uses SDKMAN for Java version management. A `.sdkmanrc` file in the project root specifies the required Java version.

**Initialize SDKMAN:** `source "$HOME/.sdkman/bin/sdkman-init.sh"`

**Auto-switch to project Java version:** `sdk env` (reads `.sdkmanrc`)

**Install required version:** `sdk install java 24.0.2-amzn`

**Current requirement:** Java 24.0.2-amzn (Amazon Corretto)

## Checklists

**Service:** Kotlin • constructor injection • Mono/Flux • resiliency • fallback • logger • tests

**Entity:** @Table • @Id/@Column • data class • UUID • OffsetDateTime • Int (cents) • snake_case

**Controller:** @RestController • WebFlux • DTOs • delegate • OpenAPI • @WebFluxTest

## Known Issues

**Spring Boot 4.0 Actuator:** Health indicator classes unavailable. Use `curl http://localhost:8200/v1/sys/health` or VaultFailureHandler.

## AI Workflow

1. Check CONSTITUTION.md for patterns
2. Use Kotlin syntax (never Java)
3. Match existing code style
4. Use TodoWrite for tracking
5. Test incrementally
6. Update docs with issues/resolutions

**Docs:** [CONSTITUTION.md](documentation/CONSTITUTION.md) • [VAULT_SETUP.md](VAULT_SETUP.md) • [plans/](documentation/plans/)
