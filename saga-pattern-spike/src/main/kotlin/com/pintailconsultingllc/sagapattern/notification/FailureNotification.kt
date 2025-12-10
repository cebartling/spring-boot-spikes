package com.pintailconsultingllc.sagapattern.notification

import com.pintailconsultingllc.sagapattern.util.ErrorSuggestions
import java.util.UUID

/**
 * Notification sent to customers when their order saga fails.
 *
 * Contains all information needed to inform the customer about:
 * - What happened (which step failed)
 * - What was done about it (compensation/rollback)
 * - What they can do next (suggested actions)
 */
data class FailureNotification(
    val orderId: UUID,
    val customerId: UUID,
    val failedStep: String,
    val failureReason: String,
    val compensationStatus: CompensationStatus,
    val compensatedSteps: List<String>,
    val suggestions: List<String>
) {
    /**
     * Status of the compensation process.
     */
    enum class CompensationStatus {
        /** No compensation was needed (first step failed) */
        NOT_NEEDED,
        /** All compensations completed successfully */
        COMPLETED,
        /** Some compensations failed - manual intervention may be needed */
        PARTIAL
    }

    companion object {
        /**
         * Create a notification for a first-step failure (no compensation needed).
         */
        fun forFirstStepFailure(
            orderId: UUID,
            customerId: UUID,
            failedStep: String,
            failureReason: String,
            suggestions: List<String>
        ): FailureNotification = FailureNotification(
            orderId = orderId,
            customerId = customerId,
            failedStep = failedStep,
            failureReason = failureReason,
            compensationStatus = CompensationStatus.NOT_NEEDED,
            compensatedSteps = emptyList(),
            suggestions = suggestions
        )

        /**
         * Create a notification for a failure with successful compensation.
         */
        fun forCompensatedFailure(
            orderId: UUID,
            customerId: UUID,
            failedStep: String,
            failureReason: String,
            compensatedSteps: List<String>,
            suggestions: List<String>
        ): FailureNotification = FailureNotification(
            orderId = orderId,
            customerId = customerId,
            failedStep = failedStep,
            failureReason = failureReason,
            compensationStatus = CompensationStatus.COMPLETED,
            compensatedSteps = compensatedSteps,
            suggestions = suggestions
        )

        /**
         * Create a notification for a failure with partial compensation.
         */
        fun forPartialCompensation(
            orderId: UUID,
            customerId: UUID,
            failedStep: String,
            failureReason: String,
            compensatedSteps: List<String>,
            suggestions: List<String>
        ): FailureNotification = FailureNotification(
            orderId = orderId,
            customerId = customerId,
            failedStep = failedStep,
            failureReason = failureReason,
            compensationStatus = CompensationStatus.PARTIAL,
            compensatedSteps = compensatedSteps,
            suggestions = suggestions
        )

        /**
         * Generate suggestions based on the error code.
         */
        fun suggestionsForError(errorCode: String?): List<String> = 
            ErrorSuggestions.suggestionsForError(errorCode)
    }
}
