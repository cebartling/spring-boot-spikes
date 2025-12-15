package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.history.OrderEvent
import com.pintailconsultingllc.sagapattern.history.OrderEventType
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Repository for order event persistence.
 *
 * Manages the event history for orders and saga executions. Provides an
 * audit trail of all significant events during order processing, including
 * step executions, failures, and compensation activities.
 *
 * ## Naming Conventions
 *
 * This repository follows consistent naming patterns for method types:
 *
 * - **`findXxx` methods**: Query for existing records by various criteria.
 *   Returns nullable results for single-record queries or lists for multi-record queries.
 *   Events are typically ordered by timestamp for chronological history.
 *
 * - **`countXxx` methods**: Count records matching specific criteria.
 *   Used for event statistics and monitoring.
 *
 * - **`deleteXxx` methods**: Remove records matching specific criteria.
 *   Returns the count of deleted records.
 *
 * - **`save`** (inherited): Handles insert/update based on entity state.
 *   New entities (null ID) are inserted; existing entities are updated.
 *
 * ## Event Types
 *
 * Events capture the full lifecycle of order processing:
 * - Order creation and status changes
 * - Step execution start, success, and failure
 * - Compensation initiation and completion
 * - Retry attempts and outcomes
 *
 * @see OrderEvent
 * @see OrderEventType
 * @see OrderRepository
 */
@Repository
interface OrderEventRepository : CoroutineCrudRepository<OrderEvent, UUID> {

    /**
     * Find all events for an order, ordered by timestamp.
     */
    suspend fun findByOrderIdOrderByTimestampAsc(orderId: UUID): List<OrderEvent>

    /**
     * Find events for an order since a specific time.
     */
    suspend fun findByOrderIdAndTimestampAfterOrderByTimestampAsc(
        orderId: UUID,
        after: Instant
    ): List<OrderEvent>

    /**
     * Find events for an order by event type.
     */
    suspend fun findByOrderIdAndEventTypeOrderByTimestampAsc(
        orderId: UUID,
        eventType: OrderEventType
    ): List<OrderEvent>

    /**
     * Find events for a saga execution.
     */
    suspend fun findBySagaExecutionIdOrderByTimestampAsc(sagaExecutionId: UUID): List<OrderEvent>

    /**
     * Find events for an order with a specific step name.
     */
    suspend fun findByOrderIdAndStepNameOrderByTimestampAsc(
        orderId: UUID,
        stepName: String
    ): List<OrderEvent>

    /**
     * Count events for an order.
     */
    suspend fun countByOrderId(orderId: UUID): Long

    /**
     * Count events of a specific type for an order.
     */
    suspend fun countByOrderIdAndEventType(orderId: UUID, eventType: OrderEventType): Long

    /**
     * Find the most recent event for an order.
     */
    @Query("SELECT * FROM order_events WHERE order_id = :orderId ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestByOrderId(orderId: UUID): OrderEvent?

    /**
     * Find events for orders by customer (via join with orders table).
     */
    @Query("""
        SELECT oe.* FROM order_events oe
        JOIN orders o ON oe.order_id = o.id
        WHERE o.customer_id = :customerId
        ORDER BY oe.timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun findByCustomerId(customerId: UUID, limit: Int, offset: Int): List<OrderEvent>

    /**
     * Delete all events for an order.
     */
    suspend fun deleteByOrderId(orderId: UUID): Long
}
