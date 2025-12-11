package com.pintailconsultingllc.sagapattern.history

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import java.time.Instant
import java.util.UUID

/**
 * Represents the complete timeline of an order.
 */
data class OrderTimeline(
    /** Order identifier. */
    val orderId: UUID,

    /** Order reference number (for display). */
    val orderNumber: String,

    /** When the order was created. */
    val createdAt: Instant,

    /** Current order status. */
    val currentStatus: OrderStatus,

    /** Timeline entries ordered chronologically. */
    val entries: List<TimelineEntry>,

    /** Total number of saga executions (including retries). */
    val executionCount: Int = 1
) {
    companion object {
        /**
         * Create an empty timeline for an order.
         */
        fun empty(
            orderId: UUID,
            orderNumber: String,
            createdAt: Instant,
            currentStatus: OrderStatus
        ): OrderTimeline = OrderTimeline(
            orderId = orderId,
            orderNumber = orderNumber,
            createdAt = createdAt,
            currentStatus = currentStatus,
            entries = emptyList()
        )
    }

    /**
     * Check if the timeline contains any failure entries.
     */
    fun hasFailures(): Boolean = entries.any { it.status == TimelineStatus.FAILED }

    /**
     * Check if the timeline contains any compensated entries.
     */
    fun hasCompensations(): Boolean = entries.any { it.status == TimelineStatus.COMPENSATED }

    /**
     * Get the most recent entry.
     */
    fun latestEntry(): TimelineEntry? = entries.lastOrNull()

    /**
     * Get entries for a specific step.
     */
    fun entriesForStep(stepName: String): List<TimelineEntry> =
        entries.filter { it.stepName == stepName }
}
