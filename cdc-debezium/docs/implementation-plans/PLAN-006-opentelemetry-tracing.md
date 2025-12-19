# PLAN-006: OpenTelemetry Tracing Integration

## Objective

Add distributed tracing to the CDC consumer using OpenTelemetry, with proper span creation, context propagation from Kafka headers, and required semantic attributes.

## Dependencies

- PLAN-004: Spring Boot Kafka consumer
- PLAN-009: OpenTelemetry infrastructure (Collector, Jaeger) - can develop in parallel

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add OpenTelemetry dependencies |
| `src/main/resources/application.yml` | OpenTelemetry configuration |
| `src/.../config/OpenTelemetryConfig.kt` | OTel SDK configuration |
| `src/.../tracing/CdcTracingInterceptor.kt` | Custom span attributes |
| `src/.../consumer/CustomerCdcConsumer.kt` | Add tracing to consumer |

### build.gradle.kts Additions

```kotlin
dependencies {
    // OpenTelemetry BOM
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.35.0"))
    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.1.0-alpha"))

    // Core OTel APIs
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Spring Boot instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    // Kafka instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-kafka-clients-2.6")

    // Context propagation
    implementation("io.opentelemetry:opentelemetry-context")
}
```

### application.yml OpenTelemetry Configuration

```yaml
otel:
  service:
    name: cdc-consumer
  exporter:
    otlp:
      endpoint: http://localhost:4317
      protocol: grpc
  traces:
    exporter: otlp
  metrics:
    exporter: otlp
  logs:
    exporter: otlp

spring:
  application:
    name: cdc-consumer
```

### OpenTelemetryConfig.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenTelemetryConfig {

    @Value("\${otel.service.name:cdc-consumer}")
    private lateinit var serviceName: String

    @Value("\${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private lateinit var otlpEndpoint: String

    @Bean
    fun openTelemetry(): OpenTelemetry {
        val resource = Resource.getDefault()
            .merge(Resource.create(
                io.opentelemetry.api.common.Attributes.of(
                    ResourceAttributes.SERVICE_NAME, serviceName
                )
            ))

        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal()
    }

    @Bean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer("cdc-consumer", "1.0.0")
    }
}
```

### CdcTracingService.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.tracing

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.stereotype.Component

@Component
class CdcTracingService(
    private val tracer: Tracer
) {
    companion object {
        // Semantic conventions for messaging
        private val MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system")
        private val MESSAGING_DESTINATION_NAME = AttributeKey.stringKey("messaging.destination.name")
        private val MESSAGING_KAFKA_CONSUMER_GROUP = AttributeKey.stringKey("messaging.kafka.consumer.group")
        private val MESSAGING_KAFKA_PARTITION = AttributeKey.longKey("messaging.kafka.partition")
        private val MESSAGING_KAFKA_MESSAGE_OFFSET = AttributeKey.longKey("messaging.kafka.message.offset")
        private val MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation")
        private val DB_OPERATION = AttributeKey.stringKey("db.operation")
        private val CUSTOMER_ID = AttributeKey.stringKey("customer.id")
    }

    private val kafkaHeaderGetter = object : TextMapGetter<Headers> {
        override fun keys(carrier: Headers): Iterable<String> =
            carrier.map { it.key() }

        override fun get(carrier: Headers?, key: String): String? =
            carrier?.lastHeader(key)?.value()?.let { String(it) }
    }

    fun startSpan(
        record: ConsumerRecord<String, String?>,
        consumerGroup: String
    ): Span {
        // Extract context from Kafka headers if present
        val extractedContext = io.opentelemetry.api.GlobalOpenTelemetry.getPropagators()
            .textMapPropagator
            .extract(Context.current(), record.headers(), kafkaHeaderGetter)

        val span = tracer.spanBuilder("cdc.public.customer process")
            .setParent(extractedContext)
            .setSpanKind(SpanKind.CONSUMER)
            .setAllAttributes(
                Attributes.builder()
                    .put(MESSAGING_SYSTEM, "kafka")
                    .put(MESSAGING_DESTINATION_NAME, record.topic())
                    .put(MESSAGING_KAFKA_CONSUMER_GROUP, consumerGroup)
                    .put(MESSAGING_KAFKA_PARTITION, record.partition().toLong())
                    .put(MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset())
                    .put(MESSAGING_OPERATION, "process")
                    .build()
            )
            .startSpan()

        // Add message key as customer ID
        record.key()?.let { key ->
            span.setAttribute(CUSTOMER_ID, key)
        }

        return span
    }

    fun setDbOperation(span: Span, operation: DbOperation) {
        span.setAttribute(DB_OPERATION, operation.value)
    }

    fun endSpanSuccess(span: Span) {
        span.setStatus(StatusCode.OK)
        span.end()
    }

    fun endSpanError(span: Span, error: Throwable) {
        span.setStatus(StatusCode.ERROR, error.message ?: "Unknown error")
        span.recordException(error)
        span.end()
    }

    enum class DbOperation(val value: String) {
        UPSERT("upsert"),
        DELETE("delete"),
        IGNORE("ignore")
    }
}
```

### Updated CustomerCdcConsumer.kt with Tracing

```kotlin
@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerService,
    private val tracingService: CdcTracingService
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

        try {
            span.makeCurrent().use { scope ->
                val key = record.key()
                val value = record.value()

                logger.info(
                    "Received CDC event: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), key
                )

                when {
                    value == null -> {
                        logger.info("Received tombstone for key={}", key)
                        tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                    }
                    else -> {
                        val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                        processEvent(event, span)
                    }
                }

                acknowledgment.acknowledge()
                tracingService.endSpanSuccess(span)
            }
        } catch (e: Exception) {
            logger.error("Error processing CDC event: key={}", record.key(), e)
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent, span: Span) {
        if (event.isDelete()) {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)
            logger.info("Processing DELETE for customer: id={}", event.id)
            customerService.delete(event.id).block()
        } else {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            customerService.upsert(event).block()
        }
    }
}
```

## Commands to Run

```bash
# Ensure OTel Collector and Jaeger are running (from PLAN-009)
docker compose up -d otel-collector jaeger

# Build and run
./gradlew build
./gradlew bootRun

# Generate some CDC events
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'traced@example.com', 'active');"

docker compose exec postgres psql -U postgres -c \
  "UPDATE customer SET status = 'inactive' WHERE email = 'traced@example.com';"

docker compose exec postgres psql -U postgres -c \
  "DELETE FROM customer WHERE email = 'traced@example.com';"

# Open Jaeger UI
open http://localhost:16686

# Search for service: cdc-consumer
# Verify spans appear with correct attributes
```

## Acceptance Criteria

1. [ ] OpenTelemetry SDK initializes without errors
2. [ ] Each CDC message creates a span
3. [ ] Span has correct semantic attributes:
   - [ ] `messaging.system = kafka`
   - [ ] `messaging.destination.name = cdc.public.customer`
   - [ ] `messaging.kafka.consumer.group = cdc-consumer-group`
   - [ ] `messaging.kafka.partition` (number)
   - [ ] `messaging.kafka.message.offset` (number)
   - [ ] `messaging.operation = process`
   - [ ] `db.operation = upsert | delete | ignore`
4. [ ] Span `kind` is `CONSUMER`
5. [ ] Traces appear in Jaeger UI
6. [ ] Error cases record exception on span
7. [ ] Successful processing shows `OK` status
8. [ ] Context propagation works (if trace headers present in Kafka message)

## Expected Jaeger Trace View

```
Service: cdc-consumer
Operation: cdc.public.customer process

Tags:
  messaging.system: kafka
  messaging.destination.name: cdc.public.customer
  messaging.kafka.consumer.group: cdc-consumer-group
  messaging.kafka.partition: 0
  messaging.kafka.message.offset: 42
  messaging.operation: process
  db.operation: upsert
  customer.id: 550e8400-e29b-41d4-a716-446655440001
```

## Estimated Complexity

Medium-High - OpenTelemetry configuration has many options; context propagation requires understanding of W3C trace context.

## Notes

- Using Spring Boot OTel starter simplifies auto-instrumentation
- Manual span creation gives control over attributes
- W3C Trace Context is the standard propagation format
- Kafka instrumentation can auto-create spans, but we create custom ones for CDC-specific attributes
- The `makeCurrent()` call is important for context propagation to child operations
