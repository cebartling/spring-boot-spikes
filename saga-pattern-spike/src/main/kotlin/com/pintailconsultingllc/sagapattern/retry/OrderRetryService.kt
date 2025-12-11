package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.RetryAttempt
import com.pintailconsultingllc.sagapattern.domain.RetryOutcome
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.RetryAttemptRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.ShippingAddress as SagaShippingAddress
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Service for managing order retries.
 */
interface OrderRetryService {
    /**
     * Check if an order is eligible for retry.
     */
    suspend fun checkRetryEligibility(orderId: UUID): RetryEligibility

    /**
     * Initiate a retry for a failed order.
     */
    suspend fun initiateRetry(request: RetryRequest): RetryResult

    /**
     * Get the retry history for an order.
     */
    suspend fun getRetryHistory(orderId: UUID): List<RetryAttempt>

    /**
     * Determine which step to resume from and which steps to skip.
     */
    suspend fun determineResumePoint(
        sagaExecution: SagaExecution,
        context: SagaContext
    ): ResumePoint
}

/**
 * Information about where to resume saga execution.
 */
data class ResumePoint(
    /**
     * The step to resume from (0-based index).
     */
    val resumeStepIndex: Int,

    /**
     * Name of the step to resume from.
     */
    val resumeStepName: String,

    /**
     * Names of steps that can be skipped.
     */
    val skippedSteps: List<String>,

    /**
     * Steps that need to be re-executed even though they completed before.
     */
    val stepsToReExecute: List<String>
)

/**
 * Default implementation of OrderRetryService.
 */
@Service
@EnableConfigurationProperties(RetryConfiguration::class)
class DefaultOrderRetryService(
    private val orderRepository: OrderRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val retryAttemptRepository: RetryAttemptRepository,
    private val stepValidityChecker: StepValidityChecker,
    private val retryConfiguration: RetryConfiguration
) : OrderRetryService {

    private val logger = LoggerFactory.getLogger(DefaultOrderRetryService::class.java)

    // Error codes that indicate non-retryable failures
    private val nonRetryableErrorCodes = setOf(
        "FRAUD_DETECTED",
        "ACCOUNT_SUSPENDED",
        "ORDER_CANCELLED"
    )

    @Observed(name = "retry.eligibility.check", contextualName = "check-retry-eligibility")
    override suspend fun checkRetryEligibility(orderId: UUID): RetryEligibility {
        logger.debug("Checking retry eligibility for order {}", orderId)

        // Find the order
        val order = orderRepository.findById(orderId)
            ?: return RetryEligibility.ineligible(
                "Order not found",
                listOf(RetryBlocker(BlockerType.INVALID_ORDER_STATE, "Order does not exist", false))
            )

        // Check order status
        if (!isRetryableOrderStatus(order.status)) {
            return RetryEligibility.ineligible(
                "Order status ${order.status} is not retryable",
                listOf(RetryBlocker(BlockerType.INVALID_ORDER_STATE, "Order is not in a failed state", false))
            )
        }

        // Get the latest saga execution
        val sagaExecution = sagaExecutionRepository.findByOrderId(orderId)
            ?: return RetryEligibility.ineligible(
                "No saga execution found for order",
                listOf(RetryBlocker(BlockerType.INVALID_ORDER_STATE, "No execution history", false))
            )

        // Check for non-retryable failure reasons
        val failureReason = sagaExecution.failureReason
        if (failureReason != null && isNonRetryableFailure(failureReason)) {
            val blockerType = determineBlockerType(failureReason)
            return RetryEligibility.ineligible(
                "Non-retryable failure: $failureReason",
                listOf(RetryBlocker(blockerType, failureReason, false))
            )
        }

        // Check if there's an active retry in progress
        if (retryAttemptRepository.existsActiveRetryByOrderId(orderId)) {
            return RetryEligibility.retryInProgress()
        }

        // Count previous retry attempts
        val retryCount = retryAttemptRepository.countByOrderId(orderId)
        val attemptsRemaining = (retryConfiguration.maxAttempts - retryCount.toInt()).coerceAtLeast(0)

        if (attemptsRemaining <= 0) {
            return RetryEligibility.maxRetriesExceeded()
        }

        // Check cooldown
        val latestRetry = retryAttemptRepository.findLatestByOrderId(orderId)
        if (latestRetry != null) {
            val cooldownEnd = latestRetry.initiatedAt.plus(
                Duration.ofMinutes(retryConfiguration.cooldownMinutes.toLong())
            )
            if (Instant.now().isBefore(cooldownEnd)) {
                return RetryEligibility.inCooldown(cooldownEnd, attemptsRemaining)
            }
        }

        // Check if within retry window
        val retryWindowEnd = order.createdAt.plus(Duration.ofHours(retryConfiguration.windowHours.toLong()))
        if (Instant.now().isAfter(retryWindowEnd)) {
            return RetryEligibility.ineligible(
                "Retry window has expired",
                listOf(RetryBlocker(BlockerType.INVALID_ORDER_STATE, "Retry window expired", false))
            )
        }

        // Determine required actions based on failure
        val requiredActions = determineRequiredActions(sagaExecution)

        return RetryEligibility.eligible(
            attemptsRemaining = attemptsRemaining,
            requiredActions = requiredActions,
            expiresAt = retryWindowEnd
        )
    }

    @Observed(name = "retry.initiate", contextualName = "initiate-retry")
    override suspend fun initiateRetry(request: RetryRequest): RetryResult {
        logger.info("Initiating retry for order {}", request.orderId)

        // First check eligibility
        val eligibility = checkRetryEligibility(request.orderId)
        if (!eligibility.eligible) {
            return when {
                eligibility.reason?.contains("cooldown") == true ->
                    RetryResult.inCooldown(request.orderId, eligibility.nextRetryAvailableAt!!)
                eligibility.reason?.contains("Maximum") == true ->
                    RetryResult.maxRetriesExceeded(request.orderId)
                eligibility.reason?.contains("in progress") == true ->
                    RetryResult.retryInProgress(request.orderId)
                else ->
                    RetryResult.rejected(request.orderId, eligibility.reason ?: "Not eligible for retry")
            }
        }

        // Get the order and saga execution
        val order = orderRepository.findById(request.orderId)
        val originalExecution = sagaExecutionRepository.findByOrderId(request.orderId)
        if (order == null) {
            logger.warn("Order {} not found during retry initiation", request.orderId)
            return RetryResult.rejected(request.orderId, "Order not found")
        }
        if (originalExecution == null) {
            logger.warn("Saga execution for order {} not found during retry initiation", request.orderId)
            return RetryResult.rejected(request.orderId, "Saga execution not found")
        }

        // Create retry attempt record
        val retryCount = retryAttemptRepository.countByOrderId(request.orderId)
        val retryAttempt = RetryAttempt(
            orderId = request.orderId,
            originalExecutionId = originalExecution.id,
            attemptNumber = (retryCount + 1).toInt()
        )
        val savedRetryAttempt = retryAttemptRepository.save(retryAttempt)

        // Create saga context for determining resume point
        val defaultShippingAddress = SagaShippingAddress(
            street = "",
            city = "",
            state = "",
            postalCode = "",
            country = ""
        )
        val context = SagaContext(
            order = order,
            sagaExecutionId = originalExecution.id,
            customerId = order.customerId,
            paymentMethodId = request.updatedPaymentMethodId ?: "default-payment",
            shippingAddress = request.updatedShippingAddress?.let {
                SagaShippingAddress(
                    street = it.street,
                    city = it.city,
                    state = it.state,
                    postalCode = it.postalCode,
                    country = it.country
                )
            } ?: defaultShippingAddress
        )

        // Determine resume point
        val resumePoint = determineResumePoint(originalExecution, context)

        // Update retry attempt with step info
        retryAttemptRepository.updateExecutionDetails(
            savedRetryAttempt.id,
            savedRetryAttempt.id, // Will be updated when actual execution starts
            resumePoint.resumeStepName,
            resumePoint.skippedSteps.toTypedArray()
        )

        logger.info(
            "Retry initiated for order {}: resuming from {}, skipping {}",
            request.orderId,
            resumePoint.resumeStepName,
            resumePoint.skippedSteps
        )

        return RetryResult.initiated(
            orderId = request.orderId,
            executionId = savedRetryAttempt.id, // Placeholder until actual execution
            retryAttemptId = savedRetryAttempt.id,
            resumedFromStep = resumePoint.resumeStepName,
            skippedSteps = resumePoint.skippedSteps
        )
    }

    @Observed(name = "retry.history", contextualName = "get-retry-history")
    override suspend fun getRetryHistory(orderId: UUID): List<RetryAttempt> {
        return retryAttemptRepository.findByOrderIdOrderByAttemptNumberAsc(orderId)
    }

    override suspend fun determineResumePoint(
        sagaExecution: SagaExecution,
        context: SagaContext
    ): ResumePoint {
        logger.debug("Determining resume point for saga execution {}", sagaExecution.id)

        // Get all step results from the original execution
        val stepResults = sagaStepResultRepository
            .findBySagaExecutionIdOrderByStepOrder(sagaExecution.id)

        val skippedSteps = mutableListOf<String>()
        val stepsToReExecute = mutableListOf<String>()
        var resumeStepIndex = 0
        var resumeStepName = "Inventory Reservation" // Default to first step

        for (stepResult in stepResults) {
            if (stepResult.status == StepStatus.COMPLETED) {
                // Check if the completed step's result is still valid
                val validity = stepValidityChecker.isStepResultStillValid(stepResult, context)

                if (validity.valid) {
                    skippedSteps.add(stepResult.stepName)
                    resumeStepIndex = stepResult.stepOrder // Next step after this one
                } else {
                    // This step and all subsequent steps need to be re-executed
                    stepsToReExecute.add(stepResult.stepName)
                    resumeStepName = stepResult.stepName
                    break
                }
            } else if (stepResult.status == StepStatus.FAILED) {
                // Resume from the failed step
                resumeStepIndex = stepResult.stepOrder - 1 // 0-based index
                resumeStepName = stepResult.stepName
                break
            }
        }

        // If we haven't set a resume step name from the loop, use the step after skipped ones
        if (skippedSteps.isNotEmpty() && stepsToReExecute.isEmpty() && resumeStepName == "Inventory Reservation") {
            // Find the step after the last skipped one
            val lastSkipped = stepResults.lastOrNull { it.stepName in skippedSteps }
            if (lastSkipped != null) {
                val nextStep = stepResults.find { it.stepOrder == lastSkipped.stepOrder + 1 }
                if (nextStep != null) {
                    resumeStepName = nextStep.stepName
                    resumeStepIndex = nextStep.stepOrder - 1
                }
            }
        }

        return ResumePoint(
            resumeStepIndex = resumeStepIndex,
            resumeStepName = resumeStepName,
            skippedSteps = skippedSteps,
            stepsToReExecute = stepsToReExecute
        )
    }

    private fun isRetryableOrderStatus(status: OrderStatus): Boolean {
        return status in listOf(OrderStatus.FAILED, OrderStatus.COMPENSATED)
    }

    private fun isNonRetryableFailure(failureReason: String): Boolean {
        return nonRetryableErrorCodes.any { code ->
            failureReason.contains(code, ignoreCase = true)
        }
    }

    private fun determineBlockerType(failureReason: String): BlockerType {
        return when {
            failureReason.contains("FRAUD", ignoreCase = true) -> BlockerType.FRAUD_DETECTED
            failureReason.contains("SUSPENDED", ignoreCase = true) -> BlockerType.ACCOUNT_SUSPENDED
            failureReason.contains("CANCELLED", ignoreCase = true) -> BlockerType.ORDER_CANCELLED
            else -> BlockerType.INVALID_ORDER_STATE
        }
    }

    private fun determineRequiredActions(sagaExecution: SagaExecution): List<RequiredAction> {
        val actions = mutableListOf<RequiredAction>()
        val failureReason = sagaExecution.failureReason ?: return actions

        // Determine required actions based on failure reason
        when {
            failureReason.contains("payment", ignoreCase = true) ||
            failureReason.contains("declined", ignoreCase = true) ||
            failureReason.contains("card", ignoreCase = true) -> {
                actions.add(
                    RequiredAction(
                        action = ActionType.UPDATE_PAYMENT_METHOD,
                        description = "Your payment was declined. Please update your payment method.",
                        completed = false
                    )
                )
            }
            failureReason.contains("address", ignoreCase = true) ||
            failureReason.contains("shipping", ignoreCase = true) -> {
                actions.add(
                    RequiredAction(
                        action = ActionType.VERIFY_ADDRESS,
                        description = "There was an issue with your shipping address. Please verify it.",
                        completed = false
                    )
                )
            }
            failureReason.contains("inventory", ignoreCase = true) ||
            failureReason.contains("stock", ignoreCase = true) -> {
                actions.add(
                    RequiredAction(
                        action = ActionType.CONFIRM_ITEM_AVAILABILITY,
                        description = "Some items may have changed availability. Please confirm.",
                        completed = false
                    )
                )
            }
        }

        return actions
    }

    /**
     * Mark a retry attempt as completed.
     */
    suspend fun completeRetryAttempt(retryAttemptId: UUID, outcome: RetryOutcome, failureReason: String? = null) {
        retryAttemptRepository.markCompleted(
            retryAttemptId = retryAttemptId,
            outcome = outcome,
            failureReason = failureReason,
            completedAt = Instant.now()
        )
    }
}
