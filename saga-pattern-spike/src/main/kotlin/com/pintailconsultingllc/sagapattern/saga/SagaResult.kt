package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Represents the final result of a saga execution.
 */
sealed class SagaResult {
    /**
     * Saga completed successfully.
     */
    data class Success(
        val order: Order,
        val confirmationNumber: String,
        val totalCharged: BigDecimal,
        val estimatedDelivery: LocalDate,
        val trackingNumber: String? = null
    ) : SagaResult() {
        val status: OrderStatus = OrderStatus.COMPLETED
    }

    /**
     * Saga failed and compensation was executed.
     */
    data class Compensated(
        val order: Order,
        val failedStep: String,
        val failureReason: String,
        val compensatedSteps: List<String>
    ) : SagaResult() {
        val status: OrderStatus = OrderStatus.COMPENSATED
    }

    /**
     * Saga failed without compensation (first step failed).
     */
    data class Failed(
        val order: Order,
        val failedStep: String,
        val failureReason: String,
        val errorCode: String? = null
    ) : SagaResult() {
        val status: OrderStatus = OrderStatus.FAILED
    }

    companion object {
        /**
         * Generate a unique confirmation number for successful orders.
         */
        fun generateConfirmationNumber(): String {
            val year = LocalDate.now().year
            val random = UUID.randomUUID().toString().take(8).uppercase()
            return "ORD-$year-$random"
        }
    }
}
