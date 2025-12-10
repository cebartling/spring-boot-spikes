package com.pintailconsultingllc.sagapattern.retry

import java.util.UUID

/**
 * Request to retry a failed order.
 *
 * Contains any updated information needed for the retry,
 * such as a new payment method or corrected shipping address.
 */
data class RetryRequest(
    /**
     * ID of the order to retry.
     */
    val orderId: UUID,

    /**
     * Updated payment method ID (if payment failed).
     */
    val updatedPaymentMethodId: String? = null,

    /**
     * Updated shipping address (if shipping failed).
     */
    val updatedShippingAddress: ShippingAddress? = null,

    /**
     * List of changes the customer has acknowledged.
     * Used for price changes or availability changes.
     */
    val acknowledgedChanges: List<String> = emptyList()
)

/**
 * Shipping address for retry requests.
 */
data class ShippingAddress(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)
