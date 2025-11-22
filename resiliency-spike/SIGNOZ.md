# SigNoz Observability Platform

This document describes the SigNoz setup for the Resiliency Spike project, providing comprehensive Application Performance Monitoring (APM), distributed tracing, metrics, and log management.

## Overview

SigNoz is an open-source observability platform that provides:

- **Distributed Tracing** - Track requests across service boundaries with detailed timing
- **Application Metrics** - Monitor service performance, throughput, latency, error rates
- **Service Maps** - Visualize service dependencies and communication patterns
- **Exceptions Tracking** - Centralized exception monitoring and analysis
- **Alerts** - Configure alerts based on metrics, traces, and logs
- **Dashboards** - Customizable dashboards for metrics visualization
- **Log Management** - Aggregate, search, and analyze application logs

## Architecture

```
┌──────────────────────┐
│   Spring Boot App    │
│    (Port 8080)       │
│                      │
│  OpenTelemetry       │
│  Auto-Instrument     │
└─────────┬────────────┘
          │
          │ OTLP/HTTP (Port 4320)
          │ OTLP/gRPC (Port 4319)
          ▼
┌──────────────────────┐
│  SigNoz OTel         │
│  Collector           │
│  (signoz-otel-       │
│   collector)         │
└─────────┬────────────┘
          │
          │ TCP (Port 9000)
          ▼
┌──────────────────────┐      ┌──────────────────────┐
│  ClickHouse          │◄─────┤  SigNoz Query        │
│  Database            │      │  Service             │
│  (signoz-clickhouse) │      │  (Port 8085)         │
└──────────────────────┘      └─────────┬────────────┘
                                        │
                                        │ HTTP API
                                        ▼
                              ┌──────────────────────┐
                              │  SigNoz Frontend     │
                              │  UI (Port 3301)      │
                              └──────────────────────┘
```

## Technology Stack

### SigNoz Components

1. **SigNoz OTel Collector** (v0.88.11)
   - Receives traces, metrics, and logs via OTLP protocol
   - Processes and batches telemetry data
   - Exports data to ClickHouse database
   - Custom configuration for memory limits and resource attributes

2. **SigNoz Query Service** (v0.42.0)
   - Queries ClickHouse for trace, metric, and log data
   - Provides REST API for frontend
   - Handles data aggregation and analysis
   - Service dependency graph generation

3. **SigNoz Frontend** (v0.42.0)
   - React-based web UI
   - APM dashboards and service maps
   - Trace visualization and analysis
   - Metrics dashboards and alerts management

4. **ClickHouse Database** (v23.11-alpine)
   - Columnar database optimized for analytics
   - Stores traces, metrics, and logs
   - High-performance queries for large datasets
   - Persistent storage in Docker volume

## Port Mappings

| Service | Internal Port | External Port | Purpose |
|---------|--------------|---------------|---------|
| SigNoz Frontend | 3301 | 3301 | Web UI |
| SigNoz Query Service | 8085 | 8085 | Backend API |
| SigNoz OTel Collector | 4317 | 4319 | OTLP gRPC receiver |
| SigNoz OTel Collector | 4318 | 4320 | OTLP HTTP receiver |
| SigNoz OTel Collector | 13133 | - | Health check endpoint |
| ClickHouse | 9000 | 9000 | Native protocol |
| ClickHouse | 8123 | 8123 | HTTP interface |

**Note:** SigNoz OTLP ports are mapped to 4319/4320 to avoid conflicts with Jaeger (4317/4318).

## Configuration

### Docker Compose Services

All SigNoz services are configured in `docker-compose.yml` with both `infra` and `app` profiles.

**Service Dependencies:**
- `signoz-clickhouse` - Base database (no dependencies)
- `signoz-query-service` - Depends on ClickHouse being healthy
- `signoz-otel-collector` - Depends on ClickHouse being healthy
- `signoz-frontend` - Depends on Query Service being healthy

**Health Checks:**
- All services include health checks to ensure proper startup order
- Health check intervals: 10s
- Start periods: 20-30s depending on service

### Application Configuration

#### Local Development (Spring Boot running on host)

In `src/main/resources/application.properties`:

```properties
# Enable tracing
management.tracing.enabled=true
management.tracing.sampling.probability=1.0

# SigNoz OTLP endpoint (HTTP)
management.otlp.tracing.endpoint=http://localhost:4320/v1/traces
management.otlp.tracing.compression=gzip

# W3C Trace Context propagation
management.tracing.propagation.type=w3c

# Baggage propagation
management.tracing.baggage.enabled=true
management.tracing.baggage.remote-fields=user-id,session-id
```

#### Containerized Deployment (Full stack with app profile)

In `docker-compose.yml`, update the Spring Boot app environment:

```yaml
environment:
  - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://signoz-otel-collector:4318/v1/traces
```

### OTel Collector Configuration

Located at `docker/signoz/otel-collector-config.yaml`:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024

  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128

exporters:
  clickhousetraces:
    datasource: tcp://signoz-clickhouse:9000/?database=signoz

  clickhousemetricswrite:
    endpoint: tcp://signoz-clickhouse:9000/?database=signoz

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [clickhousetraces]

    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [clickhousemetricswrite]
```

## Running SigNoz

### Option 1: Infrastructure Profile (Local Development)

Start SigNoz services while running Spring Boot locally:

```bash
# Start all infrastructure services including SigNoz
docker-compose --profile infra up -d

# Wait for all services to be healthy (check with)
docker-compose --profile infra ps

# Update application.properties to use SigNoz
# Uncomment the SigNoz endpoint:
# management.otlp.tracing.endpoint=http://localhost:4320/v1/traces

# Run Spring Boot application locally
./gradlew bootRun

# Access SigNoz UI
open http://localhost:3301
```

### Option 2: Full Containerized Stack

Run everything in containers:

```bash
# Update docker-compose.yml to use SigNoz endpoint
# Uncomment in the resiliency-spike-app service:
# - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://signoz-otel-collector:4318/v1/traces

# Start full stack
docker-compose --profile app up -d --build

# Monitor logs
docker-compose --profile app logs -f

# Access SigNoz UI
open http://localhost:3301
```

### Verifying SigNoz is Running

```bash
# Check service health
docker-compose --profile infra ps

# View SigNoz logs
docker-compose --profile infra logs -f signoz-frontend
docker-compose --profile infra logs -f signoz-query-service
docker-compose --profile infra logs -f signoz-otel-collector
docker-compose --profile infra logs -f signoz-clickhouse

# Test OTel Collector health
curl http://localhost:13133/

# Test Query Service health
curl http://localhost:8085/api/v1/health

# Access SigNoz UI
open http://localhost:3301
```

## Using SigNoz UI

### First-Time Setup

1. **Open SigNoz UI**: http://localhost:3301
2. **Create Account** (first time only):
   - Email: your-email@example.com
   - Password: your-password
   - Organization: resiliency-spike

### Navigation Overview

**Main Sections:**
- **Services** - APM metrics for each service (latency, throughput, error rates)
- **Traces** - Distributed trace search and analysis
- **Metrics** - Custom metrics dashboards
- **Logs** - Log aggregation and search
- **Alerts** - Alert rules and notification channels
- **Dashboards** - Custom dashboards

### Viewing Application Traces

1. **Navigate to Services**:
   - Click "Services" in left sidebar
   - Find service: `resiliency-spike`
   - View key metrics:
     - **P99 Latency** - 99th percentile response time
     - **Error Rate** - Percentage of failed requests
     - **Operations per Second** - Request throughput
     - **Apdex Score** - User satisfaction metric

2. **Analyze a Specific Endpoint**:
   - Click on service name: `resiliency-spike`
   - View operation breakdown (e.g., `GET /api/v1/products`)
   - See latency distribution, error rates, RPS

3. **Search for Traces**:
   - Navigate to "Traces" section
   - Filter by:
     - **Service Name**: resiliency-spike
     - **Operation**: GET /api/v1/products/1
     - **Status**: Error, OK
     - **Duration**: > 100ms
     - **Time Range**: Last 15 minutes, 1 hour, custom
   - Click on a trace to view details

### Trace Detail View

When you click on a specific trace, you'll see:

1. **Trace Timeline**:
   - Visual representation of all spans
   - Parent-child relationships
   - Duration of each operation
   - Sequential vs. parallel execution

2. **Span Details**:
   - Operation name (e.g., `GET /api/v1/products/1`)
   - Duration and timestamps
   - Span attributes/tags:
     - `http.method`: GET
     - `http.url`: /api/v1/products/1
     - `http.status_code`: 200
     - `db.system`: postgresql
     - `db.statement`: SELECT * FROM products WHERE id = $1

3. **Events**:
   - Exception events with stack traces
   - Circuit breaker state changes
   - Rate limiter decisions
   - Retry attempts

### Service Map

1. **Navigate to Service Map**:
   - Click on "Services" → Select service → "Service Map" tab
   - Visual graph of service dependencies
   - Shows database connections (PostgreSQL)
   - Displays external service calls

2. **Analyze Dependencies**:
   - Node size indicates request volume
   - Color indicates health (green = healthy, red = errors)
   - Click nodes to view detailed metrics

### Creating Dashboards

1. **Navigate to Dashboards** → "New Dashboard"

2. **Add Panels**:
   - **Latency Panel**:
     - Metric: `http.server.duration`
     - Aggregation: P99, P95, P50
     - Filter: service.name = resiliency-spike

   - **Error Rate Panel**:
     - Metric: `http.server.request.count`
     - Aggregation: Rate
     - Filter: http.status_code >= 400

   - **Database Query Time**:
     - Metric: `db.client.duration`
     - Aggregation: P99
     - Filter: db.system = postgresql

3. **Save Dashboard**:
   - Name: "Resiliency Spike Metrics"
   - Add to favorites for quick access

### Setting Up Alerts

1. **Navigate to Alerts** → "New Alert"

2. **Example Alert: High Error Rate**:
   ```
   Alert Name: High Error Rate
   Metric: http.server.request.count
   Condition: error rate > 5%
   Duration: 5 minutes
   Severity: Critical
   Notification: Email/Slack
   ```

3. **Example Alert: Slow Endpoint**:
   ```
   Alert Name: Slow Product API
   Metric: http.server.duration
   Condition: P99 > 500ms
   Filter: http.route = /api/v1/products/{id}
   Duration: 3 minutes
   Severity: Warning
   ```

4. **Example Alert: Circuit Breaker Open**:
   ```
   Alert Name: Circuit Breaker Triggered
   Metric: resilience4j.circuitbreaker.state
   Condition: state = OPEN
   Duration: 1 minute
   Severity: Critical
   ```

## What Gets Traced in SigNoz

### Automatic Instrumentation

SigNoz captures all OpenTelemetry traces including:

1. **HTTP Requests (WebFlux)**:
   - Request method, path, query parameters
   - Response status code, size
   - Request duration
   - Error details and exceptions

2. **Database Queries (R2DBC)**:
   - SQL statements (parameterized)
   - Query execution time
   - Connection pool metrics
   - Database name and table

3. **Messaging (Pulsar)**:
   - Message publish/consume operations
   - Topic information
   - Message size and headers

4. **Resilience4j Patterns**:
   - Circuit breaker state transitions
   - Retry attempts with exponential backoff
   - Rate limiter accept/reject decisions
   - Bulkhead concurrency limits

### Additional Metrics

SigNoz automatically collects Spring Boot Actuator metrics via the Prometheus endpoint:

```properties
# In application.properties
management.endpoints.web.exposure.include=prometheus
```

Metrics include:
- JVM memory, GC, threads
- HTTP request rates, latencies
- Database connection pool stats
- Resilience4j circuit breaker, retry, rate limiter metrics

## Common Use Cases

### 1. Debugging Slow Requests

**Scenario**: API endpoint is responding slowly

**Steps**:
1. Navigate to **Services** → `resiliency-spike`
2. Sort operations by **P99 Latency** (descending)
3. Click on slow operation (e.g., `GET /api/v1/carts/{cartId}`)
4. Click **"View Traces"** → Sort by duration
5. Open slowest trace
6. Examine timeline to find bottleneck:
   - Database query taking too long?
   - Multiple sequential queries (N+1)?
   - Circuit breaker adding latency?
   - External service timeout?

**SigNoz Advantages**:
- Flamegraph view shows nested spans clearly
- Automatic detection of slow database queries
- Side-by-side comparison of fast vs. slow traces

### 2. Investigating Errors

**Scenario**: 500 errors reported by users

**Steps**:
1. Navigate to **Traces**
2. Filter by:
   - Service: `resiliency-spike`
   - Status: `Error`
   - Time range: Last 1 hour
3. Group by operation to find problematic endpoint
4. Click on error trace
5. Examine **Events** tab for exception stack trace
6. Check span attributes for context (user ID, request parameters)
7. Use trace ID to search logs for additional context

**SigNoz Advantages**:
- Exceptions dashboard with error grouping
- Exception count and error rate metrics
- Direct navigation from error to related logs

### 3. Monitoring Circuit Breakers

**Scenario**: Circuit breaker frequently opening

**Steps**:
1. Navigate to **Services** → `resiliency-spike`
2. Create custom dashboard with circuit breaker metrics:
   - `resilience4j.circuitbreaker.state`
   - `resilience4j.circuitbreaker.failure.rate`
   - `resilience4j.circuitbreaker.calls`
3. Filter by circuit breaker name (shoppingCart, product, etc.)
4. Set up alert for `state = OPEN`
5. Examine traces during circuit breaker events
6. Identify root cause (database timeout, external service failure)

**SigNoz Advantages**:
- Real-time circuit breaker state visualization
- Correlation between circuit breaker events and error traces
- Historical analysis of circuit breaker patterns

### 4. Analyzing Database Performance

**Scenario**: Database queries causing bottlenecks

**Steps**:
1. Navigate to **Services** → `resiliency-spike`
2. Filter traces with database spans
3. Sort database operations by duration
4. Examine slow queries:
   - Missing indexes?
   - Full table scans?
   - Lock contention?
5. Check database connection pool metrics:
   - Pool exhaustion?
   - High wait times?

**SigNoz Advantages**:
- Dedicated database dashboard
- Query performance breakdown by operation
- Connection pool metrics visualization
- N+1 query detection

### 5. Service Dependency Analysis

**Scenario**: Understanding service architecture

**Steps**:
1. Navigate to **Service Map**
2. View visual graph of dependencies:
   - Spring Boot app → PostgreSQL
   - Spring Boot app → Apache Pulsar
   - (Future: Spring Boot app → External APIs)
3. Click on edges to see call metrics
4. Identify single points of failure
5. Plan for resilience improvements

**SigNoz Advantages**:
- Automatic service map generation
- Real-time dependency health status
- Request volume visualization

### 6. Capacity Planning

**Scenario**: Planning for traffic growth

**Steps**:
1. Create dashboard with key metrics:
   - Requests per second (RPS)
   - P99 latency trend over time
   - CPU and memory usage
   - Database connection pool utilization
   - Error rate percentage
2. Analyze patterns:
   - Peak traffic hours
   - Seasonal trends
   - Resource saturation points
3. Set alerts for capacity thresholds
4. Plan scaling strategy

**SigNoz Advantages**:
- Long-term metrics retention in ClickHouse
- Customizable dashboards
- Percentile aggregations over time
- Resource utilization correlation

## Advanced Features

### Custom Instrumentation

Add custom spans for business logic:

```kotlin
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed

@Service
class OrderService(
    private val observationRegistry: ObservationRegistry
) {
    @Observed(name = "order.process", contextualName = "process-order")
    fun processOrder(orderId: Long) {
        // This method is automatically traced
        // Span name: "order.process"
        // Span display name: "process-order"
    }

    fun complexBatchOperation() {
        observationRegistry.observation()
            .name("batch.operation")
            .lowCardinalityKeyValue("operation.type", "bulk-insert")
            .highCardinalityKeyValue("batch.size", "1000")
            .observe {
                // Your batch operation code
                performBulkInsert()
            }
    }
}
```

### Adding Custom Attributes

```kotlin
import io.micrometer.tracing.Tracer

@Service
class CartService(
    private val tracer: Tracer
) {
    fun createCart(userId: String, sessionId: String) {
        val currentSpan = tracer.currentSpan()
        currentSpan?.tag("user.id", userId)
        currentSpan?.tag("session.id", sessionId)
        currentSpan?.tag("cart.type", "standard")

        // These tags will appear in SigNoz trace attributes
        // and can be used for filtering and aggregation
    }
}
```

### Baggage for Context Propagation

```kotlin
import io.micrometer.tracing.BaggageInScope

@Service
class CheckoutService(
    private val tracer: Tracer
) {
    fun processCheckout(userId: String, cartId: String) {
        tracer.createBaggage("user.id", userId).use {
            tracer.createBaggage("cart.id", cartId).use {
                // user.id and cart.id are now available in all downstream spans
                // across service boundaries
                validateCart()
                processPayment()
                confirmOrder()
            }
        }
    }
}
```

## Comparison: SigNoz vs. Jaeger

| Feature | SigNoz | Jaeger |
|---------|--------|--------|
| **Distributed Tracing** | ✅ Full support | ✅ Full support |
| **APM Metrics** | ✅ Built-in | ❌ Requires Prometheus |
| **Service Maps** | ✅ Built-in | ✅ Basic |
| **Log Management** | ✅ Built-in | ❌ Requires separate tool |
| **Custom Dashboards** | ✅ Rich UI | ❌ Limited |
| **Alerts** | ✅ Built-in | ❌ Requires separate tool |
| **Metrics Correlation** | ✅ Native | ⚠️ Manual |
| **Exception Tracking** | ✅ Dedicated view | ⚠️ Basic |
| **Long-term Storage** | ✅ ClickHouse (efficient) | ⚠️ Elasticsearch/Cassandra |
| **Resource Usage** | Higher (ClickHouse) | Lower (in-memory) |
| **Setup Complexity** | Medium (4 services) | Low (1 service) |
| **Production Ready** | ✅ Yes (with ClickHouse) | ✅ Yes (with backend) |
| **Open Source** | ✅ Yes (MIT) | ✅ Yes (Apache 2.0) |

**When to use SigNoz**:
- Need comprehensive APM in addition to tracing
- Want unified observability (traces + metrics + logs)
- Require custom dashboards and alerts
- Long-term metrics retention with efficient storage

**When to use Jaeger**:
- Only need distributed tracing
- Prefer simpler deployment
- Lower resource requirements
- Already have separate metrics/logging solutions

## Production Considerations

### 1. ClickHouse Storage Configuration

For production, configure ClickHouse with persistent storage and replication:

```yaml
signoz-clickhouse:
  image: clickhouse/clickhouse-server:23.11-alpine
  volumes:
    - signoz-clickhouse-data:/var/lib/clickhouse
    - ./docker/signoz/clickhouse-config.xml:/etc/clickhouse-server/config.xml:ro
  environment:
    - CLICKHOUSE_DB=signoz
    - CLICKHOUSE_USER=signoz
    - CLICKHOUSE_PASSWORD=${CLICKHOUSE_PASSWORD}  # Use secrets management
```

### 2. Data Retention Policies

Configure retention in ClickHouse:

```sql
-- Traces: 7 days
ALTER TABLE signoz_traces.signoz_index_v2
  MODIFY TTL toDateTime(timestamp) + INTERVAL 7 DAY;

-- Metrics: 30 days
ALTER TABLE signoz_metrics.samples
  MODIFY TTL toDateTime(unix_milli) + INTERVAL 30 DAY;

-- Logs: 14 days
ALTER TABLE signoz_logs.logs
  MODIFY TTL toDateTime(timestamp) + INTERVAL 14 DAY;
```

### 3. Sampling Strategy

Reduce trace volume in high-traffic production:

```properties
# Production: sample 10% of requests
management.tracing.sampling.probability=0.1

# Or use head-based sampling in OTel Collector config
```

### 4. Resource Limits

Configure resource limits for production:

```yaml
signoz-clickhouse:
  deploy:
    resources:
      limits:
        cpus: '4'
        memory: 8G
      reservations:
        cpus: '2'
        memory: 4G

signoz-otel-collector:
  deploy:
    resources:
      limits:
        cpus: '1'
        memory: 2G
      reservations:
        cpus: '0.5'
        memory: 1G
```

### 5. Security Hardening

- **ClickHouse**: Enable TLS, configure user authentication
- **SigNoz UI**: Add reverse proxy with authentication (Nginx, Traefik)
- **Secrets**: Use environment variables or secrets management (Vault)
- **Network**: Restrict access using firewall rules or network policies

### 6. High Availability

For production HA:

- **ClickHouse Cluster**: Run replicated ClickHouse cluster (3+ nodes)
- **Query Service**: Run multiple instances behind load balancer
- **OTel Collector**: Run multiple collector instances
- **Frontend**: Run multiple frontend instances

### 7. Monitoring SigNoz Itself

Monitor SigNoz components:

```bash
# ClickHouse metrics
curl http://localhost:8123/metrics

# OTel Collector metrics
curl http://localhost:13133/metrics

# Query Service health
curl http://localhost:8085/api/v1/health
```

## Troubleshooting

### Traces Not Appearing in SigNoz

**Check**:
1. SigNoz services are running:
   ```bash
   docker-compose --profile infra ps
   ```

2. Application is sending to correct endpoint:
   ```properties
   management.otlp.tracing.endpoint=http://localhost:4320/v1/traces
   ```

3. OTel Collector is receiving data:
   ```bash
   docker-compose logs signoz-otel-collector | grep "TracesExporter"
   ```

4. ClickHouse is healthy:
   ```bash
   curl http://localhost:8123/ping
   ```

5. Check application logs for OTLP export errors

### SigNoz UI Not Loading

**Check**:
1. Frontend service is running:
   ```bash
   docker-compose --profile infra ps signoz-frontend
   ```

2. Query Service is reachable:
   ```bash
   curl http://localhost:8085/api/v1/health
   ```

3. Check frontend logs:
   ```bash
   docker-compose logs signoz-frontend
   ```

4. Browser console for JavaScript errors

### ClickHouse Connection Errors

**Check**:
1. ClickHouse service health:
   ```bash
   docker-compose --profile infra ps signoz-clickhouse
   ```

2. Database initialized:
   ```bash
   docker exec -it resiliency-spike-signoz-clickhouse clickhouse-client --query "SHOW DATABASES"
   ```

3. Credentials correct in Query Service and OTel Collector

4. Check ClickHouse logs:
   ```bash
   docker-compose logs signoz-clickhouse
   ```

### High Memory Usage

**Solutions**:
1. Reduce sampling rate in application.properties
2. Configure memory limits in OTel Collector config
3. Adjust ClickHouse memory settings
4. Implement TTL policies for data retention

### Missing Spans

**Possible Causes**:
- Reactive chain not subscribed (WebFlux issue)
- Manual instrumentation error
- Sampling filtered out the trace
- OTel Collector dropped spans (check logs)

## Resources

- [SigNoz Documentation](https://signoz.io/docs/)
- [SigNoz GitHub](https://github.com/SigNoz/signoz)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [ClickHouse Documentation](https://clickhouse.com/docs/)
- [Spring Boot Observability](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.observability)

## Next Steps

1. **Enable Metrics Collection**:
   - Expose Prometheus endpoint in Spring Boot
   - Configure OTel Collector to scrape metrics
   - Create custom metrics dashboards

2. **Add Log Forwarding**:
   - Configure logback to export logs via OTLP
   - Correlate logs with traces using trace ID
   - Set up log-based alerts

3. **Advanced Alerting**:
   - Configure Slack/email notifications
   - Set up on-call rotation
   - Create runbooks for common alerts

4. **Custom Dashboards**:
   - Business metrics (orders, revenue)
   - SLA compliance dashboards
   - Infrastructure health dashboards

5. **Integration Testing**:
   - Test trace propagation across services
   - Verify metric accuracy
   - Validate alert triggers
