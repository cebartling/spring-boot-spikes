# PLAN-007: OpenTelemetry Metrics Integration

## Objective

Add metrics instrumentation to the CDC consumer for throughput, latency, error rates, and database operation counts, exported via OpenTelemetry to Prometheus.

## Dependencies

- PLAN-006: OpenTelemetry tracing (shared OTel SDK)
- PLAN-009: Observability infrastructure (Prometheus) - can develop in parallel

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add OTel metrics dependencies |
| `src/.../config/OpenTelemetryConfig.kt` | Add meter provider |
| `src/.../metrics/CdcMetricsService.kt` | Metrics recording service |
| `src/.../consumer/CustomerCdcConsumer.kt` | Record metrics in consumer |

### build.gradle.kts Additions

```kotlin
dependencies {
    // OTel Metrics (add to existing OTel dependencies)
    implementation("io.opentelemetry:opentelemetry-sdk-metrics")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Micrometer bridge for Kafka client metrics
    implementation("io.micrometer:micrometer-registry-otlp")
}
```

### OpenTelemetryConfig.kt Updates

```kotlin
@Configuration
class OpenTelemetryConfig {

    @Bean
    fun openTelemetry(): OpenTelemetry {
        // ... existing resource setup ...

        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build()

        val metricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        val meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(metricExporter)
                    .setInterval(java.time.Duration.ofSeconds(10))
                    .build()
            )
            .setResource(resource)
            .build()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal()
    }

    @Bean
    fun meter(openTelemetry: OpenTelemetry): Meter {
        return openTelemetry.getMeter("cdc-consumer", "1.0.0")
    }
}
```

### CdcMetricsService.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.metrics

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.Meter
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class CdcMetricsService(
    private val meter: Meter
) {
    private lateinit var messagesProcessed: LongCounter
    private lateinit var messagesErrored: LongCounter
    private lateinit var processingLatency: LongHistogram
    private lateinit var dbUpserts: LongCounter
    private lateinit var dbDeletes: LongCounter

    companion object {
        private val TOPIC = AttributeKey.stringKey("topic")
        private val PARTITION = AttributeKey.longKey("partition")
        private val OPERATION = AttributeKey.stringKey("operation")
        private val OUTCOME = AttributeKey.stringKey("outcome")
    }

    @PostConstruct
    fun init() {
        messagesProcessed = meter.counterBuilder("cdc.messages.processed")
            .setDescription("Total number of CDC messages processed")
            .setUnit("{messages}")
            .build()

        messagesErrored = meter.counterBuilder("cdc.messages.errors")
            .setDescription("Total number of CDC message processing errors")
            .setUnit("{messages}")
            .build()

        processingLatency = meter.histogramBuilder("cdc.processing.latency")
            .setDescription("CDC message processing latency")
            .setUnit("ms")
            .ofLongs()
            .build()

        dbUpserts = meter.counterBuilder("cdc.db.upserts")
            .setDescription("Total number of database upsert operations")
            .setUnit("{operations}")
            .build()

        dbDeletes = meter.counterBuilder("cdc.db.deletes")
            .setDescription("Total number of database delete operations")
            .setUnit("{operations}")
            .build()
    }

    fun recordMessageProcessed(topic: String, partition: Int, operation: String) {
        messagesProcessed.add(1, Attributes.of(
            TOPIC, topic,
            PARTITION, partition.toLong(),
            OPERATION, operation
        ))
    }

    fun recordMessageError(topic: String, partition: Int) {
        messagesErrored.add(1, Attributes.of(
            TOPIC, topic,
            PARTITION, partition.toLong()
        ))
    }

    fun recordProcessingLatency(startTime: Instant, topic: String, partition: Int) {
        val latencyMs = Duration.between(startTime, Instant.now()).toMillis()
        processingLatency.record(latencyMs, Attributes.of(
            TOPIC, topic,
            PARTITION, partition.toLong()
        ))
    }

    fun recordDbUpsert() {
        dbUpserts.add(1)
    }

    fun recordDbDelete() {
        dbDeletes.add(1)
    }

    /**
     * Timer helper for measuring operation duration.
     */
    inline fun <T> timed(
        topic: String,
        partition: Int,
        block: () -> T
    ): T {
        val start = Instant.now()
        try {
            return block()
        } finally {
            recordProcessingLatency(start, topic, partition)
        }
    }
}
```

### KafkaMetricsConfig.kt (Consumer Lag)

```kotlin
package com.pintailconsultingllc.cdcdebezium.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import org.apache.kafka.clients.consumer.Consumer
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import jakarta.annotation.PostConstruct

@Configuration
class KafkaMetricsConfig(
    private val consumerFactory: ConsumerFactory<String, String>,
    private val meterRegistry: MeterRegistry
) {
    // Kafka client metrics are auto-registered by Spring Boot Actuator
    // when micrometer is on the classpath
}
```

### Updated CustomerCdcConsumer.kt with Metrics

```kotlin
@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerService,
    private val tracingService: CdcTracingService,
    private val metricsService: CdcMetricsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["cdc.public.customer"],
        groupId = "cdc-consumer-group"
    )
    fun consume(
        record: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment
    ) {
        val span = tracingService.startSpan(record, "cdc-consumer-group")
        val startTime = java.time.Instant.now()

        try {
            span.makeCurrent().use { scope ->
                val key = record.key()
                val value = record.value()

                logger.info(
                    "Received CDC event: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), key
                )

                val operation = when {
                    value == null -> {
                        logger.info("Received tombstone for key={}", key)
                        tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                        "ignore"
                    }
                    else -> {
                        val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                        processEvent(event, span)
                    }
                }

                // Record success metrics
                metricsService.recordMessageProcessed(record.topic(), record.partition(), operation)
                metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())

                acknowledgment.acknowledge()
                tracingService.endSpanSuccess(span)
            }
        } catch (e: Exception) {
            logger.error("Error processing CDC event: key={}", record.key(), e)
            metricsService.recordMessageError(record.topic(), record.partition())
            metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent, span: Span): String {
        return if (event.isDelete()) {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)
            logger.info("Processing DELETE for customer: id={}", event.id)
            customerService.delete(event.id).block()
            metricsService.recordDbDelete()
            "delete"
        } else {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            customerService.upsert(event).block()
            metricsService.recordDbUpsert()
            "upsert"
        }
    }
}
```

## Commands to Run

```bash
# Ensure Prometheus is running (from PLAN-009)
docker compose up -d prometheus otel-collector

# Build and run
./gradlew build
./gradlew bootRun

# Generate CDC events to create metrics
for i in {1..10}; do
  docker compose exec postgres psql -U postgres -c \
    "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'metrics-$i@example.com', 'active');"
  sleep 0.5
done

# Query Prometheus for metrics
open http://localhost:9090

# Example PromQL queries:
# - cdc_messages_processed_total
# - rate(cdc_messages_processed_total[1m])
# - histogram_quantile(0.95, rate(cdc_processing_latency_bucket[5m]))
# - cdc_db_upserts_total
# - cdc_db_deletes_total
# - cdc_messages_errors_total

# Check consumer lag (Kafka client metrics via Micrometer)
# - kafka_consumer_records_lag_max
```

## Acceptance Criteria

1. [ ] Metrics are exported to OTel Collector
2. [ ] Metrics appear in Prometheus
3. [ ] Counter `cdc.messages.processed` increments on each message
4. [ ] Counter `cdc.messages.errors` increments on processing errors
5. [ ] Histogram `cdc.processing.latency` records latency distribution
6. [ ] Counter `cdc.db.upserts` tracks upsert operations
7. [ ] Counter `cdc.db.deletes` tracks delete operations
8. [ ] Metrics have correct labels (topic, partition, operation)
9. [ ] Consumer lag metrics are available via Kafka client metrics

## Metrics Reference

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cdc_messages_processed_total` | Counter | topic, partition, operation | Messages processed |
| `cdc_messages_errors_total` | Counter | topic, partition | Processing errors |
| `cdc_processing_latency` | Histogram | topic, partition | Processing time (ms) |
| `cdc_db_upserts_total` | Counter | - | DB upsert operations |
| `cdc_db_deletes_total` | Counter | - | DB delete operations |

## Prometheus Queries

```promql
# Throughput (messages/sec)
rate(cdc_messages_processed_total[1m])

# Error rate
rate(cdc_messages_errors_total[1m]) / rate(cdc_messages_processed_total[1m])

# P95 latency
histogram_quantile(0.95, rate(cdc_processing_latency_bucket[5m]))

# Upsert vs Delete ratio
sum(cdc_db_upserts_total) / sum(cdc_db_deletes_total)
```

## Estimated Complexity

Medium - OTel metrics API is straightforward; main effort is identifying the right metrics to track.

## Notes

- Metrics are buffered and exported every 10 seconds (configurable)
- Use histograms for latency to get percentiles
- Labels add cardinality - keep them bounded
- Kafka client metrics (lag, etc.) come from Micrometer auto-instrumentation
- Consider adding custom labels for business dimensions if needed
