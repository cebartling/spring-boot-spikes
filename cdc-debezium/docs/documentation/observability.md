# Observability

This project includes full OpenTelemetry observability with distributed tracing, metrics, and structured logging using the Grafana LGTM stack.

## Observability Stack

| Component | Port | Purpose |
|-----------|------|---------|
| OpenTelemetry Collector | 4317, 4318 | OTLP receiver (gRPC/HTTP) |
| Grafana | 3000 | Unified observability dashboard |
| Prometheus | 9090 | Metrics storage and querying |
| Tempo | 3200 | Distributed trace storage |
| Loki | 3100 | Log aggregation and querying |
| Jaeger | 16686 | Distributed tracing UI (legacy) |

## Data Flow

```
┌─────────────────┐
│  CDC Consumer   │
│  (Spring Boot)  │
└────────┬────────┘
         │ OTLP (HTTP :4318)
         ▼
┌─────────────────┐
│  OTel Collector │
│   (pipelines)   │
└───┬───┬───┬─────┘
    │   │   │
    ▼   ▼   ▼
┌──────┐ ┌─────┐ ┌──────────┐
│Tempo │ │Loki │ │Prometheus│
│:3200 │ │:3100│ │  :9090   │
└──┬───┘ └──┬──┘ └────┬─────┘
   │        │         │
   └────────┼─────────┘
            ▼
      ┌──────────┐
      │ Grafana  │
      │  :3000   │
      └──────────┘
```

## Accessing Observability UIs

```bash
# Open Grafana (unified dashboard - recommended)
open http://localhost:3000
# Login: admin/admin

# Open Jaeger UI (traces - legacy)
open http://localhost:16686

# Open Prometheus UI (metrics)
open http://localhost:9090

# Check OTel Collector health
curl -s http://localhost:8888/metrics | head -20

# Check Loki readiness
curl -s http://localhost:3100/ready

# Check Tempo readiness
curl -s http://localhost:3200/ready
```

## Tracing

The CDC consumer emits spans for each message processed with the following semantic attributes:

| Attribute | Description | Example |
|-----------|-------------|---------|
| `messaging.system` | Messaging system | `kafka` |
| `messaging.destination.name` | Topic name | `cdc.public.customer` |
| `messaging.kafka.consumer.group` | Consumer group | `cdc-consumer-group` |
| `messaging.kafka.partition` | Partition number | `0` |
| `messaging.kafka.message.offset` | Message offset | `42` |
| `messaging.operation` | Operation type | `process` |
| `db.operation` | Database operation | `upsert`, `delete`, `ignore` |
| `customer.id` | Customer UUID | `550e8400-...` |

### Finding Traces in Jaeger

1. Open http://localhost:16686
2. Select service: `cdc-consumer`
3. Search for operations: `cdc.public.customer process`
4. Click on a trace to view spans and attributes

### Trace Context Propagation

The consumer extracts W3C Trace Context from Kafka message headers when present, allowing traces to span across services. If no trace context exists, a new trace is started.

## Metrics

The application exposes custom metrics via OpenTelemetry:

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cdc_messages_processed_total` | Counter | topic, partition, operation | Total CDC messages processed |
| `cdc_messages_errors_total` | Counter | topic, partition | Processing errors |
| `cdc_processing_latency` | Histogram | topic, partition | Processing latency (ms) |
| `cdc_db_upserts_total` | Counter | - | Database upsert operations |
| `cdc_db_deletes_total` | Counter | - | Database delete operations |

### Prometheus Queries

```promql
# Throughput (messages/sec)
rate(cdc_messages_processed_total[1m])

# Error rate
rate(cdc_messages_errors_total[1m]) / rate(cdc_messages_processed_total[1m])

# P95 latency
histogram_quantile(0.95, rate(cdc_processing_latency_bucket[5m]))

# P99 latency
histogram_quantile(0.99, rate(cdc_processing_latency_bucket[5m]))

# Upsert vs Delete ratio
sum(cdc_db_upserts_total) / sum(cdc_db_deletes_total)

# Consumer lag (Kafka client metrics)
kafka_consumer_records_lag_max
```

### Verifying Metrics in Prometheus

1. Open http://localhost:9090
2. Go to Status → Targets to verify `otel-collector` is UP
3. Use the Expression Browser to query metrics
4. Example: Enter `cdc_messages_processed_total` and click Execute

## Structured Logging

Logs are output in JSON format with automatic trace correlation via the OpenTelemetry Logback integration. Logs are also exported via OTLP to Loki for centralized storage and querying.

### Log Pipeline

```
CDC Consumer → OTel Logback Appender → OTel Collector → Loki → Grafana
```

### Log Format (Console)

```json
{
  "@timestamp": "2024-01-15T10:30:00.123+0000",
  "level": "INFO",
  "logger_name": "com.pintailconsultingllc.cdcdebezium.consumer.CustomerCdcConsumer",
  "message": "CDC event processed successfully",
  "thread_name": "org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1",
  "service": "cdc-consumer",
  "trace_id": "abc123def456",
  "span_id": "789xyz",
  "kafka_topic": "cdc.public.customer",
  "kafka_partition": "0",
  "kafka_offset": "42",
  "message_key": "550e8400-e29b-41d4-a716-44665544",
  "customer_id": "550e8400-e29b-41d4-a716-446655440001",
  "db_operation": "upsert",
  "processing_outcome": "success"
}
```

### Log Labels in Loki

When querying logs in Loki/Grafana, the following labels are available:

| Label | Description | Example |
|-------|-------------|---------|
| `service_name` | OTel service name | `cdc-consumer` |
| `level` | Log level (lowercase) | `info`, `debug`, `warn`, `error` |
| `logger` | Logger class name | `com.pintailconsultingllc...` |
| `traceId` | OpenTelemetry trace ID | `abc123def456` |
| `spanId` | OpenTelemetry span ID | `789xyz` |

### Viewing Logs in Grafana

1. Open Grafana: http://localhost:3000
2. Go to **Explore** → Select **Loki** data source
3. Use LogQL queries:

```logql
# All CDC consumer logs
{service_name="cdc-consumer"}

# Error logs only
{service_name="cdc-consumer", level="error"}

# Filter by log message content
{service_name="cdc-consumer"} |= "CDC event processed"

# Logs with specific trace ID
{service_name="cdc-consumer"} | json | traceId="abc123def456"
```

### Log Fields Reference

| Field | Description | Example |
|-------|-------------|---------|
| `trace_id` | OpenTelemetry trace ID | `abc123def456` |
| `span_id` | OpenTelemetry span ID | `789xyz` |
| `kafka_topic` | Source Kafka topic | `cdc.public.customer` |
| `kafka_partition` | Kafka partition number | `0` |
| `kafka_offset` | Message offset in partition | `42` |
| `message_key` | Kafka message key (truncated) | `550e8400-e29b-41d4...` |
| `customer_id` | Customer UUID from payload | `550e8400-...` |
| `db_operation` | Database operation performed | `upsert`, `delete`, `ignore` |
| `processing_outcome` | Processing result | `success`, `error` |
| `error_type` | Exception class (errors only) | `JsonParseException` |

### Correlating Logs with Traces

**In Grafana (recommended):**
1. Open Grafana: http://localhost:3000
2. Go to **Explore** → Select **Tempo** data source
3. Search for a trace
4. Click on a span to see linked logs (Tempo → Loki correlation)

**In Jaeger (legacy):**
1. Find a `trace_id` in application logs
2. Open Jaeger: http://localhost:16686
3. Use "Search by Trace ID" with the trace ID
4. View the corresponding trace with spans
5. Span attributes should match log fields

## Configuration

### OTel Collector Configuration

The collector is configured via `docker/otel/otel-collector-config.yaml`:

- **Receivers**: OTLP gRPC (4317) and HTTP (4318)
- **Processors**: Batch processing, memory limiter
- **Exporters**: Jaeger (traces), Prometheus (metrics)

### Prometheus Configuration

Prometheus scrape configuration is in `docker/prometheus/prometheus.yml`:

- Scrapes OTel Collector's Prometheus exporter (port 8889)
- Scrapes OTel Collector's internal metrics (port 8888)
- Can scrape Spring Boot Actuator endpoints if enabled
