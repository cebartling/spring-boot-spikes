# Metrics Collection: Prometheus and Grafana

## Status: IN PROGRESS

**Created:** December 2025

## Overview

This document describes the implementation of metrics collection using Prometheus and visualization with Grafana for the saga pattern spike application. This complements the existing distributed tracing with Jaeger by providing comprehensive metrics observability.

## Goals

- **Metrics Collection** - Collect JVM, HTTP, database, and custom saga metrics via Prometheus
- **Visualization** - Provide pre-configured Grafana dashboards for local development
- **Zero Configuration** - Auto-provision datasources and dashboards on startup
- **Production Ready** - Configuration patterns that translate to production environments

## Architecture

```mermaid
flowchart TB
    subgraph Docker Compose
        PROM[Prometheus<br/>:9090]
        GRAF[Grafana<br/>:3000]
        JAEGER[Jaeger<br/>:16686]
        PG[(PostgreSQL)]
        WM[WireMock]
        V[Vault]
    end

    subgraph Application
        APP[Spring Boot App<br/>:8080]
        ACT[/actuator/prometheus]
    end

    APP --> ACT
    PROM -->|scrape /actuator/prometheus| ACT
    GRAF -->|query| PROM
    GRAF -.->|link to traces| JAEGER

    APP --> PG
    APP --> WM
    APP -->|secrets| V
```

## Metrics Categories

### JVM Metrics (Auto-configured)
| Metric | Description |
|--------|-------------|
| `jvm_memory_used_bytes` | Memory usage by area (heap, non-heap) |
| `jvm_memory_max_bytes` | Maximum memory available |
| `jvm_gc_pause_seconds` | GC pause duration |
| `jvm_threads_states_threads` | Thread count by state |
| `jvm_classes_loaded_classes` | Loaded class count |

### HTTP Server Metrics (Auto-configured)
| Metric | Description |
|--------|-------------|
| `http_server_requests_seconds` | Request duration histogram |
| `http_server_requests_active_seconds` | Active request duration |

### HTTP Client Metrics (Auto-configured)
| Metric | Description |
|--------|-------------|
| `http_client_requests_seconds` | WebClient request duration |

### R2DBC Connection Pool Metrics
| Metric | Description |
|--------|-------------|
| `r2dbc_pool_acquired` | Acquired connections |
| `r2dbc_pool_allocated` | Allocated connections |
| `r2dbc_pool_idle` | Idle connections |
| `r2dbc_pool_pending` | Pending acquire requests |

### Custom Saga Metrics
| Metric | Description |
|--------|-------------|
| `saga_started_total` | Counter of sagas initiated |
| `saga_completed_total` | Counter of successful sagas |
| `saga_compensated_total` | Counter of compensated sagas |
| `saga_duration_seconds` | Histogram of saga execution time |
| `saga_step_duration_seconds` | Histogram of individual step times |
| `saga_step_failed_total` | Counter of step failures by step name |

## Implementation Phases

### Phase 1: Dependencies

Add the Prometheus metrics registry to `build.gradle.kts`:

```kotlin
dependencies {
    // Existing actuator dependency
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Add Prometheus registry for metrics export
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

### Phase 2: Application Configuration

Update `application.yaml` to ensure Prometheus endpoint is properly configured:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
        http.client.requests: true
```

### Phase 3: Prometheus Infrastructure

Add Prometheus service to `docker-compose.yml`:

```yaml
prometheus:
  image: prom/prometheus:v2.48.0
  container_name: saga-prometheus
  ports:
    - "9090:9090"
  volumes:
    - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    - prometheus_data:/prometheus
  command:
    - '--config.file=/etc/prometheus/prometheus.yml'
    - '--storage.tsdb.path=/prometheus'
    - '--storage.tsdb.retention.time=7d'
    - '--web.enable-lifecycle'
  healthcheck:
    test: ["CMD", "wget", "--spider", "-q", "http://localhost:9090/-/healthy"]
    interval: 10s
    timeout: 5s
    retries: 5
```

Create `docker/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'sagapattern'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
    scrape_interval: 5s
```

### Phase 4: Grafana Infrastructure

Add Grafana service to `docker-compose.yml`:

```yaml
grafana:
  image: grafana/grafana:10.2.0
  container_name: saga-grafana
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_USER=admin
    - GF_SECURITY_ADMIN_PASSWORD=admin
    - GF_USERS_ALLOW_SIGN_UP=false
  volumes:
    - ./docker/grafana/provisioning:/etc/grafana/provisioning
    - grafana_data:/var/lib/grafana
  depends_on:
    prometheus:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:3000/api/health"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### Phase 5: Grafana Provisioning

Create datasource provisioning `docker/grafana/provisioning/datasources/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false

  - name: Jaeger
    type: jaeger
    access: proxy
    url: http://jaeger:16686
    editable: false
```

Create dashboard provisioning `docker/grafana/provisioning/dashboards/dashboards.yml`:

```yaml
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: 'Saga Pattern'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /etc/grafana/provisioning/dashboards/json
```

### Phase 6: Pre-configured Dashboards

Create the following dashboards in `docker/grafana/provisioning/dashboards/json/`:

1. **JVM Dashboard** (`jvm-dashboard.json`)
   - Memory usage (heap/non-heap)
   - GC metrics
   - Thread states
   - Class loading

2. **Spring Boot Dashboard** (`spring-boot-dashboard.json`)
   - HTTP request rate and latency
   - HTTP client metrics
   - Error rates
   - Actuator health

3. **Saga Metrics Dashboard** (`saga-dashboard.json`)
   - Saga execution rate
   - Success vs compensation ratio
   - Step duration breakdown
   - Failure analysis

## Infrastructure Ports

| Service | Port | Purpose |
|---------|------|---------|
| Prometheus | 9090 | Metrics database and query UI |
| Grafana | 3000 | Visualization dashboards |
| Spring Boot | 8080 | Application (metrics at /actuator/prometheus) |

## Usage

### Start Infrastructure

```bash
docker compose up -d
```

### Access Dashboards

- **Grafana**: http://localhost:3000 (no login required)
- **Prometheus**: http://localhost:9090

### Verify Metrics Collection

```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'

# Check Spring Boot metrics endpoint
curl http://localhost:8080/actuator/prometheus | head -50

# Query specific metric
curl 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes' | jq
```

### Example PromQL Queries

```promql
# JVM heap memory usage percentage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# HTTP request rate (requests per second)
rate(http_server_requests_seconds_count[5m])

# HTTP request 95th percentile latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Saga success rate
saga_completed_total / saga_started_total * 100

# Saga compensation rate
rate(saga_compensated_total[5m])
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GF_SECURITY_ADMIN_PASSWORD` | Grafana admin password | `admin` |
| `PROMETHEUS_RETENTION` | Metrics retention period | `7d` |

## Testing Checklist

- [ ] Prometheus scrapes Spring Boot metrics successfully
- [ ] All target endpoints show "UP" in Prometheus
- [ ] Grafana connects to Prometheus datasource
- [ ] JVM dashboard shows memory and GC metrics
- [ ] Spring Boot dashboard shows HTTP metrics
- [ ] Saga dashboard shows custom metrics
- [ ] Jaeger datasource links traces from Grafana

## References

- [Micrometer Prometheus Registry](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Prometheus Configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
