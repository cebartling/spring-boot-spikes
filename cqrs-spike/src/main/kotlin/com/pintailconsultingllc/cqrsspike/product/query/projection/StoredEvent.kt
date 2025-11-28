package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Represents an event as stored in the event store with its metadata.
 * Used by projection infrastructure to process events in order.
 */
data class StoredEvent(
    val eventId: UUID,
    val streamId: UUID,
    val eventType: String,
    val aggregateVersion: Long,
    val globalSequence: Long?,
    val occurredAt: OffsetDateTime,
    val event: ProductEvent
)
