package com.pintailconsultingllc.sagapattern.saga.compensation

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.event.DomainEventPublisher
import com.pintailconsultingllc.sagapattern.event.SagaCompensationCompleted
import com.pintailconsultingllc.sagapattern.event.SagaCompensationStarted
import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.saga.event.SagaEventRecorder
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Request object for compensation execution.
 *
 * Encapsulates all the information needed to execute compensation
 * for a failed saga execution.
 */
data class CompensationRequest(
    val context: SagaContext,
    val sagaExecution: SagaExecution,
    val completedSteps: List<SagaStep>,
    val failedStep: SagaStep,
    val failureReason: String,
    /**
     * Whether to record saga failed event.
     * Set to false for retry scenarios where the caller handles failure recording.
     */
    val recordSagaFailedEvent: Boolean = true
)

/**
 * Orchestrates compensation for failed saga steps.
 *
 * Extracted from OrderSagaOrchestrator to follow Single Responsibility Principle.
 * This class is responsible for:
 * - Executing compensation actions in reverse order
 * - Recording compensation events
 * - Publishing domain events for compensation lifecycle
 * - Updating step result status
 */
@Component
class CompensationOrchestrator(
    private val orderRepository: OrderRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val sagaMetrics: SagaMetrics,
    private val domainEventPublisher: DomainEventPublisher,
    private val sagaEventRecorder: SagaEventRecorder
) {
    private val logger = LoggerFactory.getLogger(CompensationOrchestrator::class.java)

    /**
     * Execute compensation for all completed steps in reverse order.
     *
     * @param request The compensation request containing all necessary context
     * @return Summary of the compensation execution
     */
    @Observed(name = "saga.compensation", contextualName = "execute-compensation")
    suspend fun executeCompensation(request: CompensationRequest): CompensationSummary {
        val (context, sagaExecution, completedSteps, failedStep, failureReason, recordSagaFailedEvent) = request

        logger.info("Starting compensation for {} completed steps", completedSteps.size)

        // Mark compensation as started
        markCompensationStarted(context.order.id, sagaExecution.id)

        // Publish compensation started event
        publishCompensationStartedEvent(context.order.id, failedStep, completedSteps)

        // Record compensation started in history
        sagaEventRecorder.recordCompensationStarted(context.order.id, sagaExecution.id, failedStep.getStepName())

        // Execute compensations in reverse order
        val stepResults = executeStepCompensations(context, sagaExecution.id, completedSteps)

        logger.info("Compensation completed. Results: {}", stepResults)

        // Publish compensation completed event
        val summary = createCompensationSummary(failedStep.getStepName(), failureReason, stepResults)
        publishCompensationCompletedEvent(context.order.id, summary)

        // Record saga failed event if requested
        if (recordSagaFailedEvent) {
            sagaEventRecorder.recordSagaFailed(
                context.order.id,
                sagaExecution.id,
                failedStep.getStepName(),
                ErrorInfo.fromStepFailure(null, failureReason)
            )
        }

        return summary
    }

    private suspend fun markCompensationStarted(orderId: UUID, sagaExecutionId: UUID) {
        sagaExecutionRepository.markCompensationStarted(sagaExecutionId, Instant.now())
        orderRepository.updateStatus(orderId, OrderStatus.COMPENSATING)
    }

    private fun publishCompensationStartedEvent(
        orderId: UUID,
        failedStep: SagaStep,
        completedSteps: List<SagaStep>
    ) {
        domainEventPublisher.publishCompensationStarted(
            SagaCompensationStarted(
                orderId = orderId,
                failedStep = failedStep.getStepName(),
                stepsToCompensate = completedSteps.map { it.getStepName() }
            )
        )
    }

    private suspend fun executeStepCompensations(
        context: SagaContext,
        sagaExecutionId: UUID,
        completedSteps: List<SagaStep>
    ): Map<String, CompensationResult> {
        val stepResults = mutableMapOf<String, CompensationResult>()

        for (step in completedSteps.reversed()) {
            logger.info("Compensating step: {}", step.getStepName())

            val compensationResult = compensateStep(context, sagaExecutionId, step)
            stepResults[step.getStepName()] = compensationResult

            recordCompensationResult(context.order.id, sagaExecutionId, step, compensationResult)
        }

        return stepResults
    }

    private suspend fun compensateStep(
        context: SagaContext,
        sagaExecutionId: UUID,
        step: SagaStep
    ): CompensationResult {
        val stepResultRecord = sagaStepResultRepository
            .findBySagaExecutionIdAndStepName(sagaExecutionId, step.getStepName())

        return try {
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
    }

    private suspend fun recordCompensationResult(
        orderId: UUID,
        sagaExecutionId: UUID,
        step: SagaStep,
        result: CompensationResult
    ) {
        if (result.success) {
            logger.info("Successfully compensated step: {}", step.getStepName())
            sagaEventRecorder.recordStepCompensated(orderId, sagaExecutionId, step.getStepName())
        } else {
            logger.error("Failed to compensate step {}: {}", step.getStepName(), result.message)
            sagaEventRecorder.recordCompensationFailed(
                orderId,
                sagaExecutionId,
                step.getStepName(),
                ErrorInfo.fromStepFailure("COMPENSATION_FAILED", result.message)
            )
        }
    }

    private fun createCompensationSummary(
        failedStep: String,
        failureReason: String,
        stepResults: Map<String, CompensationResult>
    ): CompensationSummary {
        return CompensationSummary.fromResults(
            failedStep = failedStep,
            failureReason = failureReason,
            stepResults = stepResults
        )
    }

    private fun publishCompensationCompletedEvent(orderId: UUID, summary: CompensationSummary) {
        domainEventPublisher.publishCompensationCompleted(
            SagaCompensationCompleted(
                orderId = orderId,
                compensatedSteps = summary.compensatedSteps,
                failedCompensations = summary.failedCompensations,
                allSuccessful = summary.allCompensationsSuccessful
            )
        )
    }
}
