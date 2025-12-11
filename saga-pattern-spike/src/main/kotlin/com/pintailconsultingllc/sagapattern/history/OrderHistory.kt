package com.pintailconsultingllc.sagapattern.history

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import java.time.Instant
import java.util.UUID

/**
 * Comprehensive order history including all saga executions and events.
 */
data class OrderHistory(
    /** Order identifier. */
    val orderId: UUID,

    /** Order reference number (for display). */
    val orderNumber: String,

    /** When the order was created. */
    val createdAt: Instant,

    /** When order processing completed (null if still processing). */
    val completedAt: Instant? = null,

    /** Final order status. */
    val finalStatus: OrderStatus,

    /** Timeline of all events. */
    val timeline: OrderTimeline,

    /** Summary of all saga execution attempts. */
    val executions: List<SagaExecutionSummary> = emptyList()
) {
    companion object {
        /**
         * Generate an order number from order ID and creation time.
         */
        fun generateOrderNumber(orderId: UUID, createdAt: Instant): String {
            val year = createdAt.toString().substring(0, 4)
            val shortId = orderId.toString().substring(0, 8).uppercase()
            return "ORD-$year-$shortId"
        }

        /**
         * Create a basic order history.
         */
        fun create(
            orderId: UUID,
            createdAt: Instant,
            finalStatus: OrderStatus,
            completedAt: Instant? = null,
            timeline: OrderTimeline,
            executions: List<SagaExecutionSummary> = emptyList()
        ): OrderHistory = OrderHistory(
            orderId = orderId,
            orderNumber = generateOrderNumber(orderId, createdAt),
            createdAt = createdAt,
            completedAt = completedAt,
            finalStatus = finalStatus,
            timeline = timeline,
            executions = executions
        )
    }

    /**
     * Total number of execution attempts.
     */
    val totalAttempts: Int
        get() = executions.size

    /**
     * Number of retry attempts (excluding original).
     */
    val retryCount: Int
        get() = executions.count { it.isRetry }

    /**
     * Whether the order was ultimately successful.
     */
    val wasSuccessful: Boolean
        get() = finalStatus == OrderStatus.COMPLETED

    /**
     * Whether any executions required compensation.
     */
    val hadCompensations: Boolean
        get() = executions.any { it.outcome == SagaOutcome.COMPENSATED }
}
