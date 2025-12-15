package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.event.DomainEventPublisher
import com.pintailconsultingllc.sagapattern.event.SagaCompensationCompleted
import com.pintailconsultingllc.sagapattern.event.SagaCompensationStarted
import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.history.OrderEventService
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.observability.TraceContextService
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.compensation.CompensationSummary
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Orchestrator for executing the order saga.
 *
 * Coordinates the execution of saga steps in sequence, handles failures,
 * and triggers compensation when necessary.
 */
@Component
class OrderSagaOrchestrator(
    private val sagaStepRegistry: SagaStepRegistry,
    private val orderRepository: OrderRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val sagaMetrics: SagaMetrics,
    private val domainEventPublisher: DomainEventPublisher,
    private val orderEventService: OrderEventService,
    private val traceContextService: TraceContextService
) {
    private val logger = LoggerFactory.getLogger(OrderSagaOrchestrator::class.java)
    private val objectMapper = jacksonObjectMapper()

    // Cache ordered steps from the registry (steps are static at runtime)
    private val orderedSteps: List<SagaStep> = sagaStepRegistry.getOrderedSteps()

    /**
     * Start and execute the saga for an order.
     *
     * @param context The saga context containing order and execution details
     * @return The result of the saga execution
     */
    @Observed(name = "saga.orchestrator", contextualName = "execute-saga")
    @Transactional
    suspend fun executeSaga(context: SagaContext): SagaResult {
        val startTime = Instant.now()
        logger.info("Starting saga execution for order ${context.order.id}")
        sagaMetrics.sagaStarted()

        // Create saga execution record
        val sagaExecution = createSagaExecution(context)

        // Record saga started event
        orderEventService.recordSagaStarted(context.order.id, sagaExecution.id)

        // Update order status to PROCESSING
        orderRepository.updateStatus(context.order.id, OrderStatus.PROCESSING)

        // Execute steps sequentially
        var currentStepIndex = 0
        var failedStep: SagaStep? = null
        var failureResult: StepResult? = null

        for ((index, step) in orderedSteps.withIndex()) {
            currentStepIndex = index
            logger.info("Executing step ${step.getStepName()} (${index + 1}/${orderedSteps.size})")

            // Create step result record
            val stepResult = createStepResultRecord(sagaExecution.id, step, index)

            // Mark step as in progress and record event
            sagaStepResultRepository.markInProgress(stepResult.id, Instant.now())
            sagaExecutionRepository.updateCurrentStep(sagaExecution.id, index + 1)
            orderEventService.recordStepStarted(context.order.id, sagaExecution.id, step.getStepName())

            // Execute the step
            val result = sagaMetrics.timeStepSuspend(step.getStepName()) {
                step.execute(context)
            }

            if (result.success) {
                // Record success
                val dataJson = if (result.data.isNotEmpty()) objectMapper.writeValueAsString(result.data) else null
                sagaStepResultRepository.markCompleted(stepResult.id, dataJson, Instant.now())
                sagaMetrics.stepCompleted(step.getStepName())
                orderEventService.recordStepCompleted(
                    context.order.id,
                    sagaExecution.id,
                    step.getStepName(),
                    result.data.ifEmpty { null }
                )
                logger.info("Step ${step.getStepName()} completed successfully")
            } else {
                // Record failure and break
                sagaStepResultRepository.markFailed(stepResult.id, result.errorMessage ?: "Unknown error", Instant.now())
                orderEventService.recordStepFailed(
                    context.order.id,
                    sagaExecution.id,
                    step.getStepName(),
                    ErrorInfo.fromStepFailure(result.errorCode, result.errorMessage)
                )
                failedStep = step
                failureResult = result
                logger.error("Step ${step.getStepName()} failed: ${result.errorMessage}")
                break
            }
        }

        // Record saga duration
        val duration = Duration.between(startTime, Instant.now())
        sagaMetrics.recordSagaDuration(duration)

        return if (failedStep == null) {
            // All steps completed successfully
            completeSuccessfulSaga(context, sagaExecution, duration)
        } else {
            // Handle failure - trigger compensation if needed
            handleSagaFailure(context, sagaExecution, failedStep, failureResult!!, currentStepIndex)
        }
    }

    private suspend fun createSagaExecution(context: SagaContext): SagaExecution {
        val execution = SagaExecution.create(
            id = context.sagaExecutionId,
            orderId = context.order.id,
            status = SagaStatus.IN_PROGRESS,
            startedAt = Instant.now(),
            traceId = traceContextService.getCurrentTraceId()
        )
        return sagaExecutionRepository.save(execution)
    }

    private suspend fun createStepResultRecord(sagaExecutionId: java.util.UUID, step: SagaStep, index: Int): SagaStepResult {
        val stepResult = SagaStepResult.pending(
            sagaExecutionId = sagaExecutionId,
            stepName = step.getStepName(),
            stepOrder = index + 1
        )
        return sagaStepResultRepository.save(stepResult)
    }

    private suspend fun completeSuccessfulSaga(
        context: SagaContext,
        sagaExecution: SagaExecution,
        duration: Duration
    ): SagaResult.Success {
        logger.info("Saga completed successfully for order ${context.order.id}")

        // Mark saga as completed
        sagaExecutionRepository.markCompleted(sagaExecution.id, Instant.now())

        // Update order status
        orderRepository.updateStatus(context.order.id, OrderStatus.COMPLETED)

        // Record metrics
        sagaMetrics.sagaCompleted()

        // Get the updated order
        val completedOrder = orderRepository.findById(context.order.id)!!
            .withStatus(OrderStatus.COMPLETED)

        // Extract delivery date from context
        val estimatedDelivery = context.getData<String>(SagaContext.KEY_ESTIMATED_DELIVERY)
            ?.let { LocalDate.parse(it) }
            ?: LocalDate.now().plusDays(5)

        val confirmationNumber = SagaResult.generateConfirmationNumber()
        val trackingNumber = context.getData<String>(SagaContext.KEY_TRACKING_NUMBER)

        // Record saga completed and order completed events
        orderEventService.recordSagaCompleted(
            context.order.id,
            sagaExecution.id,
            mapOf(
                "confirmationNumber" to confirmationNumber,
                "totalChargedInCents" to context.order.totalAmountInCents,
                "estimatedDelivery" to estimatedDelivery.toString()
            ).let { if (trackingNumber != null) it + ("trackingNumber" to trackingNumber) else it }
        )
        orderEventService.recordOrderCompleted(context.order.id, sagaExecution.id)

        return SagaResult.Success(
            order = completedOrder,
            confirmationNumber = confirmationNumber,
            totalChargedInCents = context.order.totalAmountInCents,
            estimatedDelivery = estimatedDelivery,
            trackingNumber = trackingNumber
        )
    }

    private suspend fun handleSagaFailure(
        context: SagaContext,
        sagaExecution: SagaExecution,
        failedStep: SagaStep,
        failureResult: StepResult,
        failedStepIndex: Int
    ): SagaResult {
        logger.warn("Saga failed at step ${failedStep.getStepName()}")

        // Record the failure
        sagaExecutionRepository.markFailed(
            sagaExecution.id,
            failedStepIndex + 1,
            failureResult.errorMessage ?: "Unknown error",
            Instant.now()
        )

        // Get completed steps (steps before the failed one)
        val completedSteps = orderedSteps.take(failedStepIndex)

        if (completedSteps.isEmpty()) {
            // First step failed, no compensation needed
            logger.info("First step failed, no compensation needed")
            orderRepository.updateStatus(context.order.id, OrderStatus.FAILED)
            sagaMetrics.sagaCompensated(failedStep.getStepName())

            // Record saga failed event
            orderEventService.recordSagaFailed(
                context.order.id,
                sagaExecution.id,
                failedStep.getStepName(),
                ErrorInfo.fromStepFailure(failureResult.errorCode, failureResult.errorMessage)
            )

            return SagaResult.Failed(
                order = context.order.withStatus(OrderStatus.FAILED),
                failedStep = failedStep.getStepName(),
                failureReason = failureResult.errorMessage ?: "Unknown error",
                errorCode = failureResult.errorCode
            )
        }

        // Execute compensation for completed steps
        val compensationSummary = executeCompensation(
            context = context,
            sagaExecution = sagaExecution,
            completedSteps = completedSteps,
            failedStep = failedStep,
            failureReason = failureResult.errorMessage ?: "Unknown error"
        )

        // Record metrics
        sagaMetrics.sagaCompensated(failedStep.getStepName())

        return if (compensationSummary.allCompensationsSuccessful) {
            // All compensations successful - mark order as compensated
            orderRepository.updateStatus(context.order.id, OrderStatus.COMPENSATED)
            sagaExecutionRepository.markCompensationCompleted(sagaExecution.id, Instant.now())

            SagaResult.Compensated(
                order = context.order.withStatus(OrderStatus.COMPENSATED),
                failedStep = failedStep.getStepName(),
                failureReason = failureResult.errorMessage ?: "Unknown error",
                compensatedSteps = compensationSummary.compensatedSteps
            )
        } else {
            // Some compensations failed - mark order as failed (partial compensation)
            logger.error(
                "Partial compensation failure: succeeded={}, failed={}",
                compensationSummary.compensatedSteps,
                compensationSummary.failedCompensations
            )
            orderRepository.updateStatus(context.order.id, OrderStatus.FAILED)

            // Return a result indicating partial compensation
            SagaResult.forPartialCompensation(
                order = context.order.withStatus(OrderStatus.FAILED),
                failedStep = failedStep.getStepName(),
                failureReason = failureResult.errorMessage ?: "Unknown error",
                compensatedSteps = compensationSummary.compensatedSteps,
                failedCompensations = compensationSummary.failedCompensations
            )
        }
    }

    /**
     * Execute compensation for all completed steps in reverse order.
     *
     * @param context The saga context
     * @param sagaExecution The saga execution record
     * @param completedSteps Steps that completed successfully and need compensation
     * @param failedStep The step that failed
     * @param failureReason The reason for the failure
     * @return Summary of the compensation execution
     */
    @Observed(name = "saga.compensation", contextualName = "execute-compensation")
    private suspend fun executeCompensation(
        context: SagaContext,
        sagaExecution: SagaExecution,
        completedSteps: List<SagaStep>,
        failedStep: SagaStep,
        failureReason: String
    ): CompensationSummary {
        logger.info("Starting compensation for {} completed steps", completedSteps.size)

        // Mark compensation as started
        sagaExecutionRepository.markCompensationStarted(sagaExecution.id, Instant.now())
        orderRepository.updateStatus(context.order.id, OrderStatus.COMPENSATING)

        // Publish compensation started event and record to history
        domainEventPublisher.publishCompensationStarted(
            SagaCompensationStarted(
                orderId = context.order.id,
                failedStep = failedStep.getStepName(),
                stepsToCompensate = completedSteps.map { it.getStepName() }
            )
        )
        orderEventService.recordCompensationStarted(context.order.id, sagaExecution.id, failedStep.getStepName())

        // Execute compensations in reverse order
        val stepResults = mutableMapOf<String, CompensationResult>()

        for (step in completedSteps.reversed()) {
            logger.info("Compensating step: {}", step.getStepName())

            // Find the step result record
            val stepResultRecord = sagaStepResultRepository
                .findBySagaExecutionIdAndStepName(sagaExecution.id, step.getStepName())

            val compensationResult = try {
                // Execute compensation with timing
                val result = sagaMetrics.timeStepSuspend("${step.getStepName()}-compensate") {
                    step.compensate(context)
                }

                // Record compensation metrics
                sagaMetrics.compensationExecuted(step.getStepName())

                // Update step result status
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
                orderEventService.recordStepCompensated(context.order.id, sagaExecution.id, step.getStepName())
            } else {
                logger.error(
                    "Failed to compensate step {}: {}",
                    step.getStepName(),
                    compensationResult.message
                )
                orderEventService.recordCompensationFailed(
                    context.order.id,
                    sagaExecution.id,
                    step.getStepName(),
                    ErrorInfo.fromStepFailure("COMPENSATION_FAILED", compensationResult.message)
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

        // Record saga failed event
        orderEventService.recordSagaFailed(
            context.order.id,
            sagaExecution.id,
            failedStep.getStepName(),
            ErrorInfo.fromStepFailure(null, failureReason)
        )

        return CompensationSummary.fromResults(
            failedStep = failedStep.getStepName(),
            failureReason = failureReason,
            stepResults = stepResults
        )
    }
}
