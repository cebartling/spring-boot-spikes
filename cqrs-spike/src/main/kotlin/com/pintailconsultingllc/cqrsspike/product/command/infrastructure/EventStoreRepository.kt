package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.EventMetadata
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Interface for Event Store Repository.
 */
interface EventStoreRepository {
    /**
     * Saves events to the event store without metadata.
     */
    fun saveEvents(events: List<ProductEvent>): Mono<Void>

    /**
     * Saves events to the event store with metadata for tracing and auditing.
     *
     * @param events List of events to save
     * @param metadata Optional metadata including causationId, correlationId, and userId
     */
    fun saveEvents(events: List<ProductEvent>, metadata: EventMetadata?): Mono<Void>

    /**
     * Finds all events for a given aggregate ID.
     */
    fun findEventsByAggregateId(aggregateId: UUID): Flux<ProductEvent>
}

/**
 * Extended interface with additional query capabilities.
 */
interface ExtendedEventStoreRepository : EventStoreRepository {
    /**
     * Finds events starting from a specific version.
     */
    fun findEventsByAggregateIdFromVersion(
        aggregateId: UUID,
        fromVersion: Long
    ): Flux<ProductEvent>

    /**
     * Finds events by type within a time range.
     */
    fun findEventsByTypeAndTimeRange(
        eventType: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): Flux<ProductEvent>

    /**
     * Finds events by correlation ID.
     */
    fun findEventsByCorrelationId(correlationId: UUID): Flux<ProductEvent>

    /**
     * Gets the current version of an aggregate's event stream.
     */
    fun getStreamVersion(aggregateId: UUID): Mono<Long>

    /**
     * Checks if an event stream exists.
     */
    fun streamExists(aggregateId: UUID): Mono<Boolean>
}
