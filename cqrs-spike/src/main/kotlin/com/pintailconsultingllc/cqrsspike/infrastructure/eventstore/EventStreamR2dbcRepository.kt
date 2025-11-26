package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * R2DBC repository for event_store.event_stream table.
 */
@Repository
interface EventStreamR2dbcRepository : ReactiveCrudRepository<EventStreamEntity, UUID> {

    /**
     * Find event stream by aggregate type and ID.
     */
    @Query("""
        SELECT * FROM event_store.event_stream
        WHERE aggregate_type = :aggregateType
        AND aggregate_id = :aggregateId
    """)
    fun findByAggregateTypeAndAggregateId(
        aggregateType: String,
        aggregateId: UUID
    ): Mono<EventStreamEntity>

    /**
     * Check if stream exists for aggregate.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM event_store.event_stream
            WHERE aggregate_type = :aggregateType
            AND aggregate_id = :aggregateId
        )
    """)
    fun existsByAggregateTypeAndAggregateId(
        aggregateType: String,
        aggregateId: UUID
    ): Mono<Boolean>
}
