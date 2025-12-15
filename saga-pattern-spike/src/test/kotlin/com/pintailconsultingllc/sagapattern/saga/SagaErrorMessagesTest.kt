package com.pintailconsultingllc.sagapattern.saga

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SagaErrorMessages.
 */
@Tag("unit")
class SagaErrorMessagesTest {

    @Test
    fun `noItemsToReserve returns expected message`() {
        val message = SagaErrorMessages.noItemsToReserve()
        assertEquals("Cannot reserve inventory: order has no items", message)
    }

    @Test
    fun `stepExecutionFailed includes step name and reason`() {
        val message = SagaErrorMessages.stepExecutionFailed("TestStep", "connection timeout")
        assertTrue(message.contains("TestStep"))
        assertTrue(message.contains("connection timeout"))
    }

    @Test
    fun `stepExecutionFailed handles null reason`() {
        val message = SagaErrorMessages.stepExecutionFailed("TestStep", null)
        assertTrue(message.contains("TestStep"))
        assertTrue(message.contains("unknown reason"))
    }

    @Test
    fun `stepUnexpectedError includes step name and reason`() {
        val message = SagaErrorMessages.stepUnexpectedError("TestStep", "NPE")
        assertTrue(message.contains("TestStep"))
        assertTrue(message.contains("NPE"))
    }

    @Test
    fun `compensationFailed includes step name and reason`() {
        val message = SagaErrorMessages.compensationFailed("TestStep", "service unavailable")
        assertTrue(message.contains("TestStep"))
        assertTrue(message.contains("service unavailable"))
    }

    @Test
    fun `compensationFailed handles null reason`() {
        val message = SagaErrorMessages.compensationFailed("TestStep", null)
        assertTrue(message.contains("TestStep"))
        assertTrue(message.contains("unknown reason"))
    }

    @Test
    fun `inventoryReleased includes reservation id`() {
        val message = SagaErrorMessages.inventoryReleased("res-123")
        assertTrue(message.contains("res-123"))
    }

    @Test
    fun `paymentVoided includes authorization id`() {
        val message = SagaErrorMessages.paymentVoided("auth-456")
        assertTrue(message.contains("auth-456"))
    }

    @Test
    fun `shipmentCancelled includes shipment id`() {
        val message = SagaErrorMessages.shipmentCancelled("ship-789")
        assertTrue(message.contains("ship-789"))
    }

    @Test
    fun `unknownError returns default message when context is null`() {
        val message = SagaErrorMessages.unknownError()
        assertEquals("An unexpected error occurred", message)
    }

    @Test
    fun `unknownError includes context when provided`() {
        val message = SagaErrorMessages.unknownError("step execution")
        assertTrue(message.contains("step execution"))
    }

    @Test
    fun `error codes are defined`() {
        assertEquals("NO_ITEMS", SagaErrorMessages.Codes.NO_ITEMS)
        assertEquals("STEP_FAILED", SagaErrorMessages.Codes.STEP_FAILED)
        assertEquals("UNEXPECTED_ERROR", SagaErrorMessages.Codes.UNEXPECTED_ERROR)
        assertEquals("COMPENSATION_FAILED", SagaErrorMessages.Codes.COMPENSATION_FAILED)
        assertEquals("RETRY_NOT_ELIGIBLE", SagaErrorMessages.Codes.RETRY_NOT_ELIGIBLE)
        assertEquals("CONTEXT_BUILD_FAILED", SagaErrorMessages.Codes.CONTEXT_BUILD_FAILED)
        assertEquals("SERVICE_UNAVAILABLE", SagaErrorMessages.Codes.SERVICE_UNAVAILABLE)
        assertEquals("VALIDATION_FAILED", SagaErrorMessages.Codes.VALIDATION_FAILED)
    }
}
