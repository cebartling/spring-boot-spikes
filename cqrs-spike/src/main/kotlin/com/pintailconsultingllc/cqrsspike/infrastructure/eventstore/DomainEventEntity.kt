package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * R2DBC entity for event_store.domain_event table.
 * Represents a single domain event.
 */
@Table("event_store.domain_event")
data class DomainEventEntity(
    @Id
    @Column("event_id")
    val eventId: UUID,

    @Column("stream_id")
    val streamId: UUID,

    @Column("event_type")
    val eventType: String,

    @Column("event_version")
    val eventVersion: Int,

    @Column("aggregate_version")
    val aggregateVersion: Int,

    @Column("event_data")
    val eventData: String,

    @Column("metadata")
    val metadata: String?,

    @Column("occurred_at")
    val occurredAt: OffsetDateTime,

    @Column("causation_id")
    val causationId: UUID?,

    @Column("correlation_id")
    val correlationId: UUID?,

    @Column("user_id")
    val userId: String?
)
