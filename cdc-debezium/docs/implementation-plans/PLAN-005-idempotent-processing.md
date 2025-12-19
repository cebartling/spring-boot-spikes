# PLAN-005: Idempotent Upsert/Delete Processing

## Objective

Implement the data persistence layer with idempotent upsert and delete operations using R2DBC reactive database access.

## Dependencies

- PLAN-004: Spring Boot Kafka consumer foundation

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add R2DBC dependencies |
| `src/main/resources/application.yml` | R2DBC connection configuration |
| `src/.../entity/CustomerEntity.kt` | R2DBC entity |
| `src/.../repository/CustomerRepository.kt` | Reactive repository |
| `src/.../service/CustomerService.kt` | Business logic with idempotency |
| `src/.../consumer/CustomerCdcConsumer.kt` | Wire service into consumer |

### build.gradle.kts Additions

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("io.r2dbc:r2dbc-pool")
}
```

### application.yml R2DBC Configuration

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
    pool:
      initial-size: 5
      max-size: 10
```

### CustomerEntity.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("customer_materialized")
data class CustomerEntity(
    @Id
    val id: UUID,
    val email: String,
    val status: String,
    val updatedAt: Instant,
    val sourceTimestamp: Long? = null
)
```

### Materialized Table Schema

Add to `docker/postgres/init/01-schema.sql`:

```sql
-- Materialized view of customer data (populated by CDC consumer)
CREATE TABLE public.customer_materialized (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    status TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    source_timestamp BIGINT
);
```

### CustomerRepository.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.entity.CustomerEntity
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import java.util.UUID

interface CustomerRepository : ReactiveCrudRepository<CustomerEntity, UUID>
```

### CustomerService.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.entity.CustomerEntity
import com.pintailconsultingllc.cdcdebezium.repository.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class CustomerService(
    private val customerRepository: CustomerRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Idempotent upsert: insert or update based on primary key.
     * Uses source timestamp for optimistic concurrency - only applies
     * updates if the incoming event is newer than existing data.
     */
    fun upsert(event: CustomerCdcEvent): Mono<CustomerEntity> {
        val entity = CustomerEntity(
            id = event.id,
            email = event.email ?: "",
            status = event.status ?: "",
            updatedAt = event.updatedAt ?: java.time.Instant.now(),
            sourceTimestamp = event.sourceTimestamp
        )

        return customerRepository.findById(event.id)
            .flatMap { existing ->
                // Only update if incoming event is newer
                if (shouldUpdate(existing, entity)) {
                    logger.debug("Updating customer: id={}", event.id)
                    customerRepository.save(entity)
                } else {
                    logger.debug("Skipping stale update for customer: id={}", event.id)
                    Mono.just(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Inserting new customer: id={}", event.id)
                    customerRepository.save(entity)
                }
            )
    }

    /**
     * Idempotent delete: succeeds even if record doesn't exist.
     */
    fun delete(id: java.util.UUID): Mono<Void> {
        return customerRepository.findById(id)
            .flatMap { existing ->
                logger.debug("Deleting customer: id={}", id)
                customerRepository.delete(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Customer already deleted or never existed: id={}", id)
                    Mono.empty()
                }
            )
    }

    private fun shouldUpdate(existing: CustomerEntity, incoming: CustomerEntity): Boolean {
        // If we have source timestamps, use them for ordering
        val existingTs = existing.sourceTimestamp
        val incomingTs = incoming.sourceTimestamp

        return when {
            existingTs == null || incomingTs == null -> true
            incomingTs > existingTs -> true
            else -> false
        }
    }
}
```

### Updated CustomerCdcConsumer.kt

```kotlin
@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerService
) {
    // ... existing code ...

    private fun processEvent(event: CustomerCdcEvent): Mono<Void> {
        return if (event.isDelete()) {
            logger.info("Processing DELETE for customer: id={}", event.id)
            customerService.delete(event.id)
        } else {
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            customerService.upsert(event).then()
        }
    }

    @KafkaListener(
        topics = ["cdc.public.customer"],
        groupId = "cdc-consumer-group"
    )
    fun consume(
        record: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment
    ) {
        // ... handle tombstones ...

        try {
            val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
            processEvent(event)
                .doOnSuccess { acknowledgment.acknowledge() }
                .doOnError { e ->
                    logger.error("Error processing CDC event: key={}", key, e)
                    acknowledgment.acknowledge() // Skip bad messages for spike
                }
                .block() // Block for manual ack mode
        } catch (e: Exception) {
            logger.error("Error deserializing CDC event: key={}", key, e)
            acknowledgment.acknowledge()
        }
    }
}
```

## Commands to Run

```bash
# Recreate postgres with new schema
docker compose down postgres
docker volume rm cdc-debezium_postgres-data || true
docker compose up -d postgres

# Wait for postgres, then redeploy connector to re-snapshot
curl -X DELETE http://localhost:8083/connectors/postgres-cdc-connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium/connector-config.json

# Build and run
./gradlew build
./gradlew bootRun

# Verify materialized table is populated
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized;"

# Test idempotency: run the same INSERT twice via CDC
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES
   ('550e8400-e29b-41d4-a716-446655440099', 'idempotent@example.com', 'active');"

# Check materialized table
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email = 'idempotent@example.com';"

# Test update
docker compose exec postgres psql -U postgres -c \
  "UPDATE customer SET status = 'inactive' WHERE email = 'idempotent@example.com';"

# Verify update propagated
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email = 'idempotent@example.com';"

# Test delete
docker compose exec postgres psql -U postgres -c \
  "DELETE FROM customer WHERE email = 'idempotent@example.com';"

# Verify delete propagated
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email = 'idempotent@example.com';"
# Should return 0 rows

# Test idempotent delete (delete already-deleted record)
# This should not cause an error in the consumer
```

## Acceptance Criteria

1. [ ] R2DBC connects to PostgreSQL successfully
2. [ ] Snapshot data materializes into `customer_materialized` table
3. [ ] INSERT events create new rows in materialized table
4. [ ] UPDATE events modify existing rows
5. [ ] DELETE events remove rows from materialized table
6. [ ] Duplicate INSERT (same ID) does not create duplicate rows
7. [ ] Duplicate UPDATE (same ID, same data) is handled gracefully
8. [ ] DELETE on non-existent record succeeds without error
9. [ ] Out-of-order events are handled via source timestamp comparison
10. [ ] Consumer survives database connection issues (logs error, continues)

## Idempotency Guarantees

| Scenario | Behavior |
|----------|----------|
| First INSERT | Creates row |
| Duplicate INSERT (same ID) | Updates row (no error) |
| UPDATE after INSERT | Updates row |
| Duplicate UPDATE | No-op if timestamps match |
| DELETE after INSERT | Removes row |
| DELETE on missing row | No-op (success) |
| Out-of-order (old event after new) | Skipped via timestamp check |

## Estimated Complexity

Medium - R2DBC reactive patterns require familiarity; timestamp-based idempotency needs careful implementation.

## Notes

- Using a separate `customer_materialized` table avoids conflicts with source table
- Source timestamp comparison prevents out-of-order event processing
- The `block()` call in consumer is necessary for manual acknowledgement mode
- Consider adding retry logic for transient database failures in production
