package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import java.util.UUID

/**
 * Metadata attached to domain events for tracing and auditing.
 */
data class EventMetadata(
    val causationId: UUID? = null,
    val correlationId: UUID? = null,
    val userId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val additionalData: Map<String, Any?> = emptyMap()
)
