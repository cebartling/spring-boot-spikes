# PLAN-009: Observability Infrastructure (Collector, Jaeger, Prometheus)

## Objective

Set up the OpenTelemetry Collector, Jaeger (traces), and Prometheus (metrics) as Docker Compose services to receive and visualize observability data from the CDC consumer.

## Dependencies

- PLAN-001: Docker Compose base infrastructure

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Add observability services |
| `docker/otel/otel-collector-config.yaml` | OTel Collector pipeline configuration |
| `docker/prometheus/prometheus.yml` | Prometheus scrape configuration |

### Docker Compose Services

Add to `docker-compose.yml`:

```yaml
services:
  # ... existing services ...

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.96.0
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./docker/otel/otel-collector-config.yaml:/etc/otel-collector-config.yaml:ro
    ports:
      - "4317:4317"   # OTLP gRPC receiver
      - "4318:4318"   # OTLP HTTP receiver
      - "8888:8888"   # Collector metrics
      - "8889:8889"   # Prometheus exporter
    depends_on:
      - jaeger

  jaeger:
    image: jaegertracing/all-in-one:1.54
    ports:
      - "16686:16686" # Jaeger UI
      - "14250:14250" # gRPC (used by OTel Collector)
      - "14268:14268" # HTTP collector
    environment:
      COLLECTOR_OTLP_ENABLED: "true"

  prometheus:
    image: prom/prometheus:v2.49.1
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"   # Prometheus UI
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'
```

### OTel Collector Configuration (otel-collector-config.yaml)

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
    timeout: 1s
    send_batch_size: 1024

  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128

  resource:
    attributes:
      - key: environment
        value: development
        action: upsert

exporters:
  # Traces to Jaeger
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

  # Metrics to Prometheus (pull-based)
  prometheus:
    endpoint: 0.0.0.0:8889
    namespace: cdc
    const_labels:
      environment: development

  # Debug logging (for troubleshooting)
  debug:
    verbosity: detailed
    sampling_initial: 5
    sampling_thereafter: 200

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [otlp/jaeger, debug]

    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [prometheus, debug]

    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [debug]

  telemetry:
    logs:
      level: info
    metrics:
      address: 0.0.0.0:8888
```

### Prometheus Configuration (prometheus.yml)

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Scrape OTel Collector's Prometheus exporter
  - job_name: 'otel-collector'
    static_configs:
      - targets: ['otel-collector:8889']

  # Scrape OTel Collector's own metrics
  - job_name: 'otel-collector-internal'
    static_configs:
      - targets: ['otel-collector:8888']

  # Scrape Spring Boot application (if actuator is enabled)
  - job_name: 'cdc-consumer'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
    # Fallback if running on Linux (no host.docker.internal)
    # - targets: ['172.17.0.1:8080']
```

## Directory Structure

```
docker/
├── otel/
│   └── otel-collector-config.yaml
├── prometheus/
│   └── prometheus.yml
└── postgres/
    └── init/
        ├── 01-schema.sql
        └── 02-seed.sql
```

## Commands to Run

```bash
# Create directories
mkdir -p docker/otel docker/prometheus

# Start observability stack
docker compose up -d otel-collector jaeger prometheus

# Verify OTel Collector is running
curl -s http://localhost:8888/metrics | head -20

# Verify Jaeger is running
curl -s http://localhost:16686/api/services | jq .

# Verify Prometheus is running
curl -s http://localhost:9090/-/ready

# Check Prometheus targets
open http://localhost:9090/targets
# Should show otel-collector as UP

# Open Jaeger UI
open http://localhost:16686

# Open Prometheus UI
open http://localhost:9090

# Test OTLP endpoint (send a test span)
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  -d '{
    "resourceSpans": [{
      "resource": {"attributes": [{"key": "service.name", "value": {"stringValue": "test"}}]},
      "scopeSpans": [{
        "spans": [{
          "traceId": "5B8EFFF798038103D269B633813FC60C",
          "spanId": "EEE19B7EC3C1B174",
          "name": "test-span",
          "kind": 1,
          "startTimeUnixNano": "1544712660000000000",
          "endTimeUnixNano": "1544712661000000000"
        }]
      }]
    }]
  }'

# Verify test span appears in Jaeger (service: test)
```

## Acceptance Criteria

1. [ ] OpenTelemetry Collector starts and accepts connections on:
   - [ ] Port 4317 (gRPC OTLP)
   - [ ] Port 4318 (HTTP OTLP)
   - [ ] Port 8889 (Prometheus exporter)
2. [ ] Jaeger UI is accessible at http://localhost:16686
3. [ ] Prometheus UI is accessible at http://localhost:9090
4. [ ] Prometheus shows `otel-collector` target as UP
5. [ ] Test span sent to OTLP endpoint appears in Jaeger
6. [ ] OTel Collector exports metrics to Prometheus
7. [ ] Services survive `docker compose restart`
8. [ ] Debug logging shows received telemetry data

## Service Ports Reference

| Service | Port | Purpose |
|---------|------|---------|
| OTel Collector | 4317 | OTLP gRPC receiver (traces, metrics, logs) |
| OTel Collector | 4318 | OTLP HTTP receiver |
| OTel Collector | 8888 | Collector internal metrics |
| OTel Collector | 8889 | Prometheus exporter endpoint |
| Jaeger | 16686 | Jaeger UI |
| Jaeger | 14250 | Jaeger gRPC collector |
| Prometheus | 9090 | Prometheus UI and API |

## Data Flow

```
┌─────────────────┐
│  CDC Consumer   │
│  (Spring Boot)  │
└────────┬────────┘
         │ OTLP (gRPC :4317)
         ▼
┌─────────────────┐
│  OTel Collector │
│   (pipelines)   │
└───────┬─┬───────┘
        │ │
   ┌────┘ └────┐
   ▼           ▼
┌──────┐  ┌──────────┐
│Jaeger│  │Prometheus│
│:16686│  │  :9090   │
└──────┘  └──────────┘
```

## Estimated Complexity

Low-Medium - Docker Compose services are straightforward; OTel Collector configuration has many options but the basic pipeline is simple.

## Notes

- Using `otel/opentelemetry-collector-contrib` for additional exporters
- Jaeger `all-in-one` image includes storage (in-memory for development)
- Prometheus uses `host.docker.internal` to scrape local application (works on Mac/Windows)
- For Linux, use the Docker bridge IP (`172.17.0.1`) or run the app in Docker
- The `debug` exporter logs received telemetry for troubleshooting
- Memory limiter processor prevents OOM conditions
- Batch processor improves export efficiency
