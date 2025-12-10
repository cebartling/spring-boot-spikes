package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import java.time.Instant
import java.util.UUID

/**
 * Result of a retry operation.
 *
 * Indicates whether the retry was initiated successfully and provides
 * details about the retry execution.
 */
data class RetryResult(
    /**
     * Whether the retry was initiated successfully.
     */
    val success: Boolean,

    /**
     * ID of the order being retried.
     */
    val orderId: UUID,

    /**
     * ID of the new saga execution (if retry was initiated).
     */
    val newExecutionId: UUID? = null,

    /**
     * ID of the retry attempt record.
     */
    val retryAttemptId: UUID? = null,

    /**
     * Step from which execution resumed.
     */
    val resumedFromStep: String? = null,

    /**
     * Steps that were skipped because their results are still valid.
     */
    val skippedSteps: List<String> = emptyList(),

    /**
     * Current order status after retry initiation.
     */
    val orderStatus: OrderStatus? = null,

    /**
     * Reason if retry could not be initiated.
     */
    val failureReason: String? = null,

    /**
     * When the next retry will be available (if blocked by cooldown).
     */
    val nextRetryAvailableAt: Instant? = null,

    /**
     * Required actions that must be completed before retry.
     */
    val requiredActions: List<RequiredAction> = emptyList(),

    /**
     * Price changes that need acknowledgment before retry.
     */
    val priceChanges: List<PriceChange> = emptyList()
) {
    companion object {
        /**
         * Create a successful retry result.
         */
        fun initiated(
            orderId: UUID,
            executionId: UUID,
            retryAttemptId: UUID,
            resumedFromStep: String,
            skippedSteps: List<String>
        ): RetryResult = RetryResult(
            success = true,
            orderId = orderId,
            newExecutionId = executionId,
            retryAttemptId = retryAttemptId,
            resumedFromStep = resumedFromStep,
            skippedSteps = skippedSteps,
            orderStatus = OrderStatus.PROCESSING
        )

        /**
         * Create a result indicating retry was rejected.
         */
        fun rejected(orderId: UUID, reason: String): RetryResult = RetryResult(
            success = false,
            orderId = orderId,
            failureReason = reason
        )

        /**
         * Create a result indicating retry is in cooldown.
         */
        fun inCooldown(orderId: UUID, nextAvailableAt: Instant): RetryResult = RetryResult(
            success = false,
            orderId = orderId,
            failureReason = "Retry cooldown period not elapsed",
            nextRetryAvailableAt = nextAvailableAt
        )

        /**
         * Create a result indicating max retries exceeded.
         */
        fun maxRetriesExceeded(orderId: UUID): RetryResult = RetryResult(
            success = false,
            orderId = orderId,
            failureReason = "Maximum retry attempts exceeded"
        )

        /**
         * Create a result indicating a retry is already in progress.
         */
        fun retryInProgress(orderId: UUID): RetryResult = RetryResult(
            success = false,
            orderId = orderId,
            failureReason = "A retry is already in progress for this order"
        )

        /**
         * Create a result indicating required actions are pending.
         */
        fun requiresAction(orderId: UUID, requiredActions: List<RequiredAction>): RetryResult =
            RetryResult(
                success = false,
                orderId = orderId,
                failureReason = "Required actions must be completed before retry",
                requiredActions = requiredActions
            )

        /**
         * Create a result indicating price changes need acknowledgment.
         */
        fun requiresPriceAcknowledgment(orderId: UUID, priceChanges: List<PriceChange>): RetryResult =
            RetryResult(
                success = false,
                orderId = orderId,
                failureReason = "Price changes must be acknowledged before retry",
                priceChanges = priceChanges
            )
    }
}

/**
 * Represents a price change that needs acknowledgment.
 */
data class PriceChange(
    /**
     * Item identifier.
     */
    val itemId: String,

    /**
     * Item name/description.
     */
    val itemName: String,

    /**
     * Original price in cents.
     */
    val originalPriceInCents: Long,

    /**
     * New price in cents.
     */
    val newPriceInCents: Long,

    /**
     * Change identifier for acknowledgment.
     */
    val changeId: String
) {
    /**
     * Calculate the price difference.
     */
    val differenceInCents: Long
        get() = newPriceInCents - originalPriceInCents

    /**
     * Whether the price increased.
     */
    val isIncrease: Boolean
        get() = differenceInCents > 0
}
