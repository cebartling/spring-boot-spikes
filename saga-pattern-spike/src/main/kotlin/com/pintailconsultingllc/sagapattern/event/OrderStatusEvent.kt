package com.pintailconsultingllc.sagapattern.event

import java.time.Instant
import java.util.UUID

/**
 * Event representing a change in order status during saga execution.
 *
 * Used for real-time status updates via Server-Sent Events (SSE).
 */
data class OrderStatusEvent(
    /**
     * The order ID this event relates to.
     */
    val orderId: UUID,

    /**
     * Type of status change event.
     */
    val eventType: StatusEventType,

    /**
     * Name of the step affected (null for saga-level events).
     */
    val stepName: String?,

    /**
     * The new status value.
     */
    val newStatus: String,

    /**
     * When the event occurred.
     */
    val timestamp: Instant = Instant.now(),

    /**
     * Additional event-specific details.
     */
    val details: Map<String, Any>? = null
)

/**
 * Types of order status change events.
 */
enum class StatusEventType {
    /**
     * Saga execution has started.
     */
    SAGA_STARTED,

    /**
     * A saga step has started executing.
     */
    STEP_STARTED,

    /**
     * A saga step completed successfully.
     */
    STEP_COMPLETED,

    /**
     * A saga step failed.
     */
    STEP_FAILED,

    /**
     * Compensation (rollback) has started.
     */
    COMPENSATION_STARTED,

    /**
     * A step has been compensated (rolled back).
     */
    STEP_COMPENSATED,

    /**
     * Saga completed successfully.
     */
    SAGA_COMPLETED,

    /**
     * Saga failed and compensation completed.
     */
    SAGA_FAILED
}
