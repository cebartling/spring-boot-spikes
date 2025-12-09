package com.pintailconsultingllc.sagapattern.api.dto

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.saga.SagaResult
import java.math.BigDecimal
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
     * Total amount charged.
     */
    val totalCharged: BigDecimal,

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
            totalCharged = result.totalCharged,
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
            totalCharged = order.totalAmount,
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
     * The saga step that failed.
     */
    val failedStep: String,

    /**
     * Reason for the failure.
     */
    val failureReason: String,

    /**
     * Machine-readable error code.
     */
    val errorCode: String? = null,

    /**
     * Steps that were compensated (rolled back).
     */
    val compensatedSteps: List<String> = emptyList()
) {
    companion object {
        /**
         * Create response from a failed saga result.
         */
        fun fromFailed(result: SagaResult.Failed): OrderFailureResponse = OrderFailureResponse(
            orderId = result.order.id,
            status = result.status,
            failedStep = result.failedStep,
            failureReason = result.failureReason,
            errorCode = result.errorCode
        )

        /**
         * Create response from a compensated saga result.
         */
        fun fromCompensated(result: SagaResult.Compensated): OrderFailureResponse = OrderFailureResponse(
            orderId = result.order.id,
            status = result.status,
            failedStep = result.failedStep,
            failureReason = result.failureReason,
            compensatedSteps = result.compensatedSteps
        )
    }
}
