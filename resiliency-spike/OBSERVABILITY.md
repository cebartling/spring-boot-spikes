# Observability with OpenTelemetry

This document describes the observability setup for the Resiliency Spike project using OpenTelemetry and Jaeger for distributed tracing.

## Overview

The project is instrumented with OpenTelemetry through Spring Boot's Micrometer integration, providing:

- **Distributed Tracing** - Track requests across service boundaries
- **Automatic Instrumentation** - WebFlux, R2DBC, Pulsar, Resilience4j
- **Trace Context Propagation** - W3C Trace Context standard
- **Centralized Trace Storage** - Jaeger backend
- **Visual Trace Analysis** - Jaeger UI

## Architecture

```
┌──────────────────┐
│  Spring Boot App │
│   (Port 8080)    │
│                  │
│  OpenTelemetry   │
│  Auto-Instrument │
└────────┬─────────┘
         │
         │ OTLP/HTTP
         │ (Port 4318)
         ▼
┌──────────────────┐
│  Jaeger          │
│  All-in-One      │
│                  │
│  • Collector     │
│  • Query         │
│  • UI (16686)    │
└──────────────────┘
```

## Technology Stack

### OpenTelemetry Integration
- **Micrometer Tracing** - Spring Boot's observability abstraction
- **Micrometer OTel Bridge** - Bridges Micrometer to OpenTelemetry
- **OTLP Exporter** - Exports traces via OpenTelemetry Protocol

### Jaeger (All-in-One)
- **Version**: latest
- **Components**: Collector, Query Service, UI
- **Storage**: In-memory (for development)
- **Protocol**: OTLP over HTTP and gRPC

## What Gets Traced

### Automatic Instrumentation

Spring Boot automatically instruments the following:

1. **HTTP Requests (WebFlux)**
   - All incoming HTTP requests
   - Request method, path, status code
   - Request/response headers (configurable)
   - Query parameters

2. **Database Queries (R2DBC)**
   - All PostgreSQL queries
   - Query execution time
   - Connection acquisition
   - SQL statements (sanitized)

3. **Messaging (Pulsar)**
   - Message publishing
   - Message consumption
   - Topic information

4. **Resilience4j Patterns**
   - Circuit breaker state changes
   - Retry attempts
   - Rate limiter decisions
   - Fallback invocations

### Trace Information

Each trace includes:
- **Trace ID** - Unique identifier for the entire request flow
- **Span ID** - Unique identifier for each operation
- **Parent Span ID** - Links child spans to parents
- **Service Name** - `resiliency-spike`
- **Operation Name** - HTTP endpoint, DB query, etc.
- **Duration** - Operation execution time
- **Tags/Attributes** - Metadata (HTTP method, status, error info)
- **Events** - Important moments within a span

## Configuration

### Dependencies (build.gradle.kts)

```kotlin
// OpenTelemetry integration via Micrometer
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

### Application Properties

```properties
# Enable tracing
management.tracing.enabled=true
management.tracing.sampling.probability=1.0

# OTLP Exporter (Jaeger endpoint)
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.otlp.tracing.compression=gzip

# W3C Trace Context propagation
management.tracing.propagation.type=w3c

# Baggage propagation (custom context)
management.tracing.baggage.enabled=true
management.tracing.baggage.remote-fields=user-id,session-id

# Logging with trace context
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### Docker Compose Configuration

Jaeger runs as a service in both `infra` and `app` profiles:

```yaml
jaeger:
  profiles: ["infra", "app"]
  image: jaegertracing/all-in-one:latest
  ports:
    - "16686:16686"   # Jaeger UI
    - "4318:4318"     # OTLP HTTP receiver
    - "4317:4317"     # OTLP gRPC receiver
```

## Accessing Jaeger UI

### Local Development (Infrastructure Profile)

```bash
# Start infrastructure services including Jaeger
docker-compose --profile infra up -d

# Run Spring Boot application
./gradlew bootRun

# Access Jaeger UI
open http://localhost:16686
```

### Containerized Deployment (App Profile)

```bash
# Start everything in containers
docker-compose --profile app up -d --build

# Access Jaeger UI
open http://localhost:16686
```

## Using Jaeger UI

### Finding Traces

1. **Open Jaeger UI**: http://localhost:16686
2. **Select Service**: Choose `resiliency-spike` from the dropdown
3. **Search Options**:
   - **Operation**: Filter by specific endpoints (e.g., `GET /api/v1/products`)
   - **Tags**: Filter by attributes (e.g., `http.status_code=200`)
   - **Lookback**: Time range (last 1 hour, 3 hours, etc.)
   - **Min/Max Duration**: Filter by trace duration

### Analyzing a Trace

Each trace shows:

1. **Timeline View**
   - Visual representation of spans
   - Parent-child relationships
   - Duration of each operation
   - Overlapping/sequential operations

2. **Span Details**
   - Operation name
   - Duration and timestamps
   - Tags (HTTP method, status, URL)
   - Process information (service name, version)
   - Logs/Events within the span

3. **Trace Graph**
   - Service dependencies
   - Call flow visualization

### Example Trace Flow

A typical request to `GET /api/v1/products/1` shows:

```
Trace: GET /api/v1/products/1 (total: 45ms)
├─ HTTP GET /api/v1/products/1 (45ms)
│  ├─ ProductService.findProductById (40ms)
│  │  ├─ Circuit Breaker: product (1ms)
│  │  ├─ Rate Limiter: product (1ms)
│  │  └─ R2DBC Query: SELECT * FROM products WHERE id = $1 (38ms)
│  │     ├─ Connection Acquire (2ms)
│  │     ├─ Query Execute (35ms)
│  │     └─ Result Fetch (1ms)
│  └─ Response Serialization (5ms)
```

## Trace Context in Logs

With the configured logging pattern, all log messages include trace context:

```
2025-01-15 10:30:45.123  INFO [resiliency-spike,a1b2c3d4e5f6g7h8,i9j0k1l2m3n4o5p6] ...
                                  ^                ^               ^
                                  |                |               |
                              Service Name     Trace ID        Span ID
```

This allows you to:
- Correlate logs with traces
- Search logs by trace ID
- Find all logs for a specific request

## Sampling Configuration

Currently configured for **100% sampling** (all requests traced):

```properties
management.tracing.sampling.probability=1.0
```

For production, adjust based on traffic volume:
- `1.0` = 100% (trace everything)
- `0.1` = 10% (trace 1 in 10 requests)
- `0.01` = 1% (trace 1 in 100 requests)

## Monitoring Endpoints

### Actuator Endpoints

```bash
# Check if tracing is enabled
curl http://localhost:8080/actuator/health

# View metrics (includes trace metrics)
curl http://localhost:8080/actuator/metrics

# Prometheus metrics (if needed)
curl http://localhost:8080/actuator/prometheus
```

## Common Use Cases

### 1. Debugging Slow Requests

**Problem**: API endpoint is slow
**Solution**:
1. Open Jaeger UI
2. Filter by operation (e.g., `GET /api/v1/carts/{cartId}`)
3. Sort by duration (longest first)
4. Examine trace timeline to identify bottleneck:
   - Database query taking too long?
   - Multiple sequential queries (N+1 problem)?
   - Circuit breaker adding latency?
   - External service call?

### 2. Investigating Errors

**Problem**: Endpoint returning errors
**Solution**:
1. Filter traces by tag: `error=true` or `http.status_code=500`
2. Examine span events for exception details
3. Check if circuit breaker opened
4. Review retry attempts
5. Correlate with logs using trace ID

### 3. Understanding Circuit Breaker Behavior

**Problem**: Circuit breaker frequently opening
**Solution**:
1. Search for traces with circuit breaker spans
2. Look for patterns in failing requests
3. Check if failures cluster around specific times
4. Examine span tags for failure reasons
5. Correlate with rate limiter events

### 4. Analyzing Rate Limiter Impact

**Problem**: Requests being rate limited
**Solution**:
1. Filter by operation with rate limiter
2. Look for rate limiter rejection events
3. Identify peak traffic periods
4. Check if legitimate traffic or potential abuse
5. Adjust rate limits if needed

### 5. Database Query Performance

**Problem**: Database queries slow
**Solution**:
1. Filter traces with R2DBC spans
2. Sort by R2DBC span duration
3. Identify slow queries
4. Check for:
   - Missing indexes
   - Inefficient queries
   - Connection pool exhaustion
   - Lock contention

## Advanced Configuration

### Custom Spans (Manual Instrumentation)

While auto-instrumentation covers most cases, you can add custom spans:

```kotlin
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed

@Service
class MyService(
    private val observationRegistry: ObservationRegistry
) {
    @Observed(name = "business.operation", contextualName = "process-order")
    fun processOrder(orderId: Long) {
        // Automatically creates a span named "business.operation"
        // with contextual name "process-order"
    }

    fun complexOperation() {
        // Manual observation
        observationRegistry.observation()
            .name("complex.operation")
            .lowCardinalityKeyValue("operation.type", "batch")
            .observe {
                // Your code here
                performBatchOperation()
            }
    }
}
```

### Adding Custom Tags

```kotlin
import io.micrometer.tracing.Tracer

@Service
class CartService(
    private val tracer: Tracer
) {
    fun createCart(userId: String) {
        val currentSpan = tracer.currentSpan()
        currentSpan?.tag("user.id", userId)
        currentSpan?.tag("cart.type", "standard")

        // Your logic here
    }
}
```

### Baggage Propagation

Pass context across service boundaries:

```kotlin
import io.micrometer.tracing.BaggageInScope

@Service
class OrderService(
    private val tracer: Tracer
) {
    fun processOrder(userId: String) {
        tracer.createBaggage("user-id", userId).use {
            // user-id is now available in all downstream spans
            // and can be accessed in other services
            callInventoryService()
            callPaymentService()
        }
    }
}
```

## Production Considerations

### 1. Storage Backend

Jaeger all-in-one uses in-memory storage (not suitable for production).

**Production Options**:
- **Elasticsearch** - Recommended for production
- **Cassandra** - High scalability
- **Kafka** - Streaming ingestion

### 2. Sampling Strategy

Reduce overhead in high-traffic environments:

```properties
# Production: trace 10% of requests
management.tracing.sampling.probability=0.1
```

### 3. Data Retention

Configure trace retention policies based on compliance and storage:
- Development: 1-7 days
- Production: 7-30 days (depending on requirements)

### 4. Performance Impact

OpenTelemetry has minimal overhead:
- **Latency**: ~1-2ms per instrumented operation
- **CPU**: <1% additional usage
- **Memory**: ~10-20MB for agent

### 5. Security

**Considerations**:
- Don't trace sensitive data (passwords, tokens, PII)
- Sanitize SQL queries (parameterized queries)
- Configure header propagation carefully
- Secure Jaeger UI (authentication, HTTPS)

## Troubleshooting

### Traces Not Appearing in Jaeger

**Check**:
1. Jaeger is running: `docker-compose --profile infra ps`
2. Application can reach Jaeger: `curl http://localhost:4318/v1/traces`
3. Tracing is enabled in application.properties
4. Check application logs for OTLP export errors
5. Verify sampling probability is > 0

### Missing Spans

**Possible Causes**:
- Operation not auto-instrumented (needs manual instrumentation)
- Reactive chain not properly subscribed
- Error in span creation (check logs)
- Sampling filtered out the trace

### Performance Degradation

**Solutions**:
- Reduce sampling rate
- Disable trace export for health checks
- Check network latency to Jaeger
- Review span creation overhead

## Resources

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Spring Boot Observability](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.observability)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)

## Next Steps

Consider adding:
1. **Prometheus** for metrics collection
2. **Grafana** for metrics visualization
3. **Loki** for log aggregation
4. **Alert Manager** for alerting based on traces
5. **Service mesh** (Istio/Linkerd) for advanced observability
