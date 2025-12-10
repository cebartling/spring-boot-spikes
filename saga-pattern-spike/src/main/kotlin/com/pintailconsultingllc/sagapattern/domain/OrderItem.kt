package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents a line item in an order.
 */
@Table("order_items")
data class OrderItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column("order_id")
    val orderId: UUID,

    @Column("product_id")
    val productId: UUID,

    @Column("product_name")
    val productName: String,

    val quantity: Int,

    /**
     * Unit price in cents.
     */
    @Column("unit_price_in_cents")
    val unitPriceInCents: Long,

    @Column("created_at")
    val createdAt: Instant = Instant.now()
) {
    /**
     * Calculate the total price for this line item in cents.
     */
    fun lineTotalInCents(): Long = unitPriceInCents * quantity
}
