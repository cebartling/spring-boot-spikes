package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents a line item in an order.
 *
 * Implements [Persistable] to correctly handle INSERT vs UPDATE with pre-generated UUIDs.
 */
@Table("order_items")
data class OrderItem(
    @Id
    @get:JvmName("id")
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
    val createdAt: Instant = Instant.now(),

    @Transient
    private val isNewEntity: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

    /**
     * Calculate the total price for this line item in cents.
     */
    fun lineTotalInCents(): Long = unitPriceInCents * quantity

    /**
     * Mark this entity as persisted.
     */
    fun asPersisted(): OrderItem = copy(isNewEntity = false)
}
