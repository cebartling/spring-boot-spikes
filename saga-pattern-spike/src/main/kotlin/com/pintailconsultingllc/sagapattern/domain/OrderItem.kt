package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
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
data class OrderItem @PersistenceCreator constructor(
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
    val createdAt: Instant = Instant.now()
) : Persistable<UUID> {

    @Transient
    private var isNewEntity: Boolean = false

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

    /**
     * Calculate the total price for this line item in cents.
     */
    fun lineTotalInCents(): Long = unitPriceInCents * quantity

    /**
     * Mark this entity as persisted.
     */
    fun asPersisted(): OrderItem = this.also { isNewEntity = false }

    companion object {
        /**
         * Create a new OrderItem instance.
         */
        fun create(
            orderId: UUID,
            productId: UUID,
            productName: String,
            quantity: Int,
            unitPriceInCents: Long
        ): OrderItem = OrderItem(
            id = UUID.randomUUID(),
            orderId = orderId,
            productId = productId,
            productName = productName,
            quantity = quantity,
            unitPriceInCents = unitPriceInCents
        ).apply { isNewEntity = true }
    }
}
