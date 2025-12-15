package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.OrderItem
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for order item persistence.
 *
 * Manages order line items that belong to orders. Each order can have
 * multiple items representing products being purchased.
 *
 * ## Naming Conventions
 *
 * This repository follows consistent naming patterns for method types:
 *
 * - **`findXxx` methods**: Query for existing records by various criteria.
 *   Returns lists of items for the specified order.
 *
 * - **`deleteXxx` methods**: Remove records matching specific criteria.
 *   Returns the count of deleted records.
 *
 * - **`save`** (inherited): Handles insert/update based on entity state.
 *   New entities (null ID) are inserted; existing entities are updated.
 *
 * @see OrderItem
 * @see OrderRepository
 */
@Repository
interface OrderItemRepository : CoroutineCrudRepository<OrderItem, UUID> {

    /**
     * Find all items for a specific order.
     */
    suspend fun findByOrderId(orderId: UUID): List<OrderItem>

    /**
     * Delete all items for a specific order.
     */
    suspend fun deleteByOrderId(orderId: UUID): Long
}
