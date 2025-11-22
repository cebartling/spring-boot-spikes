# Implementation Plan: AC9 - Observability Platform

**Feature:** [Local Development Services Infrastructure](../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC9 - Observability Platform

## Overview

Implement a comprehensive observability platform for local development that provides distributed tracing, log aggregation, and metrics collection. This platform will enable developers to monitor, debug, and understand the behavior of the CQRS application and its infrastructure in real-time.

## Prerequisites

- AC5 (Infrastructure Orchestration) completed
- Docker Compose configured
- Spring Boot application running

## Technology Selection

### Distributed Tracing: Tempo + Grafana

**Rationale:**
- Tempo: Lightweight, cost-effective distributed tracing backend
- Native integration with Grafana for visualization
- Supports OpenTelemetry standard
- Low resource footprint suitable for local development
- No indexing overhead (TraceID-based queries)

**Alternatives Considered:**
- Jaeger: More resource-intensive, complex setup
- Zipkin: Older, less feature-rich than Tempo
- AWS X-Ray Local: Limited functionality, vendor lock-in

### Log Aggregation: Loki + Promtail + Grafana

**Rationale:**
- Loki: Designed for cost-effective log aggregation
- Promtail: Lightweight log collector
- Native Grafana integration (unified observability platform)
- Label-based indexing (similar to Prometheus)
- Minimal resource requirements
- No need for full-text indexing

**Alternatives Considered:**
- ELK Stack (Elasticsearch, Logstash, Kibana): Too heavy for local dev
- Fluentd + Elasticsearch: Resource-intensive
- Splunk: Commercial, overkill for local development

### Metrics Collection: Prometheus + Grafana

**Rationale:**
- Prometheus: Industry-standard metrics collection
- Excellent Spring Boot integration via Micrometer
- Pull-based model (no agent required)
- Powerful query language (PromQL)
- Native Grafana integration
- Active community and ecosystem

**Alternatives Considered:**
- InfluxDB: Different data model, less Spring Boot integration
- Datadog Agent: Commercial, heavy for local dev
- CloudWatch Local: Limited functionality

### Unified Platform: Grafana

**Rationale:**
- Single UI for traces, logs, and metrics
- Correlate data across all three pillars of observability
- Rich dashboarding capabilities
- Open source and widely adopted

## Technical Implementation

### 1. Docker Compose Configuration

```yaml
# docker-compose.yml (add to existing configuration)
services:
  # ... existing services (vault, postgres, app) ...

  # Distributed Tracing
  tempo:
    image: grafana/tempo:latest
    container_name: cqrs-tempo
    command: ["-config.file=/etc/tempo.yaml"]
    volumes:
      - ./infrastructure/observability/tempo/tempo.yaml:/etc/tempo.yaml:ro
      - tempo-data:/var/tempo
    ports:
      - "3200:3200"   # Tempo query frontend
      - "4317:4317"   # OTLP gRPC receiver
      - "4318:4318"   # OTLP HTTP receiver
    networks:
      - cqrs-network
    restart: unless-stopped
    labels:
      com.example.service: "tempo"
      com.example.description: "Distributed tracing backend"

  # Log Aggregation
  loki:
    image: grafana/loki:latest
    container_name: cqrs-loki
    command: -config.file=/etc/loki/local-config.yaml
    volumes:
      - ./infrastructure/observability/loki/loki.yaml:/etc/loki/local-config.yaml:ro
      - loki-data:/loki
    ports:
      - "3100:3100"
    networks:
      - cqrs-network
    restart: unless-stopped
    labels:
      com.example.service: "loki"
      com.example.description: "Log aggregation system"

  promtail:
    image: grafana/promtail:latest
    container_name: cqrs-promtail
    command: -config.file=/etc/promtail/config.yaml
    volumes:
      - ./infrastructure/observability/promtail/promtail.yaml:/etc/promtail/config.yaml:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - /var/run/docker.sock:/var/run/docker.sock
    networks:
      - cqrs-network
    depends_on:
      - loki
    restart: unless-stopped
    labels:
      com.example.service: "promtail"
      com.example.description: "Log collector"

  # Metrics Collection
  prometheus:
    image: prom/prometheus:latest
    container_name: cqrs-prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=7d'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
    volumes:
      - ./infrastructure/observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    ports:
      - "9090:9090"
    networks:
      - cqrs-network
    restart: unless-stopped
    labels:
      com.example.service: "prometheus"
      com.example.description: "Metrics collection system"

  # Unified Observability UI
  grafana:
    image: grafana/grafana:latest
    container_name: cqrs-grafana
    environment:
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
      - GF_AUTH_DISABLE_LOGIN_FORM=true
      - GF_FEATURE_TOGGLES_ENABLE=traceqlEditor
    volumes:
      - ./infrastructure/observability/grafana/datasources:/etc/grafana/provisioning/datasources:ro
      - ./infrastructure/observability/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - grafana-data:/var/lib/grafana
    ports:
      - "3000:3000"
    networks:
      - cqrs-network
    depends_on:
      - prometheus
      - loki
      - tempo
    restart: unless-stopped
    labels:
      com.example.service: "grafana"
      com.example.description: "Observability visualization platform"

volumes:
  tempo-data:
    name: cqrs-tempo-data
  loki-data:
    name: cqrs-loki-data
  prometheus-data:
    name: cqrs-prometheus-data
  grafana-data:
    name: cqrs-grafana-data
```

### 2. Tempo Configuration

```yaml
# infrastructure/observability/tempo/tempo.yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        http:
        grpc:

ingester:
  trace_idle_period: 10s
  max_block_bytes: 1_000_000
  max_block_duration: 5m

compactor:
  compaction:
    compaction_window: 1h
    max_compaction_objects: 1000000
    block_retention: 24h
    compacted_block_retention: 10m

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
    pool:
      max_workers: 100
      queue_depth: 10000
```

### 3. Loki Configuration

```yaml
# infrastructure/observability/loki/loki.yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

limits_config:
  retention_period: 168h  # 7 days
  ingestion_rate_mb: 16
  ingestion_burst_size_mb: 32

chunk_store_config:
  max_look_back_period: 168h

table_manager:
  retention_deletes_enabled: true
  retention_period: 168h
```

### 4. Promtail Configuration

```yaml
# infrastructure/observability/promtail/promtail.yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  # Docker container logs
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
    relabel_configs:
      - source_labels: ['__meta_docker_container_name']
        regex: '/(.*)'
        target_label: 'container'
      - source_labels: ['__meta_docker_container_log_stream']
        target_label: 'stream'
      - source_labels: ['__meta_docker_container_label_com_example_service']
        target_label: 'service'
    pipeline_stages:
      - docker: {}
      - json:
          expressions:
            level: level
            logger: logger_name
            thread: thread_name
            trace_id: trace_id
            span_id: span_id
      - labels:
          level:
          service:
      - timestamp:
          source: timestamp
          format: RFC3339Nano
```

### 5. Prometheus Configuration

```yaml
# infrastructure/observability/prometheus/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    environment: 'local'
    cluster: 'cqrs-spike'

scrape_configs:
  # Spring Boot Application
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
        labels:
          application: 'cqrs-spike'
          service: 'app'

  # Prometheus self-monitoring
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # PostgreSQL Exporter (optional)
  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']
    # Uncomment if postgres-exporter is added

  # Vault metrics (if enabled)
  - job_name: 'vault'
    metrics_path: '/v1/sys/metrics'
    params:
      format: ['prometheus']
    bearer_token: 'dev-root-token'
    static_configs:
      - targets: ['vault:8200']
```

### 6. Grafana Datasource Provisioning

```yaml
# infrastructure/observability/grafana/datasources/datasources.yaml
apiVersion: 1

datasources:
  # Prometheus
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: '15s'

  # Loki
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true
    jsonData:
      derivedFields:
        - datasourceUid: tempo
          matcherRegex: 'trace_id=(\w+)'
          name: TraceID
          url: '$${__value.raw}'

  # Tempo
  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    editable: true
    jsonData:
      tracesToLogs:
        datasourceUid: loki
        tags: ['service']
        mappedTags: [{ key: 'service.name', value: 'service' }]
        mapTagNamesEnabled: true
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
      tracesToMetrics:
        datasourceUid: prometheus
        tags: [{ key: 'service.name', value: 'service' }]
        queries:
          - name: 'Request Rate'
            query: 'rate(http_server_requests_seconds_count{$$__tags}[5m])'
      serviceMap:
        datasourceUid: prometheus
      search:
        hide: false
      nodeGraph:
        enabled: true
```

### 7. Spring Boot Integration

**Maven Dependencies:**
```xml
<!-- pom.xml -->
<dependencies>
    <!-- Micrometer for metrics -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- OpenTelemetry for tracing -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <!-- Logback encoder for structured logging -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
        <version>7.4</version>
    </dependency>

    <!-- Spring Boot Actuator for metrics exposure -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

**Application Configuration:**
```yaml
# application.yml
spring:
  application:
    name: cqrs-spike

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
    tags:
      application: ${spring.application.name}
      environment: local
  tracing:
    sampling:
      probability: 1.0  # 100% sampling for local development
  otlp:
    tracing:
      endpoint: http://tempo:4318/v1/traces

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [trace_id=%X{traceId:-},span_id=%X{spanId:-}] - %msg%n"
  level:
    root: INFO
    com.example.cqrs: DEBUG
```

**Logback Configuration with Structured Logging:**
```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console appender with trace context -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <providers>
                <timestamp/>
                <version/>
                <message/>
                <loggerName/>
                <threadName/>
                <logLevel/>
                <logLevelValue/>
                <callerData/>
                <stackTrace/>
                <context/>
                <mdc/>
                <tags/>
                <logstashMarkers/>
            </providers>
            <customFields>{"service":"cqrs-spike","environment":"local"}</customFields>
        </encoder>
    </appender>

    <!-- File appender for local debugging -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"cqrs-spike","environment":"local"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

    <logger name="com.example.cqrs" level="DEBUG"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>
</configuration>
```

### 8. Custom Metrics Example

```java
package com.example.cqrs.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    // Counter example
    public void recordOrderCreated(String orderType) {
        Counter.builder("cqrs.orders.created")
            .tag("type", orderType)
            .description("Number of orders created")
            .register(meterRegistry)
            .increment();
    }

    // Timer example
    public void recordEventProcessingTime(String eventType, Runnable action) {
        Timer.builder("cqrs.event.processing.time")
            .tag("event_type", eventType)
            .description("Time taken to process events")
            .register(meterRegistry)
            .record(action);
    }

    // Gauge example
    public void registerActiveAggregates(java.util.concurrent.atomic.AtomicInteger count) {
        meterRegistry.gauge("cqrs.aggregates.active", count);
    }
}
```

### 9. Grafana Dashboard Provisioning

```yaml
# infrastructure/observability/grafana/dashboards/dashboard-provider.yaml
apiVersion: 1

providers:
  - name: 'Default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards/json
```

**Spring Boot Dashboard JSON:**
```json
{
  "dashboard": {
    "title": "CQRS Spike - Application Metrics",
    "panels": [
      {
        "title": "Request Rate",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count{application=\"cqrs-spike\"}[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Response Time (p95)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{application=\"cqrs-spike\"}[5m]))",
            "legendFormat": "{{method}} {{uri}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "JVM Memory",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{application=\"cqrs-spike\"}",
            "legendFormat": "{{area}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Database Connections",
        "targets": [
          {
            "expr": "hikaricp_connections_active{application=\"cqrs-spike\"}",
            "legendFormat": "Active"
          },
          {
            "expr": "hikaricp_connections_idle{application=\"cqrs-spike\"}",
            "legendFormat": "Idle"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

### 10. Trace Context Propagation

```java
package com.example.cqrs.infrastructure.observability;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TracingAspect {

    private final Tracer tracer;

    @Around("@annotation(com.example.cqrs.infrastructure.observability.Traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        var span = tracer.nextSpan().name(joinPoint.getSignature().getName());

        try (var ws = tracer.withSpan(span.start())) {
            span.tag("class", joinPoint.getTarget().getClass().getSimpleName());
            span.tag("method", joinPoint.getSignature().getName());

            log.debug("Executing traced method: {}", joinPoint.getSignature());

            return joinPoint.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }
}

// Custom annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
}
```

### 11. Helper Scripts

**Start Observability Stack:**
```bash
#!/bin/bash
# scripts/start-observability.sh

set -e

echo "========================================="
echo "Starting Observability Platform"
echo "========================================="

# Create directories
mkdir -p infrastructure/observability/{tempo,loki,promtail,prometheus,grafana/{datasources,dashboards/json}}

# Start observability services
docker-compose up -d tempo loki promtail prometheus grafana

# Wait for services to be ready
echo "Waiting for services to start..."
sleep 15

# Check service health
echo ""
echo "Service Status:"
echo "  Tempo:      http://localhost:3200/status"
echo "  Loki:       http://localhost:3100/ready"
echo "  Prometheus: http://localhost:9090/-/healthy"
echo "  Grafana:    http://localhost:3000"
echo ""

# Verify services are responding
if curl -sf http://localhost:3200/status > /dev/null; then
    echo "✓ Tempo is ready"
else
    echo "✗ Tempo failed to start"
fi

if curl -sf http://localhost:3100/ready > /dev/null; then
    echo "✓ Loki is ready"
else
    echo "✗ Loki failed to start"
fi

if curl -sf http://localhost:9090/-/healthy > /dev/null; then
    echo "✓ Prometheus is ready"
else
    echo "✗ Prometheus failed to start"
fi

if curl -sf http://localhost:3000/api/health > /dev/null; then
    echo "✓ Grafana is ready"
else
    echo "✗ Grafana failed to start"
fi

echo ""
echo "========================================="
echo "Observability Platform Ready!"
echo "========================================="
echo "Grafana:    http://localhost:3000"
echo "Prometheus: http://localhost:9090"
echo "========================================="
```

**Query Logs:**
```bash
#!/bin/bash
# scripts/query-logs.sh

QUERY="${1:-{service=\"app\"}}"
LIMIT="${2:-100}"

echo "Querying Loki for: $QUERY (limit: $LIMIT)"

curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode "query=$QUERY" \
  --data-urlencode "limit=$LIMIT" \
  | jq -r '.data.result[].values[][1]'
```

**View Traces:**
```bash
#!/bin/bash
# scripts/view-traces.sh

TRACE_ID="$1"

if [ -z "$TRACE_ID" ]; then
    echo "Usage: ./scripts/view-traces.sh <trace-id>"
    exit 1
fi

echo "Fetching trace: $TRACE_ID"
echo "View in Grafana: http://localhost:3000/explore?left=%7B%22queries%22:%5B%7B%22datasource%22:%22Tempo%22,%22query%22:%22$TRACE_ID%22%7D%5D%7D"
```

## Testing Strategy

### 1. Distributed Tracing Tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
public class TracingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Tracer tracer;

    @Test
    void shouldCreateTraceForHttpRequest() {
        // Make request
        var response = restTemplate.getForEntity("/api/test", String.class);

        // Verify trace context exists
        var currentSpan = tracer.currentSpan();
        assertNotNull(currentSpan);
        assertNotNull(currentSpan.context().traceId());
    }
}
```

### 2. Metrics Tests

```java
@SpringBootTest
public class MetricsTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldExposePrometheusMetrics() {
        // Verify key metrics exist
        assertNotNull(meterRegistry.find("jvm.memory.used").gauge());
        assertNotNull(meterRegistry.find("http.server.requests").timer());
    }
}
```

### 3. Log Aggregation Tests

```bash
#!/bin/bash
# scripts/test-log-aggregation.sh

echo "Testing log aggregation..."

# Generate test log
docker exec cqrs-app sh -c 'echo "TEST_LOG_MESSAGE_12345" | logger'

# Wait for Promtail to scrape
sleep 5

# Query Loki
RESULT=$(curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={container="cqrs-app"}' \
  | jq -r '.data.result[].values[][1]' \
  | grep "TEST_LOG_MESSAGE_12345")

if [ -n "$RESULT" ]; then
    echo "✓ Log aggregation working"
else
    echo "✗ Log aggregation failed"
    exit 1
fi
```

## Rollout Steps

1. **Create observability directory structure**
   ```bash
   mkdir -p infrastructure/observability/{tempo,loki,promtail,prometheus,grafana/{datasources,dashboards/json}}
   ```

2. **Create configuration files**
   - tempo.yaml
   - loki.yaml
   - promtail.yaml
   - prometheus.yml
   - grafana datasources

3. **Update docker-compose.yml**
   - Add observability services
   - Configure volumes
   - Set up networking

4. **Add Spring Boot dependencies**
   - Micrometer + Prometheus
   - OpenTelemetry
   - Logstash encoder

5. **Configure Spring Boot**
   - application.yml (tracing, metrics)
   - logback-spring.xml (structured logging)

6. **Create Grafana dashboards**
   - Application metrics dashboard
   - Infrastructure dashboard

7. **Create helper scripts**
   - start-observability.sh
   - query-logs.sh
   - view-traces.sh

8. **Update Makefile**
   - Add observability targets

9. **Test each component**
   - Verify traces appear in Tempo/Grafana
   - Verify logs in Loki
   - Verify metrics in Prometheus

10. **Update documentation**
    - Add observability guide
    - Update README with URLs
    - Document query examples

## Verification Checklist

- [ ] Tempo running and accessible
- [ ] Loki running and collecting logs
- [ ] Promtail scraping container logs
- [ ] Prometheus scraping application metrics
- [ ] Grafana accessible and datasources configured
- [ ] Application exports traces to Tempo
- [ ] Application logs appear in Loki
- [ ] Application metrics available in Prometheus
- [ ] Traces include trace_id and span_id
- [ ] Logs include trace context
- [ ] Grafana can correlate traces with logs
- [ ] Grafana can correlate traces with metrics
- [ ] Custom business metrics working
- [ ] Dashboards provisioned automatically
- [ ] Helper scripts functional

## Troubleshooting Guide

### Issue: Traces not appearing in Tempo
**Solution:**
- Verify OTLP endpoint: `application.management.otlp.tracing.endpoint`
- Check Tempo logs: `docker logs cqrs-tempo`
- Verify network connectivity: `docker exec cqrs-app curl tempo:4318`

### Issue: Logs not appearing in Loki
**Solution:**
- Check Promtail logs: `docker logs cqrs-promtail`
- Verify Promtail can access Docker socket
- Check Loki logs: `docker logs cqrs-loki`
- Verify log format matches pipeline

### Issue: Metrics not scraped by Prometheus
**Solution:**
- Verify actuator endpoint: `curl localhost:8080/actuator/prometheus`
- Check Prometheus targets: http://localhost:9090/targets
- Verify Prometheus configuration
- Check network connectivity

### Issue: Grafana datasources not working
**Solution:**
- Check datasource URLs in provisioning config
- Verify services are accessible from Grafana container
- Check Grafana logs: `docker logs cqrs-grafana`
- Manually test datasources in Grafana UI

### Issue: High resource usage
**Solution:**
- Reduce Prometheus scrape interval
- Decrease trace sampling rate
- Reduce log retention period
- Apply Docker resource limits

## Performance Optimization

**Resource Limits:**
```yaml
# docker-compose.override.yml
services:
  tempo:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M

  loki:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M

  prometheus:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G

  grafana:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
```

## Makefile Integration

```makefile
# Add to existing Makefile

.PHONY: obs-start obs-stop obs-logs obs-grafana

obs-start: ## Start observability platform
	@./scripts/start-observability.sh

obs-stop: ## Stop observability platform
	@docker-compose stop tempo loki promtail prometheus grafana

obs-logs: ## View observability platform logs
	@docker-compose logs -f tempo loki promtail prometheus grafana

obs-grafana: ## Open Grafana in browser
	@open http://localhost:3000

obs-query-logs: ## Query logs (usage: make obs-query-logs QUERY='{service="app"}')
	@./scripts/query-logs.sh '$(QUERY)'
```

## Dependencies

- **Blocks:** None
- **Blocked By:** AC5 (Infrastructure Orchestration)
- **Related:** AC6 (Development Experience), AC8 (Documentation)
