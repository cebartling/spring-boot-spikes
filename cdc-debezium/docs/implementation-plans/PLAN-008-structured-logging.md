# PLAN-008: Structured Logging with Trace Correlation

## Objective

Configure structured JSON logging with automatic trace ID and span ID injection, enabling log correlation with distributed traces in Jaeger.

## Dependencies

- PLAN-006: OpenTelemetry tracing (provides trace context)

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add logging dependencies |
| `src/main/resources/application.yml` | Logging configuration |
| `src/main/resources/logback-spring.xml` | Logback configuration with JSON encoder |
| `src/.../consumer/CustomerCdcConsumer.kt` | Add structured log fields |

### build.gradle.kts Additions

```kotlin
dependencies {
    // Structured JSON logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // OTel Logback integration for trace context
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.1.0-alpha")
}
```

### logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Import OTel trace context into MDC -->
    <contextListener class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"/>

    <!-- Console appender with JSON format -->
    <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>trace_id</includeMdcKeyName>
            <includeMdcKeyName>span_id</includeMdcKeyName>
            <includeMdcKeyName>trace_flags</includeMdcKeyName>

            <!-- Custom field names for CDC context -->
            <customFields>{"service":"cdc-consumer"}</customFields>

            <!-- Include caller info for debugging -->
            <includeCallerData>false</includeCallerData>

            <!-- Timestamp format -->
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSZ</timestampPattern>
        </encoder>
    </appender>

    <!-- Plain console for development (optional) -->
    <appender name="CONSOLE_PLAIN" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [trace_id=%X{trace_id} span_id=%X{span_id}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Root logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE_JSON"/>
    </root>

    <!-- Application loggers -->
    <logger name="com.pintailconsultingllc.cdcdebezium" level="DEBUG"/>

    <!-- Reduce noise from libraries -->
    <logger name="org.apache.kafka" level="WARN"/>
    <logger name="org.springframework.kafka" level="INFO"/>
    <logger name="io.r2dbc" level="INFO"/>
</configuration>
```

### application.yml Logging Configuration

```yaml
logging:
  level:
    root: INFO
    com.pintailconsultingllc.cdcdebezium: DEBUG
    org.apache.kafka: WARN
    org.springframework.kafka: INFO
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Enhanced CustomerCdcConsumer.kt with Structured Logging

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
                // Add CDC-specific fields to MDC for structured logging
                MDC.put("kafka_topic", record.topic())
                MDC.put("kafka_partition", record.partition().toString())
                MDC.put("kafka_offset", record.offset().toString())
                MDC.put("message_key", record.key()?.take(36) ?: "null") // Truncate for safety

                try {
                    val key = record.key()
                    val value = record.value()

                    logger.info("Received CDC event")

                    val (operation, outcome) = when {
                        value == null -> {
                            logger.info("Processing tombstone message")
                            tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                            "ignore" to "success"
                        }
                        else -> {
                            val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                            MDC.put("customer_id", event.id.toString())
                            processEvent(event, span)
                        }
                    }

                    MDC.put("processing_outcome", outcome)
                    MDC.put("db_operation", operation)

                    metricsService.recordMessageProcessed(record.topic(), record.partition(), operation)
                    metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())

                    logger.info("CDC event processed successfully")

                    acknowledgment.acknowledge()
                    tracingService.endSpanSuccess(span)

                } finally {
                    // Clean up MDC
                    MDC.remove("kafka_topic")
                    MDC.remove("kafka_partition")
                    MDC.remove("kafka_offset")
                    MDC.remove("message_key")
                    MDC.remove("customer_id")
                    MDC.remove("processing_outcome")
                    MDC.remove("db_operation")
                }
            }
        } catch (e: Exception) {
            MDC.put("processing_outcome", "error")
            MDC.put("error_type", e.javaClass.simpleName)

            logger.error("Error processing CDC event: {}", e.message, e)

            metricsService.recordMessageError(record.topic(), record.partition())
            metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()

            MDC.remove("processing_outcome")
            MDC.remove("error_type")
        }
    }

    private fun processEvent(event: CustomerCdcEvent, span: Span): Pair<String, String> {
        return if (event.isDelete()) {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)
            logger.info("Processing DELETE operation")
            customerService.delete(event.id).block()
            metricsService.recordDbDelete()
            "delete" to "success"
        } else {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)
            logger.info("Processing UPSERT operation for email={}, status={}", event.email, event.status)
            customerService.upsert(event).block()
            metricsService.recordDbUpsert()
            "upsert" to "success"
        }
    }
}
```

## Expected Log Output (JSON)

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

## Commands to Run

```bash
# Build and run
./gradlew build
./gradlew bootRun

# Generate CDC events
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'logging-test@example.com', 'active');"

# Observe JSON logs in console output
# Look for trace_id and span_id fields

# Find a trace_id from the logs, then search in Jaeger
open http://localhost:16686
# Search by trace ID to correlate logs with traces

# Test error logging
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES ('invalid-uuid', 'bad@example.com', 'active');"
# This should produce an error log with trace correlation
```

## Acceptance Criteria

1. [ ] Logs are output in JSON format
2. [ ] Each log entry includes:
   - [ ] `trace_id` (from OTel context)
   - [ ] `span_id` (from OTel context)
   - [ ] `kafka_topic`
   - [ ] `kafka_partition`
   - [ ] `kafka_offset`
   - [ ] `message_key` (truncated if needed)
3. [ ] Successful processing logs include:
   - [ ] `db_operation` (upsert/delete/ignore)
   - [ ] `processing_outcome` (success)
   - [ ] `customer_id`
4. [ ] Error logs include:
   - [ ] `processing_outcome` (error)
   - [ ] `error_type` (exception class name)
   - [ ] Stack trace
5. [ ] `trace_id` in logs matches trace ID in Jaeger
6. [ ] Logs can be searched/filtered by structured fields

## Log Fields Reference

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

## Jaeger Correlation

1. Copy `trace_id` from a log entry
2. Open Jaeger UI: http://localhost:16686
3. Search by Trace ID
4. View the corresponding trace with spans
5. Span attributes should match log fields

## Estimated Complexity

Low-Medium - Logback configuration is straightforward; main effort is ensuring consistent MDC usage.

## Notes

- MDC (Mapped Diagnostic Context) is thread-local, so clean up after processing
- The OTel Logback integration automatically injects `trace_id` and `span_id`
- Truncate sensitive/large fields (like message_key) for safety
- JSON logging can be verbose; consider log levels carefully in production
- Logstash encoder is widely used and compatible with ELK stack
