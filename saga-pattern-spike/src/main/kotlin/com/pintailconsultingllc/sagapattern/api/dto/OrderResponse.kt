package com.pintailconsultingllc.sagapattern.api.dto

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.saga.SagaResult
import com.pintailconsultingllc.sagapattern.util.ErrorSuggestions
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Response DTO for successful order creation.
 */
data class OrderResponse(
    /**
     * Unique order identifier.
     */
    val orderId: UUID,

    /**
     * Current order status.
     */
    val status: OrderStatus,

    /**
     * Order confirmation number (only for completed orders).
     */
    val confirmationNumber: String? = null,

    /**
     * Total amount charged in cents.
     */
    val totalChargedInCents: Long,

    /**
     * Estimated delivery date.
     */
    val estimatedDelivery: LocalDate? = null,

    /**
     * Shipping tracking number (if available).
     */
    val trackingNumber: String? = null,

    /**
     * When the order was created.
     */
    val createdAt: Instant
) {
    companion object {
        /**
         * Create response from a successful saga result.
         */
        fun fromSuccess(result: SagaResult.Success): OrderResponse = OrderResponse(
            orderId = result.order.id,
            status = result.status,
            confirmationNumber = result.confirmationNumber,
            totalChargedInCents = result.totalChargedInCents,
            estimatedDelivery = result.estimatedDelivery,
            trackingNumber = result.trackingNumber,
            createdAt = result.order.createdAt
        )

        /**
         * Create response from an order entity.
         */
        fun fromOrder(order: Order): OrderResponse = OrderResponse(
            orderId = order.id,
            status = order.status,
            totalChargedInCents = order.totalAmountInCents,
            createdAt = order.createdAt
        )
    }
}

/**
 * Response DTO for order creation failure.
 */
data class OrderFailureResponse(
    /**
     * Unique order identifier.
     */
    val orderId: UUID,

    /**
     * Order status (FAILED or COMPENSATED).
     */
    val status: OrderStatus,

    /**
     * Error details for the failure.
     */
    val error: ErrorDetails,

    /**
     * Compensation details (what was rolled back).
     */
    val compensation: CompensationDetails,

    /**
     * Suggested actions for the customer.
     */
    val suggestions: List<String>
) {
    /**
     * Error details structure.
     */
    data class ErrorDetails(
        val code: String?,
        val message: String,
        val failedStep: String,
        val retryable: Boolean
    )

    /**
     * Compensation status structure.
     */
    data class CompensationDetails(
        val status: CompensationStatus,
        val reversedSteps: List<String>
    )

    /**
     * Compensation status enum.
     */
    enum class CompensationStatus {
        NOT_NEEDED,
        COMPLETED,
        PARTIAL
    }

    companion object {
        /**
         * Create response from a failed saga result (first step failed, no compensation).
         */
        fun fromFailed(result: SagaResult.Failed): OrderFailureResponse = OrderFailureResponse(
            orderId = result.order.id,
            status = result.status,
            error = ErrorDetails(
                code = result.errorCode,
                message = result.failureReason,
                failedStep = result.failedStep,
                retryable = isRetryable(result.errorCode)
            ),
            compensation = CompensationDetails(
                status = CompensationStatus.NOT_NEEDED,
                reversedSteps = emptyList()
            ),
            suggestions = ErrorSuggestions.suggestionsForError(result.errorCode)
        )

        /**
         * Create response from a compensated saga result.
         */
        fun fromCompensated(result: SagaResult.Compensated): OrderFailureResponse = OrderFailureResponse(
            orderId = result.order.id,
            status = result.status,
            error = ErrorDetails(
                code = null,
                message = result.failureReason,
                failedStep = result.failedStep,
                retryable = false
            ),
            compensation = CompensationDetails(
                status = CompensationStatus.COMPLETED,
                reversedSteps = result.compensatedSteps
            ),
            suggestions = listOf(
                "Please try again",
                "Contact customer support if the issue persists"
            )
        )

        /**
         * Create response from a partially compensated saga result.
         */
        fun fromPartiallyCompensated(result: SagaResult.PartiallyCompensated): OrderFailureResponse = OrderFailureResponse(
            orderId = result.order.id,
            status = result.status,
            error = ErrorDetails(
                code = null,
                message = result.failureReason,
                failedStep = result.failedStep,
                retryable = false
            ),
            compensation = CompensationDetails(
                status = CompensationStatus.PARTIAL,
                reversedSteps = result.compensatedSteps
            ),
            suggestions = listOf(
                "Contact customer support immediately",
                "Reference order ID: ${result.order.id}"
            )
        )

        private fun isRetryable(errorCode: String?): Boolean = when (errorCode) {
            "PAYMENT_DECLINED" -> true
            "INVALID_ADDRESS" -> true
            "PAYMENT_TIMEOUT" -> true
            "SERVICE_ERROR" -> true
            else -> false
        }
    }
}
