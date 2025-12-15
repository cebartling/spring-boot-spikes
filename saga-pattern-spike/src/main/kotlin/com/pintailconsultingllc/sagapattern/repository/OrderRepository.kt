package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for order persistence.
 *
 * Manages order records including status tracking and customer associations.
 * Orders are the primary aggregate root for saga orchestration.
 *
 * ## Naming Conventions
 *
 * This repository follows consistent naming patterns for method types:
 *
 * - **`updateXxx` methods**: Update specific fields on existing orders.
 *   Used for status transitions (e.g., [updateStatus]).
 *
 * - **`findXxx` methods**: Query for existing records by various criteria.
 *   Returns nullable results for single-record queries or lists for multi-record queries.
 *
 * - **`existsXxx` methods**: Check for record existence without loading full entity.
 *   Returns boolean for efficient existence checks.
 *
 * - **`save`** (inherited): Handles insert/update based on entity state.
 *   New entities (null ID) are inserted; existing entities are updated.
 *
 * ## Order Status Lifecycle
 *
 * Orders follow this status progression:
 * ```
 * PENDING → PROCESSING → COMPLETED
 *                      ↘ FAILED → COMPENSATED
 * ```
 *
 * @see Order
 * @see OrderStatus
 * @see OrderItemRepository
 */
@Repository
interface OrderRepository : CoroutineCrudRepository<Order, UUID> {

    /**
     * Find all orders for a specific customer.
     */
    suspend fun findByCustomerId(customerId: UUID): List<Order>

    /**
     * Find all orders with a specific status.
     */
    suspend fun findByStatus(status: OrderStatus): List<Order>

    /**
     * Find orders by customer ID and status.
     */
    suspend fun findByCustomerIdAndStatus(customerId: UUID, status: OrderStatus): List<Order>

    /**
     * Update order status.
     */
    @Modifying
    @Query("UPDATE orders SET status = :status, updated_at = NOW() WHERE id = :orderId")
    suspend fun updateStatus(orderId: UUID, status: OrderStatus): Int

    /**
     * Check if an order exists for the given ID and customer.
     */
    suspend fun existsByIdAndCustomerId(id: UUID, customerId: UUID): Boolean
}
