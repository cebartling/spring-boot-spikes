package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents a customer order.
 */
@Table("orders")
data class Order(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column("customer_id")
    val customerId: UUID,

    /**
     * Total amount in cents.
     */
    @Column("total_amount")
    val totalAmountCents: Long,

    val status: OrderStatus = OrderStatus.PENDING,

    @Column("created_at")
    val createdAt: Instant = Instant.now(),

    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),

    /**
     * Order items associated with this order.
     * Marked as transient since R2DBC doesn't support embedded collections.
     * Items must be loaded separately via the repository.
     */
    @Transient
    val items: List<OrderItem> = emptyList()
) {
    /**
     * Create a copy of this order with updated status.
     */
    fun withStatus(newStatus: OrderStatus): Order = copy(
        status = newStatus,
        updatedAt = Instant.now()
    )

    /**
     * Create a copy of this order with items loaded.
     */
    fun withItems(items: List<OrderItem>): Order = copy(items = items)
}
