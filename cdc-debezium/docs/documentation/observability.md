# Observability

This project includes full OpenTelemetry observability with distributed tracing, metrics, and structured logging.

## Observability Stack

| Component | Port | Purpose |
|-----------|------|---------|
| OpenTelemetry Collector | 4317, 4318 | OTLP receiver (gRPC/HTTP) |
| Jaeger | 16686 | Distributed tracing UI |
| Prometheus | 9090 | Metrics storage and querying |

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

## Accessing Observability UIs

```bash
# Open Jaeger UI (traces)
open http://localhost:16686

# Open Prometheus UI (metrics)
open http://localhost:9090

# Check OTel Collector health
curl -s http://localhost:8888/metrics | head -20
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

Logs are output in JSON format with automatic trace correlation via the OpenTelemetry Logback integration.

### Log Format

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
