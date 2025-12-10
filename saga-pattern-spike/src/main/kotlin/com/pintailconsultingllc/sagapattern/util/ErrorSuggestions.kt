package com.pintailconsultingllc.sagapattern.util

/**
 * Utility for generating customer-facing error suggestions.
 *
 * Provides actionable suggestions based on error codes to help
 * customers resolve issues with their orders.
 */
object ErrorSuggestions {
    /**
     * Generate suggestions based on the error code.
     *
     * @param errorCode The error code from a failed operation
     * @return List of actionable suggestions for the customer
     */
    fun suggestionsForError(errorCode: String?): List<String> = when (errorCode) {
        "INVENTORY_UNAVAILABLE" -> listOf(
            "Check product availability",
            "Try reducing the quantity",
            "Add items to wishlist for notifications"
        )
        "PAYMENT_DECLINED" -> listOf(
            "Update your payment method",
            "Try a different card",
            "Contact your bank for authorization"
        )
        "FRAUD_DETECTED" -> listOf(
            "Contact customer support",
            "Verify your account information"
        )
        "INVALID_ADDRESS" -> listOf(
            "Verify your shipping address",
            "Check postal code is correct",
            "Try an alternate delivery address"
        )
        "SHIPPING_UNAVAILABLE" -> listOf(
            "Select a different shipping address",
            "Contact support for shipping options"
        )
        else -> listOf(
            "Please try again",
            "Contact customer support if the issue persists"
        )
    }
}
