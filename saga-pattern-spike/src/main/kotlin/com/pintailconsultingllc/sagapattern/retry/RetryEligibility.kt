package com.pintailconsultingllc.sagapattern.retry

import java.time.Instant

/**
 * Represents the eligibility status for retrying a failed order.
 *
 * Contains information about whether the order can be retried, any blockers,
 * and required actions the customer must take before retry.
 */
data class RetryEligibility(
    /**
     * Whether the order is eligible for retry.
     */
    val eligible: Boolean,

    /**
     * Human-readable reason if not eligible.
     */
    val reason: String? = null,

    /**
     * List of blockers preventing retry.
     */
    val blockers: List<RetryBlocker> = emptyList(),

    /**
     * Actions required before retry can proceed.
     */
    val requiredActions: List<RequiredAction> = emptyList(),

    /**
     * When the retry eligibility expires (e.g., due to reservation expiry).
     */
    val expiresAt: Instant? = null,

    /**
     * Number of retry attempts remaining.
     */
    val retryAttemptsRemaining: Int = 0,

    /**
     * When the next retry will be available (if in cooldown).
     */
    val nextRetryAvailableAt: Instant? = null
) {
    companion object {
        /**
         * Create an eligible response.
         */
        fun eligible(
            attemptsRemaining: Int,
            requiredActions: List<RequiredAction> = emptyList(),
            expiresAt: Instant? = null
        ): RetryEligibility = RetryEligibility(
            eligible = true,
            retryAttemptsRemaining = attemptsRemaining,
            requiredActions = requiredActions,
            expiresAt = expiresAt
        )

        /**
         * Create an ineligible response due to a blocker.
         */
        fun ineligible(reason: String, blockers: List<RetryBlocker> = emptyList()): RetryEligibility =
            RetryEligibility(
                eligible = false,
                reason = reason,
                blockers = blockers
            )

        /**
         * Create an ineligible response due to cooldown.
         */
        fun inCooldown(nextAvailableAt: Instant, attemptsRemaining: Int): RetryEligibility =
            RetryEligibility(
                eligible = false,
                reason = "Retry cooldown period not elapsed",
                nextRetryAvailableAt = nextAvailableAt,
                retryAttemptsRemaining = attemptsRemaining
            )

        /**
         * Create an ineligible response due to max retries exceeded.
         */
        fun maxRetriesExceeded(): RetryEligibility = RetryEligibility(
            eligible = false,
            reason = "Maximum retry attempts exceeded",
            retryAttemptsRemaining = 0
        )

        /**
         * Create an ineligible response due to active retry in progress.
         */
        fun retryInProgress(): RetryEligibility = RetryEligibility(
            eligible = false,
            reason = "A retry is already in progress"
        )
    }
}

/**
 * Represents a blocker that prevents retry.
 */
data class RetryBlocker(
    /**
     * Type of blocker.
     */
    val type: BlockerType,

    /**
     * Human-readable message describing the blocker.
     */
    val message: String,

    /**
     * Whether the customer can resolve this blocker.
     */
    val resolvable: Boolean
)

/**
 * Types of blockers that can prevent retry.
 */
enum class BlockerType {
    /**
     * Fraud was detected on the order.
     */
    FRAUD_DETECTED,

    /**
     * The customer account is suspended.
     */
    ACCOUNT_SUSPENDED,

    /**
     * The order was explicitly cancelled.
     */
    ORDER_CANCELLED,

    /**
     * The item is no longer available.
     */
    ITEM_UNAVAILABLE,

    /**
     * Maximum retry attempts exceeded.
     */
    MAX_RETRIES_EXCEEDED,

    /**
     * The order is in a state that cannot be retried.
     */
    INVALID_ORDER_STATE
}

/**
 * Represents an action the customer must take before retry.
 */
data class RequiredAction(
    /**
     * Type of action required.
     */
    val action: ActionType,

    /**
     * Human-readable description of the action.
     */
    val description: String,

    /**
     * Whether the action has been completed.
     */
    val completed: Boolean = false
)

/**
 * Types of actions that may be required before retry.
 */
enum class ActionType {
    /**
     * Customer needs to update their payment method.
     */
    UPDATE_PAYMENT_METHOD,

    /**
     * Customer needs to verify their shipping address.
     */
    VERIFY_ADDRESS,

    /**
     * Customer needs to confirm item availability/changes.
     */
    CONFIRM_ITEM_AVAILABILITY,

    /**
     * Customer needs to accept a price change.
     */
    ACCEPT_PRICE_CHANGE
}
