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
 * Represents a customer order.
 *
 * Implements [Persistable] to correctly handle INSERT vs UPDATE with pre-generated UUIDs.
 * Spring Data R2DBC determines if an entity is new by checking if the @Id is null,
 * but we generate UUIDs upfront. The [isNew] flag explicitly tells Spring Data
 * whether to INSERT or UPDATE.
 */
@Table("orders")
data class Order @PersistenceCreator constructor(
    @Id
    @get:JvmName("id")
    val id: UUID = UUID.randomUUID(),

    @Column("customer_id")
    val customerId: UUID,

    /**
     * Total amount in cents.
     */
    @Column("total_amount_in_cents")
    val totalAmountInCents: Long,

    val status: OrderStatus = OrderStatus.PENDING,

    @Column("created_at")
    val createdAt: Instant = Instant.now(),

    @Column("updated_at")
    val updatedAt: Instant = Instant.now()
) : Persistable<UUID> {

    /**
     * Order items associated with this order.
     * Marked as transient since R2DBC doesn't support embedded collections.
     * Items must be loaded separately via the repository.
     */
    @Transient
    var items: List<OrderItem> = emptyList()

    @Transient
    private var isNewEntity: Boolean = false

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

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
    fun withItems(items: List<OrderItem>): Order = this.also { this.items = items }

    /**
     * Mark this entity as persisted (for use after save operations).
     */
    fun asPersisted(): Order = this.also { isNewEntity = false }

    companion object {
        /**
         * Create a new Order instance.
         */
        fun create(
            customerId: UUID,
            totalAmountInCents: Long,
            status: OrderStatus = OrderStatus.PENDING
        ): Order = Order(
            id = UUID.randomUUID(),
            customerId = customerId,
            totalAmountInCents = totalAmountInCents,
            status = status
        ).apply { isNewEntity = true }

        /**
         * Create an Order for testing (pre-generates UUID).
         */
        fun forTest(
            id: UUID = UUID.randomUUID(),
            customerId: UUID,
            totalAmountInCents: Long,
            status: OrderStatus = OrderStatus.PENDING,
            items: List<OrderItem> = emptyList()
        ): Order = Order(
            id = id,
            customerId = customerId,
            totalAmountInCents = totalAmountInCents,
            status = status
        ).apply {
            isNewEntity = true
            this.items = items
        }
    }
}
