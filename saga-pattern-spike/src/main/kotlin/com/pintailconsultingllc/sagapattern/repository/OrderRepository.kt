package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for Order entities using R2DBC.
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
