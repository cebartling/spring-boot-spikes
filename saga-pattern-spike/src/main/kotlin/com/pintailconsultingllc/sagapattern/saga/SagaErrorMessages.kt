package com.pintailconsultingllc.sagapattern.saga

/**
 * Standardized error messages for saga operations.
 *
 * Using a centralized error message object ensures consistency across the codebase,
 * makes messages easier to find and update, and enables potential i18n support.
 */
object SagaErrorMessages {

    // Step execution errors
    fun noItemsToReserve(): String =
        "Cannot reserve inventory: order has no items"

    fun stepExecutionFailed(stepName: String, reason: String?): String =
        "Step '$stepName' failed: ${reason ?: "unknown reason"}"

    fun stepUnexpectedError(stepName: String, reason: String?): String =
        "Unexpected error during step '$stepName': ${reason ?: "unknown"}"

    // Compensation errors
    fun compensationFailed(stepName: String, reason: String?): String =
        "Failed to compensate step '$stepName': ${reason ?: "unknown reason"}"

    fun compensationUnexpectedError(stepName: String, reason: String?): String =
        "Unexpected error during compensation of '$stepName': ${reason ?: "unknown"}"

    // Compensation success messages
    fun compensationNotRequired(stepName: String): String =
        "No compensation required for step '$stepName' (not executed or nothing to undo)"

    fun inventoryReleased(reservationId: String): String =
        "Released inventory reservation: $reservationId"

    fun paymentVoided(authorizationId: String): String =
        "Voided payment authorization: $authorizationId"

    fun shipmentCancelled(shipmentId: String): String =
        "Cancelled shipment: $shipmentId"

    // Saga-level errors
    fun sagaInitializationFailed(reason: String?): String =
        "Failed to initialize saga: ${reason ?: "unknown reason"}"

    fun sagaFinalizationFailed(reason: String?): String =
        "Failed to finalize saga: ${reason ?: "unknown reason"}"

    // Retry errors
    fun retryNotEligible(orderId: String, reason: String?): String =
        "Order '$orderId' is not eligible for retry: ${reason ?: "unknown reason"}"

    fun retryContextBuildFailed(reason: String?): String =
        "Failed to build retry context: ${reason ?: "unknown reason"}"

    // Generic fallback
    fun unknownError(context: String? = null): String =
        if (context != null) "Unknown error during $context" else "An unexpected error occurred"

    // Error codes
    object Codes {
        const val NO_ITEMS = "NO_ITEMS"
        const val STEP_FAILED = "STEP_FAILED"
        const val UNEXPECTED_ERROR = "UNEXPECTED_ERROR"
        const val COMPENSATION_FAILED = "COMPENSATION_FAILED"
        const val RETRY_NOT_ELIGIBLE = "RETRY_NOT_ELIGIBLE"
        const val CONTEXT_BUILD_FAILED = "CONTEXT_BUILD_FAILED"
        const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
    }
}
