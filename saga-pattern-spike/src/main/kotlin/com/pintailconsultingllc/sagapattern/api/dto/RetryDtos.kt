package com.pintailconsultingllc.sagapattern.api.dto

import com.pintailconsultingllc.sagapattern.domain.RetryAttempt
import com.pintailconsultingllc.sagapattern.domain.RetryOutcome
import com.pintailconsultingllc.sagapattern.retry.ActionType
import com.pintailconsultingllc.sagapattern.retry.BlockerType
import com.pintailconsultingllc.sagapattern.retry.RequiredAction
import com.pintailconsultingllc.sagapattern.retry.RetryBlocker
import com.pintailconsultingllc.sagapattern.retry.RetryEligibility
import com.pintailconsultingllc.sagapattern.retry.SagaRetryResult
import java.time.Instant
import java.util.UUID

/**
 * Request DTO for initiating a retry.
 */
data class InitiateRetryRequest(
    /**
     * Updated payment method ID (optional).
     */
    val updatedPaymentMethodId: String? = null,

    /**
     * Updated shipping address (optional).
     */
    val updatedShippingAddress: ShippingAddressDto? = null,

    /**
     * Acknowledgment of price changes (if any).
     */
    val acknowledgedPriceChanges: List<String> = emptyList()
)

/**
 * Shipping address DTO.
 */
data class ShippingAddressDto(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)

/**
 * Response DTO for retry eligibility check.
 */
data class RetryEligibilityResponse(
    /**
     * Whether the order is eligible for retry.
     */
    val eligible: Boolean,

    /**
     * Number of retry attempts remaining.
     */
    val attemptsRemaining: Int,

    /**
     * When the retry window expires.
     */
    val expiresAt: Instant?,

    /**
     * When the next retry will be available (if in cooldown).
     */
    val nextRetryAvailableAt: Instant?,

    /**
     * Reason if not eligible.
     */
    val reason: String?,

    /**
     * Blockers preventing retry.
     */
    val blockers: List<RetryBlockerResponse>,

    /**
     * Required actions before retry can proceed.
     */
    val requiredActions: List<RequiredActionResponse>
) {
    companion object {
        fun fromRetryEligibility(eligibility: RetryEligibility): RetryEligibilityResponse =
            RetryEligibilityResponse(
                eligible = eligibility.eligible,
                attemptsRemaining = eligibility.retryAttemptsRemaining,
                expiresAt = eligibility.expiresAt,
                nextRetryAvailableAt = eligibility.nextRetryAvailableAt,
                reason = eligibility.reason,
                blockers = eligibility.blockers.map { RetryBlockerResponse.fromRetryBlocker(it) },
                requiredActions = eligibility.requiredActions.map { RequiredActionResponse.fromRequiredAction(it) }
            )
    }
}

/**
 * Response DTO for retry blockers.
 */
data class RetryBlockerResponse(
    val type: BlockerType,
    val description: String,
    val canBeResolved: Boolean
) {
    companion object {
        fun fromRetryBlocker(blocker: RetryBlocker): RetryBlockerResponse =
            RetryBlockerResponse(
                type = blocker.type,
                description = blocker.message,
                canBeResolved = blocker.resolvable
            )
    }
}

/**
 * Response DTO for required actions.
 */
data class RequiredActionResponse(
    val action: ActionType,
    val description: String,
    val completed: Boolean
) {
    companion object {
        fun fromRequiredAction(action: RequiredAction): RequiredActionResponse =
            RequiredActionResponse(
                action = action.action,
                description = action.description,
                completed = action.completed
            )
    }
}

/**
 * Response DTO for retry initiation result.
 */
data class RetryResultResponse(
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
    val executionId: UUID?,

    /**
     * ID of the retry attempt record.
     */
    val retryAttemptId: UUID?,

    /**
     * Step from which execution resumed.
     */
    val resumedFromStep: String?,

    /**
     * Steps that were skipped.
     */
    val skippedSteps: List<String>,

    /**
     * Compensated steps (if retry failed and required compensation).
     */
    val compensatedSteps: List<String>,

    /**
     * Failure reason (if not successful).
     */
    val failureReason: String?,

    /**
     * Confirmation number (if retry succeeded).
     */
    val confirmationNumber: String?,

    /**
     * Tracking number (if retry succeeded).
     */
    val trackingNumber: String?
) {
    companion object {
        fun fromSagaRetryResult(result: SagaRetryResult): RetryResultResponse =
            when (result) {
                is SagaRetryResult.Success -> RetryResultResponse(
                    success = true,
                    orderId = result.orderId,
                    executionId = result.executionId,
                    retryAttemptId = result.retryAttemptId,
                    resumedFromStep = null,
                    skippedSteps = result.skippedSteps,
                    compensatedSteps = emptyList(),
                    failureReason = null,
                    confirmationNumber = result.result.confirmationNumber,
                    trackingNumber = result.result.trackingNumber
                )
                is SagaRetryResult.Failed -> RetryResultResponse(
                    success = false,
                    orderId = result.orderId,
                    executionId = result.executionId,
                    retryAttemptId = result.retryAttemptId,
                    resumedFromStep = null,
                    skippedSteps = result.skippedSteps,
                    compensatedSteps = emptyList(),
                    failureReason = result.failureReason,
                    confirmationNumber = null,
                    trackingNumber = null
                )
                is SagaRetryResult.Compensated -> RetryResultResponse(
                    success = false,
                    orderId = result.orderId,
                    executionId = result.executionId,
                    retryAttemptId = result.retryAttemptId,
                    resumedFromStep = null,
                    skippedSteps = result.skippedSteps,
                    compensatedSteps = result.compensatedSteps,
                    failureReason = result.failureReason,
                    confirmationNumber = null,
                    trackingNumber = null
                )
                is SagaRetryResult.NotEligible -> RetryResultResponse(
                    success = false,
                    orderId = result.orderId,
                    executionId = null,
                    retryAttemptId = null,
                    resumedFromStep = null,
                    skippedSteps = emptyList(),
                    compensatedSteps = emptyList(),
                    failureReason = result.reason,
                    confirmationNumber = null,
                    trackingNumber = null
                )
            }
    }
}

/**
 * Response DTO for retry history.
 */
data class RetryHistoryResponse(
    /**
     * Order ID.
     */
    val orderId: UUID,

    /**
     * List of retry attempts.
     */
    val attempts: List<RetryAttemptResponse>
)

/**
 * Response DTO for individual retry attempt.
 */
data class RetryAttemptResponse(
    /**
     * Retry attempt ID.
     */
    val id: UUID,

    /**
     * Attempt number (1-based).
     */
    val attemptNumber: Int,

    /**
     * When the retry was initiated.
     */
    val initiatedAt: Instant,

    /**
     * When the retry completed.
     */
    val completedAt: Instant?,

    /**
     * Outcome of the retry.
     */
    val outcome: RetryOutcome?,

    /**
     * Step from which execution resumed.
     */
    val resumedFromStep: String?,

    /**
     * Steps that were skipped.
     */
    val skippedSteps: List<String>,

    /**
     * Failure reason (if failed).
     */
    val failureReason: String?
) {
    companion object {
        fun fromRetryAttempt(attempt: RetryAttempt): RetryAttemptResponse =
            RetryAttemptResponse(
                id = attempt.id,
                attemptNumber = attempt.attemptNumber,
                initiatedAt = attempt.initiatedAt,
                completedAt = attempt.completedAt,
                outcome = attempt.outcome,
                resumedFromStep = attempt.resumedFromStep,
                skippedSteps = attempt.skippedSteps?.toList() ?: emptyList(),
                failureReason = attempt.failureReason
            )
    }
}
