package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.EventStoreRepository
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ExtendedEventStoreRepository
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import io.r2dbc.spi.R2dbcException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Production implementation of EventStoreRepository using R2DBC and PostgreSQL.
 *
 * Uses the append_events stored procedure for atomic event appending with
 * optimistic concurrency control.
 */
@Repository
@Primary
class ProductEventStoreRepository(
    private val databaseClient: DatabaseClient,
    private val eventStreamRepository: EventStreamR2dbcRepository,
    private val domainEventRepository: DomainEventR2dbcRepository,
    private val eventSerializer: EventSerializer,
    private val eventDeserializer: EventDeserializer
) : EventStoreRepository, ExtendedEventStoreRepository {

    private val logger = LoggerFactory.getLogger(ProductEventStoreRepository::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    companion object {
        const val AGGREGATE_TYPE = "Product"

        /**
         * PostgreSQL SQL state for serialization_failure.
         * This is set by the append_events stored procedure when a concurrency conflict is detected.
         * Using the SQL state is more robust than matching error message text.
         */
        const val SQLSTATE_SERIALIZATION_FAILURE = "40001"
    }

    /**
     * Saves events to the event store without metadata.
     *
     * @param events List of events to save (must all belong to the same aggregate)
     * @return Mono<Void> that completes when events are persisted
     */
    override fun saveEvents(events: List<ProductEvent>): Mono<Void> {
        return saveEvents(events, null)
    }

    /**
     * Saves events to the event store with metadata for tracing and auditing.
     *
     * @param events List of events to save (must all belong to the same aggregate)
     * @param metadata Optional metadata including causationId, correlationId, and userId
     * @return Mono<Void> that completes when events are persisted
     */
    @Transactional
    override fun saveEvents(events: List<ProductEvent>, metadata: EventMetadata?): Mono<Void> {
        if (events.isEmpty()) {
            return Mono.empty()
        }

        val productId = events.first().productId
        val expectedVersion = events.first().version - 1

        // Verify all events belong to the same aggregate
        require(events.all { it.productId == productId }) {
            "All events must belong to the same aggregate"
        }

        return appendEventsUsingStoredProcedure(productId, expectedVersion, events, metadata)
            .doOnError { error ->
                logger.error("Failed to save events for Product $productId", error)
            }
            .then(Mono.fromRunnable {
                logger.info("Saved ${events.size} events for Product $productId")
            })
    }

    /**
     * Finds all events for a given aggregate ID in version order.
     */
    override fun findEventsByAggregateId(aggregateId: UUID): Flux<ProductEvent> {
        return eventStreamRepository.findByAggregateTypeAndAggregateId(AGGREGATE_TYPE, aggregateId)
            .flatMapMany { stream ->
                domainEventRepository.findByStreamIdOrderByAggregateVersion(stream.streamId)
            }
            .map { entity -> deserializeEvent(entity) }
            .doOnComplete {
                logger.debug("Loaded events for Product $aggregateId")
            }
    }

    /**
     * Finds events starting from a specific version (for partial replay).
     */
    override fun findEventsByAggregateIdFromVersion(
        aggregateId: UUID,
        fromVersion: Long
    ): Flux<ProductEvent> {
        return eventStreamRepository.findByAggregateTypeAndAggregateId(AGGREGATE_TYPE, aggregateId)
            .flatMapMany { stream ->
                domainEventRepository.findByStreamIdAndVersionGreaterThan(stream.streamId, fromVersion.toInt())
            }
            .map { entity -> deserializeEvent(entity) }
    }

    /**
     * Finds events by type within a time range.
     */
    override fun findEventsByTypeAndTimeRange(
        eventType: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): Flux<ProductEvent> {
        return domainEventRepository.findByEventTypeAndTimeRange(eventType, startTime, endTime)
            .map { entity -> deserializeEvent(entity) }
    }

    /**
     * Finds events by correlation ID (for distributed tracing).
     */
    override fun findEventsByCorrelationId(correlationId: UUID): Flux<ProductEvent> {
        return domainEventRepository.findByCorrelationId(correlationId)
            .map { entity -> deserializeEvent(entity) }
    }

    /**
     * Gets the current version of an aggregate's event stream.
     */
    override fun getStreamVersion(aggregateId: UUID): Mono<Long> {
        return eventStreamRepository.findByAggregateTypeAndAggregateId(AGGREGATE_TYPE, aggregateId)
            .map { it.version.toLong() }
            .defaultIfEmpty(0L)
    }

    /**
     * Checks if an event stream exists for an aggregate.
     */
    override fun streamExists(aggregateId: UUID): Mono<Boolean> {
        return eventStreamRepository.existsByAggregateTypeAndAggregateId(AGGREGATE_TYPE, aggregateId)
    }

    // Private helper methods

    private fun appendEventsUsingStoredProcedure(
        aggregateId: UUID,
        expectedVersion: Long,
        events: List<ProductEvent>,
        metadata: EventMetadata?
    ): Mono<Void> {
        // Build JSON objects for each event using Jackson (safe serialization)
        // In Spring Data R2DBC 4.0, passing array of Json objects directly doesn't work
        // So we build the array literal as a string and cast it in SQL
        val eventsJsonStrings = events.map { event ->
            buildEventJsonObject(event, metadata)
        }

        // Build PostgreSQL array literal: ARRAY['json1'::jsonb, 'json2'::jsonb, ...]
        // This approach avoids R2DBC parameter binding issues with Json arrays
        val eventsArrayLiteral = eventsJsonStrings.joinToString(
            separator = "::jsonb, ",
            prefix = "ARRAY[",
            postfix = "::jsonb]::jsonb[]"
        ) { jsonStr ->
            // Escape single quotes for PostgreSQL string literals
            "'" + jsonStr.replace("'", "''") + "'"
        }

        // Use parameterized query for scalar values, but embed the JSON array literal
        // The JSON content is safely escaped through Jackson serialization and SQL escaping
        val sql = """
            SELECT event_store.append_events(
                :aggregateType,
                :aggregateId,
                :expectedVersion,
                $eventsArrayLiteral
            )
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("aggregateType", AGGREGATE_TYPE)
            .bind("aggregateId", aggregateId)
            .bind("expectedVersion", expectedVersion.toInt())
            .fetch()
            .rowsUpdated()
            .then()
            .onErrorMap { error ->
                if (isSerializationFailure(error)) {
                    // Note: actualVersion is an approximation. The real version could be higher
                    // if multiple events were saved concurrently. Querying the actual version
                    // would require an additional database call in the error path.
                    EventStoreVersionConflictException(
                        aggregateType = AGGREGATE_TYPE,
                        aggregateId = aggregateId,
                        expectedVersion = expectedVersion,
                        actualVersion = expectedVersion + 1
                    )
                } else {
                    error
                }
            }
    }

    /**
     * Builds a JSON object for the event using Jackson ObjectMapper.
     * This is safe from injection as Jackson handles all escaping properly.
     */
    private fun buildEventJsonObject(event: ProductEvent, metadata: EventMetadata?): String {
        val eventData = eventSerializer.serialize(event)
        val eventType = eventSerializer.getEventTypeName(event)
        val eventVersion = eventSerializer.getEventVersion(event)
        val metadataJson = metadata?.let { eventSerializer.serializeMetadata(it) }

        // Use a map and Jackson for safe JSON construction
        val eventObject = buildMap {
            put("event_type", eventType)
            put("event_version", eventVersion)
            // Parse event_data back to object to avoid double-encoding
            put("event_data", objectMapper.readTree(eventData))

            if (metadataJson != null) {
                put("metadata", objectMapper.readTree(metadataJson))
            }
            metadata?.causationId?.let { put("causation_id", it.toString()) }
            metadata?.correlationId?.let { put("correlation_id", it.toString()) }
            metadata?.userId?.let { put("user_id", it) }
        }

        return objectMapper.writeValueAsString(eventObject)
    }

    private fun deserializeEvent(entity: DomainEventEntity): ProductEvent {
        return eventDeserializer.deserialize(
            eventType = entity.eventType,
            eventVersion = entity.eventVersion,
            json = entity.eventData
        )
    }

    /**
     * Checks if the error is a PostgreSQL serialization_failure.
     * This checks the SQL state code (40001) which is language-independent
     * and more robust than matching error message text.
     */
    private fun isSerializationFailure(error: Throwable): Boolean {
        // Check the error and its cause chain for R2dbcException with serialization_failure SQL state
        var current: Throwable? = error
        while (current != null) {
            if (current is R2dbcException && current.sqlState == SQLSTATE_SERIALIZATION_FAILURE) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
