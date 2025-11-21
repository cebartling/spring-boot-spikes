package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a shopping cart session.
 *
 * A shopping cart tracks items a customer intends to purchase, along with pricing,
 * totals, and state (active, abandoned, converted, expired).
 * All monetary amounts are stored in cents as Long integers.
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

    @Column("subtotal_cents")
    val subtotalCents: Long = 0,

    @Column("tax_amount_cents")
    val taxAmountCents: Long = 0,

    @Column("discount_amount_cents")
    val discountAmountCents: Long = 0,

    @Column("total_amount_cents")
    val totalAmountCents: Long = 0,

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
