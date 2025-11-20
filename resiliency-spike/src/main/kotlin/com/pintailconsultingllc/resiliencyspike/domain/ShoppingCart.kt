package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a shopping cart session.
 *
 * A shopping cart tracks items a customer intends to purchase, along with pricing,
 * totals, and state (active, abandoned, converted, expired).
 */
@Table("shopping_carts")
data class ShoppingCart(
    @Id
    val id: Long? = null,

    @Column("cart_uuid")
    val cartUuid: UUID = UUID.randomUUID(),

    @Column("user_id")
    val userId: String? = null,  // Optional: for guest vs authenticated users

    @Column("session_id")
    val sessionId: String,

    @Column("status")
    val status: CartStatus = CartStatus.ACTIVE,

    @Column("currency_code")
    val currencyCode: String = "USD",

    @Column("subtotal")
    val subtotal: BigDecimal = BigDecimal.ZERO,

    @Column("tax_amount")
    val taxAmount: BigDecimal = BigDecimal.ZERO,

    @Column("discount_amount")
    val discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column("total_amount")
    val totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column("item_count")
    val itemCount: Int = 0,

    @Column("metadata")
    val metadata: String? = null,  // JSON stored as String (promo codes, gift messages, etc.)

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("expires_at")
    val expiresAt: OffsetDateTime? = null,

    @Column("converted_at")
    val convertedAt: OffsetDateTime? = null
)

/**
 * Shopping cart status enumeration.
 */
enum class CartStatus {
    ACTIVE,      // Cart is currently being used
    ABANDONED,   // Cart has been inactive for extended period
    CONVERTED,   // Cart was successfully converted to an order
    EXPIRED      // Cart has passed its expiration time
}
