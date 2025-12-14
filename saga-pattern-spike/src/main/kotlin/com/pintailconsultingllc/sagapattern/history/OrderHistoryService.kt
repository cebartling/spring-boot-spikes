package com.pintailconsultingllc.sagapattern.history

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.repository.OrderEventRepository
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.RetryAttemptRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for retrieving order history and timeline information.
 */
interface OrderHistoryService {
    /**
     * Get complete order history including all executions and events.
     */
    suspend fun getOrderHistory(orderId: UUID): OrderHistory?

    /**
     * Get order timeline with human-readable entries.
     */
    suspend fun getOrderTimeline(orderId: UUID): OrderTimeline?

    /**
     * Get raw events for an order (for technical debugging).
     */
    suspend fun getOrderEvents(orderId: UUID): List<OrderEvent>

    /**
     * Get execution summaries for an order.
     */
    suspend fun getExecutionSummaries(orderId: UUID): List<SagaExecutionSummary>
}

/**
 * Implementation of OrderHistoryService.
 */
@Service
class OrderHistoryServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderEventRepository: OrderEventRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val retryAttemptRepository: RetryAttemptRepository,
    private val descriptionGenerator: TimelineDescriptionGenerator
) : OrderHistoryService {

    private val logger = LoggerFactory.getLogger(OrderHistoryServiceImpl::class.java)

    @Observed(name = "order.history.get", contextualName = "get-order-history")
    override suspend fun getOrderHistory(orderId: UUID): OrderHistory? {
        logger.debug("Fetching order history for: {}", orderId)

        val order = orderRepository.findById(orderId) ?: run {
            logger.warn("Order not found: {}", orderId)
            return null
        }

        val timeline = getOrderTimeline(orderId) ?: return null
        val executions = getExecutionSummaries(orderId)

        val completedAt = executions
            .filter { it.outcome == SagaOutcome.SUCCESS }
            .maxByOrNull { it.completedAt ?: it.startedAt }
            ?.completedAt

        return OrderHistory.create(
            orderId = orderId,
            createdAt = order.createdAt,
            finalStatus = order.status,
            completedAt = completedAt,
            timeline = timeline,
            executions = executions
        )
    }

    @Observed(name = "order.timeline.get", contextualName = "get-order-timeline")
    override suspend fun getOrderTimeline(orderId: UUID): OrderTimeline? {
        logger.debug("Fetching order timeline for: {}", orderId)

        val order = orderRepository.findById(orderId) ?: run {
            logger.warn("Order not found: {}", orderId)
            return null
        }

        val events = orderEventRepository.findByOrderIdOrderByTimestampAsc(orderId)
        val timelineEntries = events.map { descriptionGenerator.toTimelineEntry(it) }

        val executions = sagaExecutionRepository.findAllByOrderId(orderId)
        val executionCount = executions.size.coerceAtLeast(1)

        return OrderTimeline(
            orderId = orderId,
            orderNumber = OrderHistory.generateOrderNumber(orderId, order.createdAt),
            createdAt = order.createdAt,
            currentStatus = order.status,
            entries = timelineEntries,
            executionCount = executionCount
        )
    }

    @Observed(name = "order.events.get", contextualName = "get-order-events")
    override suspend fun getOrderEvents(orderId: UUID): List<OrderEvent> {
        logger.debug("Fetching raw events for order: {}", orderId)
        return orderEventRepository.findByOrderIdOrderByTimestampAsc(orderId)
    }

    @Observed(name = "order.executions.get", contextualName = "get-execution-summaries")
    override suspend fun getExecutionSummaries(orderId: UUID): List<SagaExecutionSummary> {
        logger.debug("Fetching execution summaries for order: {}", orderId)

        val executions = sagaExecutionRepository.findAllByOrderId(orderId)
        val retryAttempts = retryAttemptRepository.findByOrderIdOrderByAttemptNumberAsc(orderId)

        return executions.mapIndexed { index, execution ->
            val isRetry = retryAttempts.any { it.retryExecutionId == execution.id }
            val attemptNumber = if (isRetry) {
                retryAttempts.firstOrNull { it.retryExecutionId == execution.id }?.attemptNumber ?: (index + 1)
            } else {
                1
            }

            val stepsCompleted = countCompletedSteps(execution)
            val failedStepName = getFailedStepName(execution)

            toExecutionSummary(execution, attemptNumber, stepsCompleted, failedStepName, isRetry)
        }.sortedBy { it.attemptNumber }
    }

    private suspend fun countCompletedSteps(execution: SagaExecution): Int {
        val stepResults = sagaStepResultRepository.findBySagaExecutionIdOrderByStepOrder(execution.id)
        return stepResults.count {
            it.status == com.pintailconsultingllc.sagapattern.domain.StepStatus.COMPLETED
        }
    }

    private suspend fun getFailedStepName(execution: SagaExecution): String? {
        if (execution.failedStep == null) return null

        val stepResults = sagaStepResultRepository.findBySagaExecutionIdOrderByStepOrder(execution.id)
        return stepResults.find {
            it.status == com.pintailconsultingllc.sagapattern.domain.StepStatus.FAILED
        }?.stepName
    }

    private fun toExecutionSummary(
        execution: SagaExecution,
        attemptNumber: Int,
        stepsCompleted: Int,
        failedStepName: String?,
        isRetry: Boolean
    ): SagaExecutionSummary {
        val outcome = when (execution.status) {
            SagaStatus.COMPLETED -> SagaOutcome.SUCCESS
            SagaStatus.FAILED -> SagaOutcome.FAILED
            SagaStatus.COMPENSATED -> SagaOutcome.COMPENSATED
            SagaStatus.PENDING, SagaStatus.IN_PROGRESS, SagaStatus.COMPENSATING ->
                SagaOutcome.IN_PROGRESS
        }

        return SagaExecutionSummary(
            executionId = execution.id,
            attemptNumber = attemptNumber,
            startedAt = execution.startedAt,
            completedAt = execution.completedAt ?: execution.compensationCompletedAt,
            outcome = outcome,
            failedStep = failedStepName,
            stepsCompleted = stepsCompleted,
            isRetry = isRetry,
            traceId = execution.traceId
        )
    }
}
