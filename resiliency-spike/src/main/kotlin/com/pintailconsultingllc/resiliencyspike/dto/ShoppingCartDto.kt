package com.pintailconsultingllc.resiliencyspike.dto

import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import java.time.OffsetDateTime
import java.util.*

/**
 * DTO for shopping cart responses
 */
data class ShoppingCartResponse(
    val id: Long,
    val cartUuid: UUID,
    val userId: String?,
    val sessionId: String,
    val status: CartStatus,
    val currencyCode: String,
    val subtotalCents: Long,
    val taxAmountCents: Long,
    val discountAmountCents: Long,
    val totalAmountCents: Long,
    val itemCount: Int,
    val metadata: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime?,
    val convertedAt: OffsetDateTime?,
    val items: List<CartItemResponse>? = null
)

/**
 * Request to create a new shopping cart
 */
data class CreateCartRequest(
    val sessionId: String,
    val userId: String? = null,
    val expiresAt: OffsetDateTime? = null
)

/**
 * Request to associate a cart with a user
 */
data class AssociateCartWithUserRequest(
    val userId: String
)

/**
 * Request to update cart expiration
 */
data class UpdateCartExpirationRequest(
    val expiresAt: OffsetDateTime
)

/**
 * Response for cart statistics
 */
data class CartStatisticsResponse(
    val totalCarts: Long,
    val activeCarts: Long,
    val abandonedCarts: Long,
    val convertedCarts: Long,
    val expiredCarts: Long
)
