package com.pintailconsultingllc.resiliencyspike.dto

import java.time.OffsetDateTime
import java.util.*

/**
 * DTO for cart item responses
 */
data class CartItemResponse(
    val id: Long,
    val cartId: Long,
    val productId: UUID,
    val sku: String,
    val productName: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val lineTotalCents: Long,
    val discountAmountCents: Long,
    val metadata: String?,
    val addedAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

/**
 * Request to add an item to cart
 */
data class AddItemToCartRequest(
    val productId: UUID,
    val quantity: Int
)

/**
 * Request to update item quantity
 */
data class UpdateItemQuantityRequest(
    val quantity: Int
)

/**
 * Request to apply discount to an item
 */
data class ApplyItemDiscountRequest(
    val discountAmountCents: Long
)

/**
 * Request to update item metadata
 */
data class UpdateItemMetadataRequest(
    val metadata: String
)

/**
 * Response for cart totals
 */
data class CartTotalsResponse(
    val subtotalCents: Long,
    val taxAmountCents: Long,
    val discountAmountCents: Long,
    val totalAmountCents: Long,
    val itemCount: Int
)

/**
 * Response for item availability validation
 */
data class ItemAvailabilityResponse(
    val productId: UUID,
    val available: Boolean,
    val reason: String? = null
)

/**
 * Response for cart validation
 */
data class CartValidationResponse(
    val cartId: Long,
    val items: List<ItemAvailabilityResponse>,
    val allItemsValid: Boolean
)
