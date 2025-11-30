package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * R2DBC entity for event_store.event_stream table.
 * Represents an aggregate's event stream.
 */
@Table("event_store\".\"event_stream")
data class EventStreamEntity(
    @Id
    @Column("stream_id")
    val streamId: UUID,

    @Column("aggregate_type")
    val aggregateType: String,

    @Column("aggregate_id")
    val aggregateId: UUID,

    @Column("version")
    val version: Int,

    @Column("created_at")
    val createdAt: OffsetDateTime,

    @Column("updated_at")
    val updatedAt: OffsetDateTime
)
