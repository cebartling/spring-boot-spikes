package com.pintailconsultingllc.sagapattern.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.sagapattern.repository.OrderEventRepository
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Service for recording and retrieving order events.
 */
interface OrderEventService {
    /**
     * Record a new event.
     */
    suspend fun recordEvent(event: OrderEvent): OrderEvent

    /**
     * Record an ORDER_CREATED event.
     */
    suspend fun recordOrderCreated(orderId: UUID): OrderEvent

    /**
     * Record a SAGA_STARTED event.
     */
    suspend fun recordSagaStarted(orderId: UUID, sagaExecutionId: UUID): OrderEvent

    /**
     * Record a STEP_STARTED event.
     */
    suspend fun recordStepStarted(orderId: UUID, sagaExecutionId: UUID, stepName: String): OrderEvent

    /**
     * Record a STEP_COMPLETED event.
     */
    suspend fun recordStepCompleted(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        details: Map<String, Any>? = null
    ): OrderEvent

    /**
     * Record a STEP_FAILED event.
     */
    suspend fun recordStepFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        errorInfo: ErrorInfo
    ): OrderEvent

    /**
     * Record a COMPENSATION_STARTED event.
     */
    suspend fun recordCompensationStarted(
        orderId: UUID,
        sagaExecutionId: UUID,
        failedStep: String
    ): OrderEvent

    /**
     * Record a STEP_COMPENSATED event.
     */
    suspend fun recordStepCompensated(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String
    ): OrderEvent

    /**
     * Record a COMPENSATION_FAILED event.
     */
    suspend fun recordCompensationFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        errorInfo: ErrorInfo
    ): OrderEvent

    /**
     * Record a SAGA_COMPLETED event.
     */
    suspend fun recordSagaCompleted(
        orderId: UUID,
        sagaExecutionId: UUID,
        details: Map<String, Any>? = null
    ): OrderEvent

    /**
     * Record a SAGA_FAILED event.
     */
    suspend fun recordSagaFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        failedStep: String,
        errorInfo: ErrorInfo
    ): OrderEvent

    /**
     * Record a RETRY_INITIATED event.
     */
    suspend fun recordRetryInitiated(
        orderId: UUID,
        sagaExecutionId: UUID,
        details: Map<String, Any>? = null
    ): OrderEvent

    /**
     * Record an ORDER_COMPLETED event.
     */
    suspend fun recordOrderCompleted(orderId: UUID, sagaExecutionId: UUID): OrderEvent

    /**
     * Get all events for an order.
     */
    suspend fun getEventsForOrder(orderId: UUID): List<OrderEvent>

    /**
     * Get events since a specific time.
     */
    suspend fun getEventsSince(orderId: UUID, since: Instant): List<OrderEvent>

    /**
     * Get the most recent event for an order.
     */
    suspend fun getLatestEvent(orderId: UUID): OrderEvent?
}

/**
 * Implementation of OrderEventService.
 */
@Service
class OrderEventServiceImpl(
    private val orderEventRepository: OrderEventRepository,
    private val objectMapper: ObjectMapper
) : OrderEventService {

    private val logger = LoggerFactory.getLogger(OrderEventServiceImpl::class.java)

    @Observed(name = "order.event.record", contextualName = "record-event")
    override suspend fun recordEvent(event: OrderEvent): OrderEvent {
        logger.debug("Recording event: {} for order {}", event.eventType, event.orderId)
        return orderEventRepository.save(event)
    }

    override suspend fun recordOrderCreated(orderId: UUID): OrderEvent {
        val event = OrderEvent.orderCreated(orderId)
        logger.info("Order created: {}", orderId)
        return recordEvent(event)
    }

    override suspend fun recordSagaStarted(orderId: UUID, sagaExecutionId: UUID): OrderEvent {
        val event = OrderEvent.sagaStarted(orderId, sagaExecutionId)
        logger.info("Saga started for order: {} (execution: {})", orderId, sagaExecutionId)
        return recordEvent(event)
    }

    override suspend fun recordStepStarted(orderId: UUID, sagaExecutionId: UUID, stepName: String): OrderEvent {
        val event = OrderEvent.stepStarted(orderId, sagaExecutionId, stepName)
        logger.debug("Step started: {} for order {}", stepName, orderId)
        return recordEvent(event)
    }

    override suspend fun recordStepCompleted(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        details: Map<String, Any>?
    ): OrderEvent {
        val detailsJson = details?.let { objectMapper.writeValueAsString(it) }
        val event = OrderEvent.stepCompleted(orderId, sagaExecutionId, stepName, detailsJson)
        logger.info("Step completed: {} for order {}", stepName, orderId)
        return recordEvent(event)
    }

    override suspend fun recordStepFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        errorInfo: ErrorInfo
    ): OrderEvent {
        val errorInfoJson = objectMapper.writeValueAsString(errorInfo)
        val event = OrderEvent.stepFailed(orderId, sagaExecutionId, stepName, errorInfoJson)
        logger.warn("Step failed: {} for order {} - {}", stepName, orderId, errorInfo.message)
        return recordEvent(event)
    }

    override suspend fun recordCompensationStarted(
        orderId: UUID,
        sagaExecutionId: UUID,
        failedStep: String
    ): OrderEvent {
        val event = OrderEvent.compensationStarted(orderId, sagaExecutionId, failedStep)
        logger.info("Compensation started for order: {} (failed at {})", orderId, failedStep)
        return recordEvent(event)
    }

    override suspend fun recordStepCompensated(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String
    ): OrderEvent {
        val event = OrderEvent.stepCompensated(orderId, sagaExecutionId, stepName)
        logger.info("Step compensated: {} for order {}", stepName, orderId)
        return recordEvent(event)
    }

    override suspend fun recordCompensationFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        errorInfo: ErrorInfo
    ): OrderEvent {
        val errorInfoJson = objectMapper.writeValueAsString(errorInfo)
        val event = OrderEvent.compensationFailed(orderId, sagaExecutionId, stepName, errorInfoJson)
        logger.error("Compensation failed: {} for order {} - {}", stepName, orderId, errorInfo.message)
        return recordEvent(event)
    }

    override suspend fun recordSagaCompleted(
        orderId: UUID,
        sagaExecutionId: UUID,
        details: Map<String, Any>?
    ): OrderEvent {
        val detailsJson = details?.let { objectMapper.writeValueAsString(it) }
        val event = OrderEvent.sagaCompleted(orderId, sagaExecutionId, detailsJson)
        logger.info("Saga completed for order: {}", orderId)
        return recordEvent(event)
    }

    override suspend fun recordSagaFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        failedStep: String,
        errorInfo: ErrorInfo
    ): OrderEvent {
        val errorInfoJson = objectMapper.writeValueAsString(errorInfo)
        val event = OrderEvent.sagaFailed(orderId, sagaExecutionId, failedStep, errorInfoJson)
        logger.warn("Saga failed for order: {} at step {}", orderId, failedStep)
        return recordEvent(event)
    }

    override suspend fun recordRetryInitiated(
        orderId: UUID,
        sagaExecutionId: UUID,
        details: Map<String, Any>?
    ): OrderEvent {
        val detailsJson = details?.let { objectMapper.writeValueAsString(it) }
        val event = OrderEvent.retryInitiated(orderId, sagaExecutionId, detailsJson)
        logger.info("Retry initiated for order: {}", orderId)
        return recordEvent(event)
    }

    override suspend fun recordOrderCompleted(orderId: UUID, sagaExecutionId: UUID): OrderEvent {
        val event = OrderEvent.orderCompleted(orderId, sagaExecutionId)
        logger.info("Order completed: {}", orderId)
        return recordEvent(event)
    }

    @Observed(name = "order.event.get", contextualName = "get-events")
    override suspend fun getEventsForOrder(orderId: UUID): List<OrderEvent> {
        logger.debug("Fetching events for order: {}", orderId)
        return orderEventRepository.findByOrderIdOrderByTimestampAsc(orderId)
    }

    override suspend fun getEventsSince(orderId: UUID, since: Instant): List<OrderEvent> {
        logger.debug("Fetching events for order {} since {}", orderId, since)
        return orderEventRepository.findByOrderIdAndTimestampAfterOrderByTimestampAsc(orderId, since)
    }

    override suspend fun getLatestEvent(orderId: UUID): OrderEvent? {
        return orderEventRepository.findLatestByOrderId(orderId)
    }
}
