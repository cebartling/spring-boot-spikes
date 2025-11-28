package com.pintailconsultingllc.cqrsspike.product.query.projection

import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Status of a projection.
 */
enum class ProjectionState {
    STOPPED,
    STARTING,
    RUNNING,
    CATCHING_UP,
    REBUILDING,
    PAUSED,
    ERROR
}

/**
 * Current status of a projection including health metrics.
 */
data class ProjectionStatus(
    val projectionName: String,
    val state: ProjectionState,
    val lastEventId: UUID?,
    val lastEventSequence: Long?,
    val eventsProcessed: Long,
    val eventLag: Long,
    val lastProcessedAt: OffsetDateTime?,
    val lastError: String?,
    val lastErrorAt: OffsetDateTime?
)

/**
 * Result of a projection rebuild operation.
 */
data class RebuildResult(
    val projectionName: String,
    val eventsProcessed: Long,
    val duration: Duration,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Health status of a projection.
 */
data class ProjectionHealth(
    val projectionName: String,
    val healthy: Boolean,
    val eventLag: Long,
    val lastProcessedAt: OffsetDateTime?,
    val message: String
)
