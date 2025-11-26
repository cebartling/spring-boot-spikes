package com.pintailconsultingllc.cqrsspike.product.event

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Marker interface for all Product domain events.
 * All events are immutable and capture a specific state change.
 */
sealed interface ProductEvent {
    /** Unique identifier for this event */
    val eventId: UUID

    /** The aggregate (product) this event belongs to */
    val productId: UUID

    /** When this event occurred */
    val occurredAt: OffsetDateTime

    /** Version of the aggregate after this event */
    val version: Long
}
