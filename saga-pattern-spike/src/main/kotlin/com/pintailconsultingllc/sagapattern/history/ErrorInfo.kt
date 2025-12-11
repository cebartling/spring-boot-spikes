package com.pintailconsultingllc.sagapattern.history

/**
 * Contains error details for failed events.
 */
data class ErrorInfo(
    /** Error code for programmatic handling. */
    val code: String,

    /** User-friendly error message. */
    val message: String,

    /** Technical error details for debugging. */
    val technicalDetails: String? = null,

    /** Whether this error can be retried. */
    val recoverable: Boolean = false,

    /** Suggested action for the customer. */
    val suggestedAction: String? = null
) {
    companion object {
        /**
         * Create an ErrorInfo from a step failure.
         */
        fun fromStepFailure(
            errorCode: String?,
            errorMessage: String?,
            recoverable: Boolean = true
        ): ErrorInfo = ErrorInfo(
            code = errorCode ?: "UNKNOWN_ERROR",
            message = errorMessage ?: "An unexpected error occurred",
            recoverable = recoverable,
            suggestedAction = if (recoverable) "Please try again or contact support." else null
        )

        /**
         * Create an ErrorInfo for payment failures.
         */
        fun paymentDeclined(message: String? = null): ErrorInfo = ErrorInfo(
            code = "PAYMENT_DECLINED",
            message = message ?: "Your card was declined by your bank.",
            recoverable = true,
            suggestedAction = "Please update your payment method and try again."
        )

        /**
         * Create an ErrorInfo for inventory failures.
         */
        fun outOfStock(message: String? = null): ErrorInfo = ErrorInfo(
            code = "OUT_OF_STOCK",
            message = message ?: "One or more items are out of stock.",
            recoverable = true,
            suggestedAction = "Please remove unavailable items and try again."
        )

        /**
         * Create an ErrorInfo for shipping failures.
         */
        fun shippingUnavailable(message: String? = null): ErrorInfo = ErrorInfo(
            code = "SHIPPING_UNAVAILABLE",
            message = message ?: "Unable to arrange shipping to the provided address.",
            recoverable = true,
            suggestedAction = "Please verify your shipping address and try again."
        )
    }
}
