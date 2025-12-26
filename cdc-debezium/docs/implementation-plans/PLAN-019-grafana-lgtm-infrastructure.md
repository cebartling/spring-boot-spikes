# PLAN-019: Grafana LGTM Infrastructure

## Objective

Deploy the complete Grafana LGTM stack (Grafana + Loki + Tempo + Prometheus) in Docker Compose with OpenTelemetry Collector as the unified ingestion layer.

## Parent Feature

[FEATURE-002](../features/FEATURE-002.md) - Section 2.5.1: Grafana LGTM Infrastructure Setup

## Dependencies

- PLAN-009: Observability Infrastructure (extends existing OTel/Prometheus setup)

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Add Grafana, Tempo, Loki services |
| `docker/grafana/provisioning/datasources/datasources.yml` | Auto-configure data sources |
| `docker/grafana/provisioning/dashboards/dashboards.yml` | Dashboard provisioning |
| `docker/tempo/tempo.yaml` | Tempo configuration |
| `docker/loki/loki.yaml` | Loki configuration |
| `docker/otel/otel-collector-config.yaml` | Update for LGTM export |

### LGTM Architecture

```mermaid
flowchart TB
    subgraph APPS["Applications"]
        SPRING[Spring Boot<br/>OTel Agent]
        CONNECT[Kafka Connect<br/>JMX]
    end

    subgraph COLLECT["OpenTelemetry Collector"]
        RECV[Receivers]
        PROC[Processors]
        EXP[Exporters]
    end

    subgraph LGTM["Grafana LGTM Stack"]
        TEMPO[(Tempo<br/>Traces)]
        LOKI[(Loki<br/>Logs)]
        PROM[(Prometheus<br/>Metrics)]
        GRAFANA[Grafana UI]
    end

    SPRING -->|OTLP| RECV
    CONNECT -->|JMX| RECV
    RECV --> PROC
    PROC --> EXP

    EXP -->|OTLP| TEMPO
    EXP -->|Loki| LOKI
    EXP -->|Remote Write| PROM

    TEMPO --> GRAFANA
    LOKI --> GRAFANA
    PROM --> GRAFANA
```

### docker-compose.yml Additions

```yaml
services:
  # ... existing services ...

  grafana:
    image: grafana/grafana:11.0.0
    container_name: cdc-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer
      GF_FEATURE_TOGGLES_ENABLE: traceqlEditor tempoSearch tempoBackendSearch
    volumes:
      - ./docker/grafana/provisioning:/etc/grafana/provisioning:ro
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus
      - tempo
      - loki
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3000/api/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  tempo:
    image: grafana/tempo:2.4.1
    container_name: cdc-tempo
    command: ["-config.file=/etc/tempo/tempo.yaml"]
    ports:
      - "3200:3200"   # Tempo HTTP
      - "4317"        # OTLP gRPC (internal)
      - "4318"        # OTLP HTTP (internal)
    volumes:
      - ./docker/tempo/tempo.yaml:/etc/tempo/tempo.yaml:ro
      - tempo_data:/var/tempo
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3200/ready"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  loki:
    image: grafana/loki:3.0.0  # Upgraded from 2.9.6 for native OTLP ingestion
    container_name: cdc-loki
    user: "0"  # Run as root for development (volume permissions)
    command: ["-config.file=/etc/loki/loki.yaml"]
    ports:
      - "3100:3100"
    volumes:
      - ./docker/loki/loki.yaml:/etc/loki/loki.yaml:ro
      - loki_data:/tmp/loki  # Loki 3.0 uses /tmp/loki by default
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3100/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

volumes:
  grafana_data:
  tempo_data:
  loki_data:
```

### Tempo Configuration (tempo.yaml)

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

ingester:
  max_block_duration: 5m

compactor:
  compaction:
    block_retention: 48h

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal

querier:
  frontend_worker:
    frontend_address: localhost:9095

metrics_generator:
  registry:
    external_labels:
      source: tempo
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://prometheus:9090/api/v1/write
        send_exemplars: true

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]
```

### Loki Configuration (loki.yaml)

> **Note**: Loki 3.0+ includes native OTLP ingestion support at `/otlp/v1/logs`.

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  instance_addr: 127.0.0.1
  path_prefix: /tmp/loki
  storage:
    filesystem:
      chunks_directory: /tmp/loki/chunks
      rules_directory: /tmp/loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

query_range:
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100

schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093

limits_config:
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  ingestion_rate_mb: 16
  ingestion_burst_size_mb: 24
  allow_structured_metadata: true  # Required for OTLP ingestion

analytics:
  reporting_enabled: false
```

### Grafana Data Sources (datasources.yml)

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    jsonData:
      httpMethod: POST
      manageAlerts: true
      prometheusType: Prometheus

  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo:3200
    jsonData:
      httpMethod: GET
      tracesToLogsV2:
        datasourceUid: loki
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        tags: [{ key: 'service.name', value: 'service_name' }]
        filterByTraceID: true
        filterBySpanID: true
      tracesToMetrics:
        datasourceUid: prometheus
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        tags: [{ key: 'service.name', value: 'service_name' }]
      serviceMap:
        datasourceUid: prometheus
      nodeGraph:
        enabled: true
      lokiSearch:
        datasourceUid: loki

  - name: Loki
    type: loki
    uid: loki
    access: proxy
    url: http://loki:3100
    jsonData:
      derivedFields:
        - name: TraceID
          matcherRegex: '"trace_id":"([a-f0-9]+)"'
          url: '$${__value.raw}'
          datasourceUid: tempo
          urlDisplayLabel: 'View Trace'
```

### Dashboard Provisioning (dashboards.yml)

```yaml
apiVersion: 1

providers:
  - name: 'CDC Dashboards'
    orgId: 1
    folder: 'CDC'
    folderUid: 'cdc-dashboards'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /etc/grafana/provisioning/dashboards/json
```

### Updated OTel Collector Config

> **Note**: The `loki` exporter is deprecated. Use `otlphttp` exporter with Loki 3.0's native OTLP endpoint.

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

  prometheus:
    config:
      scrape_configs:
        - job_name: 'otel-collector'
          static_configs:
            - targets: ['localhost:8888']

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
      - key: service.instance.id
        from_attribute: host.name
        action: upsert

  attributes:
    actions:
      - key: environment
        value: local
        action: upsert

exporters:
  # Traces to Jaeger (legacy, kept for compatibility)
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

  # Traces to Tempo (LGTM stack)
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

  # Logs to Loki via OTLP (recommended approach, loki exporter is deprecated)
  otlphttp/loki:
    endpoint: http://loki:3100/otlp
    tls:
      insecure: true

  # Metrics to Prometheus via remote write (LGTM stack)
  prometheusremotewrite:
    endpoint: http://prometheus:9090/api/v1/write
    tls:
      insecure: true

  # Metrics to Prometheus (pull-based, legacy)
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
      processors: [memory_limiter, batch, resource, attributes]
      exporters: [otlp/jaeger, otlp/tempo]

    metrics:
      receivers: [otlp, prometheus]
      processors: [memory_limiter, batch, resource]
      exporters: [prometheus, prometheusremotewrite]

    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource, attributes]
      exporters: [otlphttp/loki, debug]

  telemetry:
    logs:
      level: info
    metrics:
      address: 0.0.0.0:8888
```

## Directory Structure

```
docker/
├── grafana/
│   └── provisioning/
│       ├── datasources/
│       │   └── datasources.yml
│       └── dashboards/
│           ├── dashboards.yml
│           └── json/
│               └── (dashboard JSON files - PLAN-020)
├── tempo/
│   └── tempo.yaml
├── loki/
│   └── loki.yaml
└── otel/
    └── otel-collector-config.yaml
```

## Commands to Run

```bash
# Create directory structure
mkdir -p docker/grafana/provisioning/datasources
mkdir -p docker/grafana/provisioning/dashboards/json
mkdir -p docker/tempo
mkdir -p docker/loki

# Start LGTM stack
docker compose up -d prometheus tempo loki grafana otel-collector

# Wait for services to be healthy
docker compose ps

# Verify Grafana is accessible
curl http://localhost:3000/api/health

# Verify Tempo is ready
curl http://localhost:3200/ready

# Verify Loki is ready
curl http://localhost:3100/ready

# Open Grafana in browser
open http://localhost:3000
# Login: admin/admin

# Verify data sources in Grafana
curl -u admin:admin http://localhost:3000/api/datasources

# Test trace ingestion
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  -d '{
    "resourceSpans": [{
      "resource": {"attributes": [{"key": "service.name", "value": {"stringValue": "test-service"}}]},
      "scopeSpans": [{
        "spans": [{
          "traceId": "5B8EFFF798038103D269B633813FC60C",
          "spanId": "EEE19B7EC3C1B174",
          "name": "test-span",
          "startTimeUnixNano": "'$(date +%s)000000000'",
          "endTimeUnixNano": "'$(date +%s)100000000'"
        }]
      }]
    }]
  }'

# Check trace in Tempo via Grafana Explore

# Test log ingestion
curl -X POST http://localhost:3100/loki/api/v1/push \
  -H "Content-Type: application/json" \
  -d '{
    "streams": [{
      "stream": {"service_name": "test-service", "level": "info"},
      "values": [["'$(date +%s)000000000'", "Test log message from curl"]]
    }]
  }'

# Check logs in Loki via Grafana Explore
```

## Acceptance Criteria

- [x] All LGTM services start successfully and health checks pass
- [x] Grafana is accessible with 3 pre-configured data sources (Prometheus, Tempo, Loki)
- [x] Prometheus data source is working (query "up" returns metrics)
- [x] Tempo data source is working (TraceQL available in Explore)
- [x] Loki data source is working (LogQL available in Explore)
- [x] Traces link to logs (trace-to-logs correlation configured)
- [x] OTel Collector exports traces to Tempo, metrics to Prometheus, logs to Loki

### Test Results Summary

| Test Class | Tests | Status | Coverage |
|------------|-------|--------|----------|
| LgtmInfrastructureAcceptanceTest | 18 | ✅ All passing | Grafana, Tempo, Loki, Prometheus, OTel Collector health/integration |
| TraceToLogsLinkingAcceptanceTest | 11 | ✅ All passing | Tempo-Loki linking, derived fields, cross-service correlation |
| GrafanaUiAcceptanceTest | 8 | ✅ All passing | Datasources page, Explore page, UI validation with Playwright |

**Total: 37 tests, 0 failures**

## Estimated Complexity

Medium - Multiple services to configure with proper networking and data source connections.

## Notes

- Tempo uses local filesystem storage - suitable for development only
- Loki uses embedded TSDB - suitable for development only
- For production, consider using object storage (S3, GCS) for Tempo and Loki
- Grafana feature toggles enable TraceQL editor and Tempo search
- Data source UIDs must match for cross-linking (traces to logs, traces to metrics)
- OTel Collector is the single ingestion point for all telemetry
- Consider memory limits for Tempo and Loki in production
