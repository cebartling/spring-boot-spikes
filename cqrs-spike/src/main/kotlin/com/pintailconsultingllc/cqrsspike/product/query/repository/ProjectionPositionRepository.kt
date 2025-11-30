package com.pintailconsultingllc.cqrsspike.product.query.repository

import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Entity for tracking projection processing position.
 */
@Table(name = "projection_position", schema = "read_model")
data class ProjectionPosition(
    @Id
    @Column("projection_name")
    val projectionName: String,

    @Column("last_event_id")
    val lastEventId: UUID?,

    @Column("last_event_sequence")
    val lastEventSequence: Long?,

    @Column("last_processed_at")
    val lastProcessedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("events_processed")
    val eventsProcessed: Long = 0
)

/**
 * Repository for projection position tracking.
 */
@Repository
interface ProjectionPositionRepository : ReactiveCrudRepository<ProjectionPosition, String> {

    /**
     * Get position for a specific projection.
     */
    fun findByProjectionName(projectionName: String): Mono<ProjectionPosition>

    /**
     * Update projection position atomically.
     */
    @Query("""
        INSERT INTO read_model.projection_position (
            projection_name, last_event_id, last_event_sequence,
            last_processed_at, events_processed
        ) VALUES (
            :projectionName, :lastEventId, :lastEventSequence,
            NOW(), :eventsProcessed
        )
        ON CONFLICT (projection_name) DO UPDATE SET
            last_event_id = EXCLUDED.last_event_id,
            last_event_sequence = EXCLUDED.last_event_sequence,
            last_processed_at = NOW(),
            events_processed = read_model.projection_position.events_processed + 1
        RETURNING *
    """)
    fun upsertPosition(
        projectionName: String,
        lastEventId: UUID,
        lastEventSequence: Long,
        eventsProcessed: Long
    ): Mono<ProjectionPosition>
}
