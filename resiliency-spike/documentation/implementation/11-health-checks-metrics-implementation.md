# Technical Implementation: Health Checks and Metrics

**Feature Reference:** [11-health-checks-and-metrics.md](../features/11-health-checks-and-metrics.md)

**Implementation Date:** 2024-2025
**Status:** ✅ Complete

---

## Architecture

```mermaid
graph TB
    App[Spring Boot App] --> Actuator[Spring Boot Actuator]
    App --> Resilience[Resilience4j]

    Actuator --> Health[Health Endpoints]
    Actuator --> Metrics[Metrics Endpoints]

    Resilience --> CBMetrics[Circuit Breaker Metrics]
    Resilience --> RetryMetrics[Retry Metrics]
    Resilience --> RLMetrics[Rate Limiter Metrics]

    Health --> HealthChecks[/actuator/health]
    Metrics --> MetricsEndpoint[/actuator/metrics]
    CBMetrics --> CBEndpoint[/actuator/circuitbreakers]
    RetryMetrics --> RetryEndpoint[/actuator/retries]
    RLMetrics --> RLEndpoint[/actuator/ratelimiters]

    style Actuator fill:#9f9,stroke:#333,stroke-width:2px
    style Resilience fill:#ff9,stroke:#333,stroke-width:2px
```

---

## Dependencies

### build.gradle.kts

```kotlin
dependencies {
    // Spring Boot Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Resilience4j (auto-configures metrics)
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // Micrometer (metrics abstraction)
    // Auto-included by spring-boot-starter-actuator
}
```

---

## Configuration

### application.properties

```properties
# Expose Actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics,circuitbreakers,circuitbreakerevents,retries,retryevents,ratelimiters,ratelimiterevents,prometheus

# Health endpoint details
management.endpoint.health.show-details=always

# Enable health indicators
management.health.circuitbreakers.enabled=true
management.health.ratelimiters.enabled=true
```

---

## Health Endpoints

### /actuator/health

**URL:** http://localhost:8080/actuator/health

**Example Response:**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "shoppingCart": {
          "status": "UP",
          "details": {
            "state": "CLOSED",
            "failureRate": "0.0%",
            "slowCallRate": "0.0%",
            "bufferedCalls": 10,
            "failedCalls": 0,
            "slowCalls": 0,
            "notPermittedCalls": 0
          }
        },
        "product": {
          "status": "UP",
          "details": {
            "state": "CLOSED"
          }
        }
      }
    },
    "rateLimiters": {
      "status": "UP",
      "details": {
        "shoppingCart": {
          "status": "UP",
          "details": {
            "availablePermissions": 95,
            "numberOfWaitingThreads": 0
          }
        }
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500000000000,
        "free": 250000000000,
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Status Codes:**
- `UP`: All components healthy
- `DOWN`: At least one component down
- `OUT_OF_SERVICE`: Maintenance mode
- `UNKNOWN`: Status cannot be determined

### Health Indicators

**Auto-Registered:**
- `circuitBreakers`: Circuit breaker states
- `rateLimiters`: Rate limiter permit availability
- `diskSpace`: Free disk space
- `ping`: Always UP (liveness check)

**Kubernetes Integration:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

---

## Metrics Endpoints

### /actuator/metrics

**URL:** http://localhost:8080/actuator/metrics

**Response:**
```json
{
  "names": [
    "http.server.requests",
    "jvm.memory.used",
    "jvm.threads.live",
    "r2dbc.pool.acquired",
    "r2dbc.pool.idle",
    "resilience4j.circuitbreaker.calls",
    "resilience4j.circuitbreaker.state",
    "resilience4j.retry.calls",
    "resilience4j.ratelimiter.available.permissions",
    "system.cpu.usage"
  ]
}
```

### /actuator/metrics/{name}

**Example:** http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls

```json
{
  "name": "resilience4j.circuitbreaker.calls",
  "description": "The number of circuit breaker calls",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 150
    }
  ],
  "availableTags": [
    {
      "tag": "name",
      "values": ["shoppingCart", "cartItem", "product", "category"]
    },
    {
      "tag": "kind",
      "values": ["successful", "failed", "not_permitted"]
    }
  ]
}
```

**Filtered Query:**
```bash
curl "http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:shoppingCart&tag=kind:successful"
```

```json
{
  "name": "resilience4j.circuitbreaker.calls",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 98
    }
  ],
  "availableTags": []
}
```

---

## Resilience4j Endpoints

### /actuator/circuitbreakers

**URL:** http://localhost:8080/actuator/circuitbreakers

**Response:**
```json
{
  "circuitBreakers": {
    "shoppingCart": {
      "state": "CLOSED",
      "failureRate": "5.0%",
      "slowCallRate": "0.0%",
      "bufferedCalls": 20,
      "failedCalls": 1,
      "slowCalls": 0,
      "notPermittedCalls": 0
    },
    "cartItem": {
      "state": "HALF_OPEN",
      "failureRate": "33.3%",
      "bufferedCalls": 3,
      "failedCalls": 1,
      "notPermittedCalls": 5
    },
    "product": {
      "state": "CLOSED",
      "failureRate": "0.0%",
      "bufferedCalls": 10,
      "failedCalls": 0
    }
  }
}
```

### /actuator/circuitbreakerevents

**Recent events:**
```json
{
  "circuitBreakerEvents": [
    {
      "circuitBreakerName": "cartItem",
      "type": "STATE_TRANSITION",
      "creationTime": "2025-11-22T10:15:30.123Z",
      "stateTransition": "CLOSED_TO_OPEN",
      "elapsedDuration": "PT0.045S"
    },
    {
      "circuitBreakerName": "cartItem",
      "type": "ERROR",
      "creationTime": "2025-11-22T10:15:25.987Z",
      "errorMessage": "Connection timeout",
      "elapsedDuration": "PT2.001S"
    }
  ]
}
```

### /actuator/retries

**URL:** http://localhost:8080/actuator/retries

**Response:**
```json
{
  "retries": {
    "shoppingCart": {
      "maxAttempts": 3,
      "successfulCallsWithRetry": 8,
      "successfulCallsWithoutRetry": 142,
      "failedCallsWithRetry": 2,
      "failedCallsWithoutRetry": 1
    }
  }
}
```

### /actuator/ratelimiters

**URL:** http://localhost:8080/actuator/ratelimiters

**Response:**
```json
{
  "rateLimiters": {
    "shoppingCart": {
      "availablePermissions": 87,
      "numberOfWaitingThreads": 0
    },
    "cartItem": {
      "availablePermissions": 0,
      "numberOfWaitingThreads": 3
    }
  }
}
```

---

## Prometheus Integration

### /actuator/prometheus

**Enable in application.properties:**
```properties
management.endpoints.web.exposure.include=prometheus
```

**URL:** http://localhost:8080/actuator/prometheus

**Example Output:**
```
# HELP resilience4j_circuitbreaker_calls_total The number of circuit breaker calls
# TYPE resilience4j_circuitbreaker_calls_total counter
resilience4j_circuitbreaker_calls_total{kind="successful",name="shoppingCart",} 98.0
resilience4j_circuitbreaker_calls_total{kind="failed",name="shoppingCart",} 2.0
resilience4j_circuitbreaker_calls_total{kind="not_permitted",name="shoppingCart",} 0.0

# HELP resilience4j_circuitbreaker_state Circuit Breaker State (0-CLOSED, 1-OPEN, 2-HALF_OPEN)
# TYPE resilience4j_circuitbreaker_state gauge
resilience4j_circuitbreaker_state{name="shoppingCart",} 0.0
resilience4j_circuitbreaker_state{name="cartItem",} 2.0

# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="GET",status="200",uri="/api/v1/products",} 150.0
http_server_requests_seconds_sum{method="GET",status="200",uri="/api/v1/products",} 2.5

# HELP r2dbc_pool_acquired_total Number of connections acquired
# TYPE r2dbc_pool_acquired_total counter
r2dbc_pool_acquired_total{name="connectionFactory",} 500.0
```

**Prometheus Scrape Config:**
```yaml
scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

---

## Custom Metrics (Not Implemented)

**Example custom business metrics:**

```kotlin
@Service
class ProductService(
    private val meterRegistry: MeterRegistry
) {
    private val productCreationCounter = meterRegistry.counter("products.created")
    private val productPriceGauge = meterRegistry.gauge("products.price.avg", AtomicDouble(0.0))!!

    fun createProduct(product: Product): Mono<Product> {
        return productRepository.save(product)
            .doOnSuccess {
                productCreationCounter.increment()
                updateAveragePrice()
            }
    }
}
```

---

## Monitoring Stack Integration

### Prometheus + Grafana

```yaml
# docker-compose.yml
prometheus:
  image: prom/prometheus:latest
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana:latest
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
```

**Grafana Dashboard:**
- Import dashboard ID: 11378 (Spring Boot 2.x)
- Or create custom dashboard with:
  - Circuit breaker states
  - HTTP request rates
  - JVM memory usage
  - R2DBC connection pool

---

## Alternative Implementations

### 1. **Micrometer + Datadog**

```kotlin
dependencies {
    implementation("io.micrometer:micrometer-registry-datadog")
}
```

```properties
management.metrics.export.datadog.api-key=${DATADOG_API_KEY}
management.metrics.export.datadog.enabled=true
```

### 2. **Micrometer + New Relic**

```kotlin
dependencies {
    implementation("io.micrometer:micrometer-registry-new-relic")
}
```

### 3. **Custom Health Indicator**

```kotlin
@Component
class DatabaseHealthIndicator(private val r2dbcEntityTemplate: R2dbcEntityTemplate) : HealthIndicator {

    override fun health(): Health {
        return r2dbcEntityTemplate.databaseClient
            .sql("SELECT 1")
            .fetch()
            .one()
            .map { Health.up().withDetail("database", "reachable").build() }
            .onErrorResume { Health.down(it).build().toMono() }
            .block()!!
    }
}
```

---

## Security Considerations

### Production Configuration

```properties
# Require authentication for actuator endpoints
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized

# Prometheus endpoint restricted
management.endpoints.web.exposure.exclude=prometheus
```

**Spring Security Integration:**
```kotlin
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .authorizeExchange()
            .pathMatchers("/actuator/health").permitAll()
            .pathMatchers("/actuator/**").hasRole("ADMIN")
            .and()
            .build()
    }
}
```

---

## Production Readiness

- [x] Health endpoints (liveness/readiness)
- [x] Metrics endpoints (Micrometer)
- [x] Circuit breaker metrics
- [x] Retry metrics
- [x] Rate limiter metrics
- [x] Prometheus export
- [ ] Custom business metrics
- [ ] Grafana dashboards
- [ ] Alerting rules (Prometheus Alertmanager)
- [ ] Authentication for actuator endpoints
- [ ] Metrics retention and aggregation
- [ ] Real-time monitoring dashboards
