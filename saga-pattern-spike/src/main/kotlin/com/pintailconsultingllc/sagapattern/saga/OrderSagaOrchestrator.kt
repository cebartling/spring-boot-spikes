package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.observability.TraceContextService
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.saga.compensation.CompensationOrchestrator
import com.pintailconsultingllc.sagapattern.saga.compensation.CompensationRequest
import com.pintailconsultingllc.sagapattern.saga.compensation.CompensationSummary
import com.pintailconsultingllc.sagapattern.saga.event.SagaEventRecorder
import com.pintailconsultingllc.sagapattern.saga.execution.StepExecutionOutcome
import com.pintailconsultingllc.sagapattern.saga.execution.StepExecutor
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Orchestrator for executing the order saga.
 *
 * Coordinates the execution of saga steps in sequence, handles failures,
 * and triggers compensation when necessary.
 *
 * Transaction boundaries are carefully managed:
 * - Phase 1 (initialization): Transactional - creates saga execution record
 * - Phase 2 (step execution): Non-transactional - allows external HTTP calls without holding connections
 * - Phase 3 (finalization): Transactional - updates final saga/order state
 */
@Component
class OrderSagaOrchestrator(
    private val sagaStepRegistry: SagaStepRegistry,
    private val orderRepository: OrderRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaMetrics: SagaMetrics,
    private val sagaEventRecorder: SagaEventRecorder,
    private val traceContextService: TraceContextService,
    private val compensationOrchestrator: CompensationOrchestrator,
    private val stepExecutor: StepExecutor
) {
    private val logger = LoggerFactory.getLogger(OrderSagaOrchestrator::class.java)

    // Cache ordered steps from the registry (steps are static at runtime)
    private val orderedSteps: List<SagaStep> = sagaStepRegistry.getOrderedSteps()

    /**
     * Start and execute the saga for an order.
     *
     * Transaction boundaries are managed per phase to avoid holding
     * database connections during external HTTP calls.
     *
     * @param context The saga context containing order and execution details
     * @return The result of the saga execution
     */
    @Observed(name = "saga.orchestrator", contextualName = "execute-saga")
    suspend fun executeSaga(context: SagaContext): SagaResult {
        val startTime = Instant.now()
        logger.info("Starting saga execution for order ${context.order.id}")
        sagaMetrics.sagaStarted()

        // Phase 1: Initialize saga (transactional)
        val sagaExecution = initializeSaga(context)

        // Phase 2: Execute steps (non-transactional - allows external HTTP calls)
        val outcome = stepExecutor.executeSteps(
            steps = orderedSteps,
            context = context,
            sagaExecutionId = sagaExecution.id,
            recordEvents = true
        )

        // Record saga duration
        val duration = Duration.between(startTime, Instant.now())
        sagaMetrics.recordSagaDuration(duration)

        // Phase 3: Finalize saga (transactional updates)
        return when (outcome) {
            is StepExecutionOutcome.AllSucceeded -> {
                completeSuccessfulSaga(context, sagaExecution, duration)
            }
            is StepExecutionOutcome.Failed -> {
                handleSagaFailure(context, sagaExecution, outcome.step, outcome.result, outcome.stepIndex)
            }
        }
    }

    /**
     * Phase 1: Initialize saga execution.
     *
     * Creates saga execution record, records the started event,
     * and updates order status to PROCESSING.
     * All operations are atomic within a single transaction.
     */
    @Transactional
    private suspend fun initializeSaga(context: SagaContext): SagaExecution {
        val execution = SagaExecution.create(
            id = context.sagaExecutionId,
            orderId = context.order.id,
            status = SagaStatus.IN_PROGRESS,
            startedAt = Instant.now(),
            traceId = traceContextService.getCurrentTraceId()
        )
        val savedExecution = sagaExecutionRepository.save(execution)

        // Record saga started event
        sagaEventRecorder.recordSagaStarted(context.order.id, savedExecution.id)

        // Update order status to PROCESSING
        orderRepository.updateStatus(context.order.id, OrderStatus.PROCESSING)

        return savedExecution
    }

    /**
     * Phase 3: Finalize successful saga execution.
     *
     * Updates saga and order status, records completion events.
     * All operations are atomic within a single transaction.
     */
    @Transactional
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
        val estimatedDelivery = context.getData(SagaContext.ESTIMATED_DELIVERY)
            ?.let { LocalDate.parse(it) }
            ?: LocalDate.now().plusDays(5)

        val confirmationNumber = SagaResult.generateConfirmationNumber()
        val trackingNumber = context.getData(SagaContext.TRACKING_NUMBER)

        // Record saga completed and order completed events
        sagaEventRecorder.recordSagaCompleted(
            context.order.id,
            sagaExecution.id,
            mapOf(
                "confirmationNumber" to confirmationNumber,
                "totalChargedInCents" to context.order.totalAmountInCents,
                "estimatedDelivery" to estimatedDelivery.toString()
            ).let { if (trackingNumber != null) it + ("trackingNumber" to trackingNumber) else it }
        )
        sagaEventRecorder.recordOrderCompleted(context.order.id, sagaExecution.id)

        return SagaResult.Success(
            order = completedOrder,
            confirmationNumber = confirmationNumber,
            totalChargedInCents = context.order.totalAmountInCents,
            estimatedDelivery = estimatedDelivery,
            trackingNumber = trackingNumber
        )
    }

    /**
     * Handle saga failure - coordinates failure handling with proper transaction boundaries.
     *
     * This method is intentionally non-transactional as it coordinates multiple operations:
     * 1. Record initial failure (transactional)
     * 2. Execute compensation (non-transactional - involves HTTP calls)
     * 3. Finalize saga state (transactional)
     */
    private suspend fun handleSagaFailure(
        context: SagaContext,
        sagaExecution: SagaExecution,
        failedStep: SagaStep,
        failureResult: StepResult,
        failedStepIndex: Int
    ): SagaResult {
        logger.warn("Saga failed at step ${failedStep.getStepName()}")

        // Record the initial failure (transactional)
        recordInitialFailure(sagaExecution.id, failedStepIndex, failureResult.errorMessage)

        // Get completed steps (steps before the failed one)
        val completedSteps = orderedSteps.take(failedStepIndex)

        if (completedSteps.isEmpty()) {
            // First step failed, no compensation needed - complete failure handling (transactional)
            return completeFirstStepFailure(context, sagaExecution, failedStep, failureResult)
        }

        // Execute compensation for completed steps (non-transactional - involves HTTP calls)
        val compensationSummary = compensationOrchestrator.executeCompensation(
            CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = completedSteps,
                failedStep = failedStep,
                failureReason = failureResult.errorMessage ?: "Unknown error",
                recordSagaFailedEvent = true
            )
        )

        // Record metrics
        sagaMetrics.sagaCompensated(failedStep.getStepName())

        // Finalize based on compensation result (transactional)
        return finalizeCompensation(context, sagaExecution, failedStep, failureResult, compensationSummary)
    }

    /**
     * Record initial saga failure in the database.
     */
    @Transactional
    private suspend fun recordInitialFailure(
        sagaExecutionId: java.util.UUID,
        failedStepIndex: Int,
        errorMessage: String?
    ) {
        sagaExecutionRepository.markFailed(
            sagaExecutionId,
            failedStepIndex + 1,
            errorMessage ?: "Unknown error",
            Instant.now()
        )
    }

    /**
     * Handle first step failure case (no compensation needed).
     * All operations are atomic within a single transaction.
     */
    @Transactional
    private suspend fun completeFirstStepFailure(
        context: SagaContext,
        sagaExecution: SagaExecution,
        failedStep: SagaStep,
        failureResult: StepResult
    ): SagaResult.Failed {
        logger.info("First step failed, no compensation needed")
        orderRepository.updateStatus(context.order.id, OrderStatus.FAILED)
        sagaMetrics.sagaCompensated(failedStep.getStepName())

        // Record saga failed event
        sagaEventRecorder.recordSagaFailed(
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

    /**
     * Finalize saga state after compensation execution.
     * Updates order and saga status based on compensation results.
     */
    @Transactional
    private suspend fun finalizeCompensation(
        context: SagaContext,
        sagaExecution: SagaExecution,
        failedStep: SagaStep,
        failureResult: StepResult,
        compensationSummary: CompensationSummary
    ): SagaResult {
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

}
