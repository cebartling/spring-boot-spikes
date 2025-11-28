package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

/**
 * R2DBC repository for event_store.domain_event table.
 */
@Repository
interface DomainEventR2dbcRepository : ReactiveCrudRepository<DomainEventEntity, UUID> {

    /**
     * Find all events for a stream, ordered by aggregate version.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        WHERE stream_id = :streamId
        ORDER BY aggregate_version ASC
    """)
    fun findByStreamIdOrderByAggregateVersion(streamId: UUID): Flux<DomainEventEntity>

    /**
     * Find events for a stream starting from a specific version.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        WHERE stream_id = :streamId
        AND aggregate_version > :fromVersion
        ORDER BY aggregate_version ASC
    """)
    fun findByStreamIdAndVersionGreaterThan(
        streamId: UUID,
        fromVersion: Int
    ): Flux<DomainEventEntity>

    /**
     * Find events by type within a time range.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        WHERE event_type = :eventType
        AND occurred_at BETWEEN :startTime AND :endTime
        ORDER BY occurred_at ASC
    """)
    fun findByEventTypeAndTimeRange(
        eventType: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): Flux<DomainEventEntity>

    /**
     * Find events by correlation ID.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        WHERE correlation_id = :correlationId
        ORDER BY occurred_at ASC
    """)
    fun findByCorrelationId(correlationId: UUID): Flux<DomainEventEntity>

    /**
     * Count events for a stream.
     */
    @Query("""
        SELECT COUNT(*) FROM event_store.domain_event
        WHERE stream_id = :streamId
    """)
    fun countByStreamId(streamId: UUID): Mono<Long>

    /**
     * Find the latest event for a stream.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        WHERE stream_id = :streamId
        ORDER BY aggregate_version DESC
        LIMIT 1
    """)
    fun findLatestByStreamId(streamId: UUID): Mono<DomainEventEntity>

    // Projection support queries

    /**
     * Find all events after a specific event ID for incremental processing.
     * Events are ordered by their occurrence time and event ID for consistent projection.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        WHERE (occurred_at, event_id) > (
            SELECT occurred_at, event_id FROM event_store.domain_event WHERE event_id = :afterEventId
        )
        ORDER BY occurred_at ASC, event_id ASC
        LIMIT :limit
    """)
    fun findEventsAfterEventId(
        afterEventId: UUID,
        limit: Int
    ): Flux<DomainEventEntity>

    /**
     * Find all events ordered by occurrence for full rebuild.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        ORDER BY occurred_at ASC, event_id ASC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllEventsOrdered(
        limit: Int,
        offset: Long
    ): Flux<DomainEventEntity>

    /**
     * Get the most recent event for determining current position.
     */
    @Query("""
        SELECT * FROM event_store.domain_event
        ORDER BY occurred_at DESC, event_id DESC
        LIMIT 1
    """)
    fun findLatestEvent(): Mono<DomainEventEntity>

    /**
     * Count events after a specific event ID for lag calculation.
     */
    @Query("""
        SELECT COUNT(*) FROM event_store.domain_event
        WHERE (occurred_at, event_id) > (
            SELECT occurred_at, event_id FROM event_store.domain_event WHERE event_id = :afterEventId
        )
    """)
    fun countEventsAfterEventId(afterEventId: UUID): Mono<Long>

    /**
     * Count all events in the store.
     */
    @Query("SELECT COUNT(*) FROM event_store.domain_event")
    fun countAllEvents(): Mono<Long>
}
