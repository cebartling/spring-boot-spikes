package com.pintailconsultingllc.sagapattern.domain

/**
 * Shipping address details for order fulfillment.
 *
 * Represents the physical address where an order should be delivered.
 */
data class ShippingAddress(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)
