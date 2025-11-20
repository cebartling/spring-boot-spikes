package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Entity representing an individual item in a shopping cart.
 *
 * Cart items store snapshots of product information (SKU, name, price) at the time
 * they were added to the cart, ensuring price consistency even if product prices change.
 */
@Table("cart_items")
data class CartItem(
    @Id
    val id: Long? = null,

    @Column("cart_id")
    val cartId: Long,

    @Column("product_id")
    val productId: Long,

    @Column("sku")
    val sku: String,  // Snapshot at time of add

    @Column("product_name")
    val productName: String,  // Snapshot at time of add

    @Column("quantity")
    val quantity: Int,

    @Column("unit_price")
    val unitPrice: BigDecimal,  // Price snapshot at time of add

    @Column("line_total")
    val lineTotal: BigDecimal,  // quantity * unit_price (calculated by DB trigger)

    @Column("discount_amount")
    val discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column("metadata")
    val metadata: String? = null,  // JSON stored as String (product options, customizations, etc.)

    @Column("added_at")
    val addedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
