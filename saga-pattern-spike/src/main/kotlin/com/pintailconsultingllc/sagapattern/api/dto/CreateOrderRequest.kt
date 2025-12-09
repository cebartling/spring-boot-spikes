package com.pintailconsultingllc.sagapattern.api.dto

import java.math.BigDecimal
import java.util.UUID

/**
 * Request DTO for creating a new order.
 */
data class CreateOrderRequest(
    /**
     * Customer placing the order.
     */
    val customerId: UUID,

    /**
     * Items to include in the order.
     */
    val items: List<OrderItemRequest>,

    /**
     * Payment method to use for this order.
     */
    val paymentMethodId: String,

    /**
     * Shipping address for delivery.
     */
    val shippingAddress: ShippingAddressRequest
) {
    /**
     * Calculate the total amount for this order.
     */
    fun calculateTotal(): BigDecimal = items.sumOf { it.calculateLineTotal() }
}

/**
 * Request DTO for an order item.
 */
data class OrderItemRequest(
    /**
     * Product being ordered.
     */
    val productId: UUID,

    /**
     * Product name for display.
     */
    val productName: String,

    /**
     * Quantity to order.
     */
    val quantity: Int,

    /**
     * Unit price for the product.
     */
    val unitPrice: BigDecimal
) {
    /**
     * Calculate the total for this line item.
     */
    fun calculateLineTotal(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))
}

/**
 * Request DTO for shipping address.
 */
data class ShippingAddressRequest(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)
