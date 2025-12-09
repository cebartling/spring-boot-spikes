package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.OrderItem
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for OrderItem entities using R2DBC.
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
