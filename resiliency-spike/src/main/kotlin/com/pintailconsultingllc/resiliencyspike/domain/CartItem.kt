package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing an individual item in a shopping cart.
 *
 * Cart items store snapshots of product information (SKU, name, price) at the time
 * they were added to the cart, ensuring price consistency even if product prices change.
 * Prices are stored in cents as Long integers.
 *
 * Note: The `product` field is transient and not persisted. It can be loaded separately
 * via custom repository methods when needed.
 */
@Table("cart_items")
data class CartItem(
    @Id
    val id: Long? = null,

    @Column("cart_id")
    val cartId: Long,

    @Column("product_id")
    val productId: UUID,

    @Column("sku")
    val sku: String,  // Snapshot at time of add

    @Column("product_name")
    val productName: String,  // Snapshot at time of add

    @Column("quantity")
    val quantity: Int,

    @Column("unit_price_cents")
    val unitPriceCents: Long,  // Price in cents snapshot at time of add

    @Column("line_total_cents")
    val lineTotalCents: Long,  // quantity * unitPriceCents (calculated by DB trigger)

    @Column("discount_amount_cents")
    val discountAmountCents: Long = 0,

    @Column("metadata")
    val metadata: String? = null,  // JSON stored as String (product options, customizations, etc.)

    @Column("added_at")
    val addedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    // Transient relationship - not stored in database, loaded separately
    @Transient
    val product: Product? = null
)
