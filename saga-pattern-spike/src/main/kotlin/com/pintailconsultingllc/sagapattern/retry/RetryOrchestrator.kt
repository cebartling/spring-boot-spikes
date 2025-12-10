package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.RetryOutcome
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.event.DomainEventPublisher
import com.pintailconsultingllc.sagapattern.event.SagaCompensationCompleted
import com.pintailconsultingllc.sagapattern.event.SagaCompensationStarted
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.RetryAttemptRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaResult
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.SagaStepRegistry
import com.pintailconsultingllc.sagapattern.saga.ShippingAddress
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.saga.compensation.CompensationSummary
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Orchestrator for retrying failed saga executions.
 *
 * Manages the retry of sagas by resuming from the failed step
 * while optionally skipping steps whose results are still valid.
 */
@Component
class RetryOrchestrator(
    private val sagaStepRegistry: SagaStepRegistry,
    private val orderRepository: OrderRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val retryAttemptRepository: RetryAttemptRepository,
    private val orderRetryService: OrderRetryService,
    private val sagaMetrics: SagaMetrics,
    private val domainEventPublisher: DomainEventPublisher
) {
    private val logger = LoggerFactory.getLogger(RetryOrchestrator::class.java)
    private val objectMapper = jacksonObjectMapper()

    // Cache ordered steps from the registry (steps are static at runtime)
    private val orderedSteps: List<SagaStep> = sagaStepRegistry.getOrderedSteps()

    /**
     * Execute a saga retry operation.
     *
     * @param orderId The order to retry
     * @param request The retry request with any updated information
     * @return The result of the retry operation
     */
    @Observed(name = "saga.retry.orchestrator", contextualName = "execute-retry")
    @Transactional
    suspend fun executeRetry(orderId: UUID, request: RetryRequest): SagaRetryResult {
        val startTime = Instant.now()
        logger.info("Starting saga retry for order {}", orderId)

        // Check retry eligibility
        val eligibility = orderRetryService.checkRetryEligibility(orderId)
        if (!eligibility.eligible) {
            logger.warn("Order {} is not eligible for retry: {}", orderId, eligibility.reason)
            return SagaRetryResult.NotEligible(
                orderId = orderId,
                reason = eligibility.reason ?: "Not eligible for retry",
                blockers = eligibility.blockers
            )
        }

        // Get the order and original execution
        val order = orderRepository.findById(orderId)
            ?: return SagaRetryResult.NotEligible(
                orderId = orderId,
                reason = "Order not found",
                blockers = emptyList()
            )

        val originalExecution = sagaExecutionRepository.findByOrderId(orderId)
            ?: return SagaRetryResult.NotEligible(
                orderId = orderId,
                reason = "No previous execution found",
                blockers = emptyList()
            )

        // Create new saga execution for the retry
        val retryExecutionId = UUID.randomUUID()
        val retryExecution = sagaExecutionRepository.save(
            SagaExecution(
                id = retryExecutionId,
                orderId = orderId,
                status = SagaStatus.IN_PROGRESS,
                startedAt = Instant.now()
            )
        )

        // Create retry attempt record
        val retryCount = retryAttemptRepository.countByOrderId(orderId)
        val retryAttempt = retryAttemptRepository.save(
            com.pintailconsultingllc.sagapattern.domain.RetryAttempt(
                orderId = orderId,
                originalExecutionId = originalExecution.id,
                retryExecutionId = retryExecutionId,
                attemptNumber = (retryCount + 1).toInt()
            )
        )

        // Build saga context
        val context = buildSagaContext(order, retryExecutionId, request)

        // Determine resume point
        val resumePoint = orderRetryService.determineResumePoint(originalExecution, context)

        // Update retry attempt with step info
        retryAttemptRepository.updateExecutionDetails(
            retryAttempt.id,
            retryExecutionId,
            resumePoint.resumeStepName,
            resumePoint.skippedSteps.toTypedArray()
        )

        // Update order status to PROCESSING
        orderRepository.updateStatus(orderId, OrderStatus.PROCESSING)

        logger.info(
            "Retry saga for order {}: resuming from step '{}', skipping steps {}",
            orderId,
            resumePoint.resumeStepName,
            resumePoint.skippedSteps
        )

        // Execute steps with skip logic
        val executionResult = executeStepsWithSkip(
            context = context,
            sagaExecution = retryExecution,
            resumePoint = resumePoint
        )

        // Record saga duration
        val duration = Duration.between(startTime, Instant.now())
        sagaMetrics.recordSagaDuration(duration)

        // Update retry attempt outcome
        val outcome = when (executionResult) {
            is StepExecutionResult.Success -> RetryOutcome.SUCCESS
            is StepExecutionResult.Failed -> RetryOutcome.FAILED
            is StepExecutionResult.Compensated -> RetryOutcome.FAILED
        }
        retryAttemptRepository.markCompleted(
            retryAttemptId = retryAttempt.id,
            outcome = outcome,
            failureReason = when (executionResult) {
                is StepExecutionResult.Failed -> executionResult.failureReason
                is StepExecutionResult.Compensated -> executionResult.failureReason
                else -> null
            },
            completedAt = Instant.now()
        )

        return when (executionResult) {
            is StepExecutionResult.Success -> {
                sagaMetrics.sagaCompleted()
                SagaRetryResult.Success(
                    orderId = orderId,
                    executionId = retryExecutionId,
                    retryAttemptId = retryAttempt.id,
                    skippedSteps = resumePoint.skippedSteps,
                    result = executionResult.sagaResult
                )
            }
            is StepExecutionResult.Failed -> {
                SagaRetryResult.Failed(
                    orderId = orderId,
                    executionId = retryExecutionId,
                    retryAttemptId = retryAttempt.id,
                    failedStep = executionResult.failedStep,
                    failureReason = executionResult.failureReason,
                    skippedSteps = resumePoint.skippedSteps
                )
            }
            is StepExecutionResult.Compensated -> {
                sagaMetrics.sagaCompensated(executionResult.failedStep)
                SagaRetryResult.Compensated(
                    orderId = orderId,
                    executionId = retryExecutionId,
                    retryAttemptId = retryAttempt.id,
                    failedStep = executionResult.failedStep,
                    failureReason = executionResult.failureReason,
                    compensatedSteps = executionResult.compensatedSteps,
                    skippedSteps = resumePoint.skippedSteps
                )
            }
        }
    }

    private fun buildSagaContext(
        order: Order,
        sagaExecutionId: UUID,
        request: RetryRequest
    ): SagaContext {
        val shippingAddress = request.updatedShippingAddress?.let {
            ShippingAddress(
                street = it.street,
                city = it.city,
                state = it.state,
                postalCode = it.postalCode,
                country = it.country
            )
        } ?: ShippingAddress(
            street = "",
            city = "",
            state = "",
            postalCode = "",
            country = ""
        )

        return SagaContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            customerId = order.customerId,
            paymentMethodId = request.updatedPaymentMethodId ?: "default-payment",
            shippingAddress = shippingAddress
        )
    }

    private suspend fun executeStepsWithSkip(
        context: SagaContext,
        sagaExecution: SagaExecution,
        resumePoint: ResumePoint
    ): StepExecutionResult {
        var failedStep: SagaStep? = null
        var failureResult: StepResult? = null
        var currentStepIndex = 0

        for ((index, step) in orderedSteps.withIndex()) {
            currentStepIndex = index
            val stepName = step.getStepName()

            if (stepName in resumePoint.skippedSteps) {
                // Create a skipped step record
                logger.info("Skipping step '{}' - result still valid from previous execution", stepName)
                val skippedStepResult = SagaStepResult.skipped(
                    sagaExecutionId = sagaExecution.id,
                    stepName = stepName,
                    stepOrder = index + 1
                )
                @Suppress("UNUSED_VARIABLE")
                val saved = sagaStepResultRepository.save(skippedStepResult)
                sagaMetrics.stepCompleted(stepName)
                continue
            }

            // Execute this step
            logger.info("Executing step '{}' ({}/{})", stepName, index + 1, orderedSteps.size)

            // Create step result record
            val stepResult = SagaStepResult.pending(
                sagaExecutionId = sagaExecution.id,
                stepName = stepName,
                stepOrder = index + 1
            )
            val savedStepResult = sagaStepResultRepository.save(stepResult)

            // Mark step as in progress
            sagaStepResultRepository.markInProgress(savedStepResult.id, Instant.now())
            sagaExecutionRepository.updateCurrentStep(sagaExecution.id, index + 1)

            // Execute the step
            val result = sagaMetrics.timeStepSuspend(stepName) {
                step.execute(context)
            }

            if (result.success) {
                // Record success
                val dataJson = if (result.data.isNotEmpty()) {
                    objectMapper.writeValueAsString(result.data)
                } else null
                sagaStepResultRepository.markCompleted(savedStepResult.id, dataJson, Instant.now())
                sagaMetrics.stepCompleted(stepName)
                logger.info("Step '{}' completed successfully", stepName)
            } else {
                // Record failure and break
                sagaStepResultRepository.markFailed(
                    savedStepResult.id,
                    result.errorMessage ?: "Unknown error",
                    Instant.now()
                )
                failedStep = step
                failureResult = result
                logger.error("Step '{}' failed: {}", stepName, result.errorMessage)
                break
            }
        }

        return if (failedStep == null) {
            // All steps completed successfully
            completeSuccessfulSaga(context, sagaExecution)
        } else {
            // Handle failure
            handleSagaFailure(
                context = context,
                sagaExecution = sagaExecution,
                failedStep = failedStep,
                failureResult = failureResult!!,
                failedStepIndex = currentStepIndex,
                skippedSteps = resumePoint.skippedSteps
            )
        }
    }

    private suspend fun completeSuccessfulSaga(
        context: SagaContext,
        sagaExecution: SagaExecution
    ): StepExecutionResult.Success {
        logger.info("Retry saga completed successfully for order {}", context.order.id)

        // Mark saga as completed
        sagaExecutionRepository.markCompleted(sagaExecution.id, Instant.now())

        // Update order status
        orderRepository.updateStatus(context.order.id, OrderStatus.COMPLETED)

        // Get the updated order
        val completedOrder = orderRepository.findById(context.order.id)!!
            .withStatus(OrderStatus.COMPLETED)

        // Extract delivery date from context
        val estimatedDelivery = context.getData<String>(SagaContext.KEY_ESTIMATED_DELIVERY)
            ?.let { LocalDate.parse(it) }
            ?: LocalDate.now().plusDays(5)

        return StepExecutionResult.Success(
            sagaResult = SagaResult.Success(
                order = completedOrder,
                confirmationNumber = SagaResult.generateConfirmationNumber(),
                totalChargedInCents = context.order.totalAmountInCents,
                estimatedDelivery = estimatedDelivery,
                trackingNumber = context.getData(SagaContext.KEY_TRACKING_NUMBER)
            )
        )
    }

    private suspend fun handleSagaFailure(
        context: SagaContext,
        sagaExecution: SagaExecution,
        failedStep: SagaStep,
        failureResult: StepResult,
        failedStepIndex: Int,
        skippedSteps: List<String>
    ): StepExecutionResult {
        logger.warn("Retry saga failed at step '{}'", failedStep.getStepName())

        // Record the failure
        sagaExecutionRepository.markFailed(
            sagaExecution.id,
            failedStepIndex + 1,
            failureResult.errorMessage ?: "Unknown error",
            Instant.now()
        )

        // Get steps that need compensation (executed steps, not skipped ones)
        val executedSteps = orderedSteps.take(failedStepIndex)
            .filter { it.getStepName() !in skippedSteps }

        if (executedSteps.isEmpty()) {
            // No steps need compensation
            logger.info("No steps need compensation (all prior steps were skipped)")
            orderRepository.updateStatus(context.order.id, OrderStatus.FAILED)

            return StepExecutionResult.Failed(
                failedStep = failedStep.getStepName(),
                failureReason = failureResult.errorMessage ?: "Unknown error"
            )
        }

        // Execute compensation for executed steps
        val compensationSummary = executeCompensation(
            context = context,
            sagaExecution = sagaExecution,
            executedSteps = executedSteps,
            failedStep = failedStep,
            failureReason = failureResult.errorMessage ?: "Unknown error"
        )

        return if (compensationSummary.allCompensationsSuccessful) {
            orderRepository.updateStatus(context.order.id, OrderStatus.COMPENSATED)
            sagaExecutionRepository.markCompensationCompleted(sagaExecution.id, Instant.now())

            StepExecutionResult.Compensated(
                failedStep = failedStep.getStepName(),
                failureReason = failureResult.errorMessage ?: "Unknown error",
                compensatedSteps = compensationSummary.compensatedSteps
            )
        } else {
            logger.error(
                "Partial compensation failure: succeeded={}, failed={}",
                compensationSummary.compensatedSteps,
                compensationSummary.failedCompensations
            )
            orderRepository.updateStatus(context.order.id, OrderStatus.FAILED)

            StepExecutionResult.Failed(
                failedStep = failedStep.getStepName(),
                failureReason = failureResult.errorMessage ?: "Unknown error"
            )
        }
    }

    @Observed(name = "saga.retry.compensation", contextualName = "execute-retry-compensation")
    private suspend fun executeCompensation(
        context: SagaContext,
        sagaExecution: SagaExecution,
        executedSteps: List<SagaStep>,
        failedStep: SagaStep,
        failureReason: String
    ): CompensationSummary {
        logger.info("Starting compensation for {} executed steps", executedSteps.size)

        // Mark compensation as started
        sagaExecutionRepository.markCompensationStarted(sagaExecution.id, Instant.now())
        orderRepository.updateStatus(context.order.id, OrderStatus.COMPENSATING)

        // Publish compensation started event
        domainEventPublisher.publishCompensationStarted(
            SagaCompensationStarted(
                orderId = context.order.id,
                failedStep = failedStep.getStepName(),
                stepsToCompensate = executedSteps.map { it.getStepName() }
            )
        )

        // Execute compensations in reverse order
        val stepResults = mutableMapOf<String, CompensationResult>()

        for (step in executedSteps.reversed()) {
            logger.info("Compensating step: {}", step.getStepName())

            val stepResultRecord = sagaStepResultRepository
                .findBySagaExecutionIdAndStepName(sagaExecution.id, step.getStepName())

            val compensationResult = try {
                val result = sagaMetrics.timeStepSuspend("${step.getStepName()}-compensate") {
                    step.compensate(context)
                }

                sagaMetrics.compensationExecuted(step.getStepName())

                if (result.success && stepResultRecord != null) {
                    sagaStepResultRepository.markCompensated(stepResultRecord.id, Instant.now())
                }

                result
            } catch (e: Exception) {
                logger.error("Compensation failed for step {}: {}", step.getStepName(), e.message, e)
                CompensationResult.failure("Unexpected error: ${e.message}")
            }

            stepResults[step.getStepName()] = compensationResult

            if (compensationResult.success) {
                logger.info("Successfully compensated step: {}", step.getStepName())
            } else {
                logger.error(
                    "Failed to compensate step {}: {}",
                    step.getStepName(),
                    compensationResult.message
                )
            }
        }

        logger.info("Compensation completed. Results: {}", stepResults)

        // Publish compensation completed event
        val compensatedSteps = stepResults.filter { it.value.success }.map { it.key }
        val failedCompensations = stepResults.filter { !it.value.success }.map { it.key }
        domainEventPublisher.publishCompensationCompleted(
            SagaCompensationCompleted(
                orderId = context.order.id,
                compensatedSteps = compensatedSteps,
                failedCompensations = failedCompensations,
                allSuccessful = failedCompensations.isEmpty()
            )
        )

        return CompensationSummary.fromResults(
            failedStep = failedStep.getStepName(),
            failureReason = failureReason,
            stepResults = stepResults
        )
    }
}

/**
 * Internal result of step execution within the retry orchestrator.
 */
sealed class StepExecutionResult {
    data class Success(
        val sagaResult: SagaResult.Success
    ) : StepExecutionResult()

    data class Failed(
        val failedStep: String,
        val failureReason: String
    ) : StepExecutionResult()

    data class Compensated(
        val failedStep: String,
        val failureReason: String,
        val compensatedSteps: List<String>
    ) : StepExecutionResult()
}

/**
 * Result of a saga retry operation.
 */
sealed class SagaRetryResult {
    abstract val orderId: UUID

    data class Success(
        override val orderId: UUID,
        val executionId: UUID,
        val retryAttemptId: UUID,
        val skippedSteps: List<String>,
        val result: SagaResult.Success
    ) : SagaRetryResult()

    data class Failed(
        override val orderId: UUID,
        val executionId: UUID,
        val retryAttemptId: UUID,
        val failedStep: String,
        val failureReason: String,
        val skippedSteps: List<String>
    ) : SagaRetryResult()

    data class Compensated(
        override val orderId: UUID,
        val executionId: UUID,
        val retryAttemptId: UUID,
        val failedStep: String,
        val failureReason: String,
        val compensatedSteps: List<String>,
        val skippedSteps: List<String>
    ) : SagaRetryResult()

    data class NotEligible(
        override val orderId: UUID,
        val reason: String,
        val blockers: List<RetryBlocker>
    ) : SagaRetryResult()
}
