package com.pintailconsultingllc.sagapattern.history

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Unit tests for OrderEvent factory methods.
 */
class OrderEventTest {

    private val testOrderId = UUID.randomUUID()
    private val testSagaExecutionId = UUID.randomUUID()

    @Test
    fun `orderCreated creates correct event`() {
        val event = OrderEvent.orderCreated(testOrderId)

        assertEquals(testOrderId, event.orderId)
        assertEquals(OrderEventType.ORDER_CREATED, event.eventType)
        assertEquals(EventOutcome.SUCCESS, event.outcome)
        assertNull(event.sagaExecutionId)
        assertNull(event.stepName)
    }

    @Test
    fun `sagaStarted creates correct event`() {
        val event = OrderEvent.sagaStarted(testOrderId, testSagaExecutionId)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.SAGA_STARTED, event.eventType)
        assertEquals(EventOutcome.NEUTRAL, event.outcome)
    }

    @Test
    fun `stepStarted creates correct event`() {
        val stepName = "Payment Processing"
        val event = OrderEvent.stepStarted(testOrderId, testSagaExecutionId, stepName)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.STEP_STARTED, event.eventType)
        assertEquals(stepName, event.stepName)
        assertEquals(EventOutcome.NEUTRAL, event.outcome)
    }

    @Test
    fun `stepCompleted creates correct event`() {
        val stepName = "Payment Processing"
        val details = """{"amount": 5000}"""
        val event = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, stepName, details)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.STEP_COMPLETED, event.eventType)
        assertEquals(stepName, event.stepName)
        assertEquals(EventOutcome.SUCCESS, event.outcome)
        assertEquals(details, event.details)
    }

    @Test
    fun `stepFailed creates correct event`() {
        val stepName = "Payment Processing"
        val errorInfo = """{"code": "PAYMENT_DECLINED"}"""
        val event = OrderEvent.stepFailed(testOrderId, testSagaExecutionId, stepName, errorInfo)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.STEP_FAILED, event.eventType)
        assertEquals(stepName, event.stepName)
        assertEquals(EventOutcome.FAILED, event.outcome)
        assertEquals(errorInfo, event.errorInfo)
    }

    @Test
    fun `compensationStarted creates correct event`() {
        val failedStep = "Payment Processing"
        val event = OrderEvent.compensationStarted(testOrderId, testSagaExecutionId, failedStep)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.COMPENSATION_STARTED, event.eventType)
        assertEquals(failedStep, event.stepName)
        assertEquals(EventOutcome.NEUTRAL, event.outcome)
    }

    @Test
    fun `stepCompensated creates correct event`() {
        val stepName = "Inventory Reservation"
        val event = OrderEvent.stepCompensated(testOrderId, testSagaExecutionId, stepName)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.STEP_COMPENSATED, event.eventType)
        assertEquals(stepName, event.stepName)
        assertEquals(EventOutcome.COMPENSATED, event.outcome)
    }

    @Test
    fun `sagaCompleted creates correct event`() {
        val details = """{"confirmationNumber": "CONF-12345"}"""
        val event = OrderEvent.sagaCompleted(testOrderId, testSagaExecutionId, details)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.SAGA_COMPLETED, event.eventType)
        assertEquals(EventOutcome.SUCCESS, event.outcome)
        assertEquals(details, event.details)
    }

    @Test
    fun `sagaFailed creates correct event`() {
        val failedStep = "Payment Processing"
        val errorInfo = """{"code": "PAYMENT_DECLINED"}"""
        val event = OrderEvent.sagaFailed(testOrderId, testSagaExecutionId, failedStep, errorInfo)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.SAGA_FAILED, event.eventType)
        assertEquals(failedStep, event.stepName)
        assertEquals(EventOutcome.FAILED, event.outcome)
        assertEquals(errorInfo, event.errorInfo)
    }

    @Test
    fun `retryInitiated creates correct event`() {
        val details = """{"attemptNumber": 2}"""
        val event = OrderEvent.retryInitiated(testOrderId, testSagaExecutionId, details)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.RETRY_INITIATED, event.eventType)
        assertEquals(EventOutcome.NEUTRAL, event.outcome)
        assertEquals(details, event.details)
    }

    @Test
    fun `orderCompleted creates correct event`() {
        val event = OrderEvent.orderCompleted(testOrderId, testSagaExecutionId)

        assertEquals(testOrderId, event.orderId)
        assertEquals(testSagaExecutionId, event.sagaExecutionId)
        assertEquals(OrderEventType.ORDER_COMPLETED, event.eventType)
        assertEquals(EventOutcome.SUCCESS, event.outcome)
    }

    @Test
    fun `orderCancelled creates correct event`() {
        val event = OrderEvent.orderCancelled(testOrderId)

        assertEquals(testOrderId, event.orderId)
        assertEquals(OrderEventType.ORDER_CANCELLED, event.eventType)
        assertEquals(EventOutcome.NEUTRAL, event.outcome)
    }
}
