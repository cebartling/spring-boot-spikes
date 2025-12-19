# PLAN-004: Spring Boot Kafka Consumer Foundation

## Objective

Implement the basic Spring Boot Kafka consumer that receives CDC events from the `cdc.public.customer` topic with manual acknowledgements and single-threaded consumption.

## Dependencies

- PLAN-001: Docker Compose infrastructure
- PLAN-003: Debezium connector (producing CDC events)

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add Spring Kafka dependency |
| `src/main/resources/application.yml` | Kafka consumer configuration |
| `src/.../dto/CustomerCdcEvent.kt` | DTO for CDC event payload |
| `src/.../consumer/CustomerCdcConsumer.kt` | Kafka listener implementation |

### build.gradle.kts Additions

```kotlin
dependencies {
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
```

### application.yml Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: cdc-consumer-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      properties:
        max.poll.records: 1
    listener:
      ack-mode: manual
      concurrency: 1
```

### CustomerCdcEvent.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class CustomerCdcEvent(
    val id: UUID,
    val email: String?,
    val status: String?,
    @JsonProperty("updated_at")
    val updatedAt: Instant?,
    @JsonProperty("__deleted")
    val deleted: String? = null,
    @JsonProperty("__op")
    val operation: String? = null,
    @JsonProperty("__source_ts_ms")
    val sourceTimestamp: Long? = null
) {
    fun isDelete(): Boolean = deleted == "true" || operation == "d"
}
```

### CustomerCdcConsumer.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper
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
        val key = record.key()
        val value = record.value()

        logger.info(
            "Received CDC event: topic={}, partition={}, offset={}, key={}",
            record.topic(), record.partition(), record.offset(), key
        )

        try {
            when {
                value == null -> {
                    // Kafka tombstone - log and skip
                    logger.info("Received tombstone for key={}", key)
                }
                else -> {
                    val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                    processEvent(event)
                }
            }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error("Error processing CDC event: key={}", key, e)
            // For spike: acknowledge to skip bad messages
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent) {
        if (event.isDelete()) {
            logger.info("Processing DELETE for customer: id={}", event.id)
            // TODO: Implement delete logic in PLAN-005
        } else {
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            // TODO: Implement upsert logic in PLAN-005
        }
    }
}
```

### ObjectMapper Configuration

```kotlin
package com.pintailconsultingllc.cdcdebezium.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}
```

## Commands to Run

```bash
# Ensure infrastructure is running
docker compose up -d postgres kafka kafka-connect

# Build the application
./gradlew build

# Run the application
./gradlew bootRun

# In another terminal, verify consumer joined the group
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group

# Insert a test record to trigger CDC event
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'test-consumer@example.com', 'active');"

# Check application logs for "Received CDC event" and "Processing UPSERT"

# Update the record
docker compose exec postgres psql -U postgres -c \
  "UPDATE customer SET status = 'inactive' WHERE email = 'test-consumer@example.com';"

# Delete the record
docker compose exec postgres psql -U postgres -c \
  "DELETE FROM customer WHERE email = 'test-consumer@example.com';"

# Check logs for DELETE processing and tombstone handling
```

## Acceptance Criteria

1. [ ] Application starts without errors
2. [ ] Consumer joins group `cdc-consumer-group`
3. [ ] Existing CDC messages (from snapshot) are consumed on startup
4. [ ] INSERT events are logged as "Processing UPSERT"
5. [ ] UPDATE events are logged as "Processing UPSERT"
6. [ ] DELETE events (with `__deleted: true`) are logged as "Processing DELETE"
7. [ ] Kafka tombstones (null value) are handled without error
8. [ ] Manual acknowledgement works (offsets committed)
9. [ ] Consumer survives malformed JSON (logs error, continues)
10. [ ] Consumer resumes from last committed offset after restart

## Estimated Complexity

Medium - Spring Kafka setup is straightforward, but handling CDC message format requires attention.

## Notes

- `enable-auto-commit: false` + `ack-mode: manual` ensures at-least-once delivery
- `max.poll.records: 1` simplifies debugging; can be increased later
- `FAIL_ON_UNKNOWN_PROPERTIES: false` allows schema evolution (new fields)
- The consumer is single-threaded (`concurrency: 1`) as per requirements
