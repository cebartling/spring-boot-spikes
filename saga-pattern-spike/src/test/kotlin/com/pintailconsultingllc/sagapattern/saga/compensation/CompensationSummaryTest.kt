package com.pintailconsultingllc.sagapattern.saga.compensation

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for CompensationSummary.
 */
@Tag("unit")
class CompensationSummaryTest {

    @Test
    fun `noCompensationNeeded creates summary with empty steps`() {
        val summary = CompensationSummary.noCompensationNeeded(
            failedStep = "Inventory Reservation",
            failureReason = "Insufficient stock"
        )

        assertEquals("Inventory Reservation", summary.failedStep)
        assertEquals("Insufficient stock", summary.failureReason)
        assertTrue(summary.compensatedSteps.isEmpty())
        assertTrue(summary.failedCompensations.isEmpty())
        assertTrue(summary.stepResults.isEmpty())
        assertTrue(summary.allCompensationsSuccessful)
        assertFalse(summary.hasCompensatedSteps)
        assertEquals(0, summary.totalStepsToCompensate)
    }

    @Test
    fun `fromResults with all successful compensations`() {
        val stepResults = mapOf(
            "Payment Processing" to CompensationResult.success("Voided authorization"),
            "Inventory Reservation" to CompensationResult.success("Released reservation")
        )

        val summary = CompensationSummary.fromResults(
            failedStep = "Shipping Arrangement",
            failureReason = "Invalid address",
            stepResults = stepResults
        )

        assertEquals("Shipping Arrangement", summary.failedStep)
        assertEquals("Invalid address", summary.failureReason)
        assertEquals(2, summary.compensatedSteps.size)
        assertTrue(summary.compensatedSteps.containsAll(listOf("Payment Processing", "Inventory Reservation")))
        assertTrue(summary.failedCompensations.isEmpty())
        assertTrue(summary.allCompensationsSuccessful)
        assertTrue(summary.hasCompensatedSteps)
        assertEquals(2, summary.totalStepsToCompensate)
    }

    @Test
    fun `fromResults with some failed compensations`() {
        val stepResults = mapOf(
            "Payment Processing" to CompensationResult.failure("Service unavailable"),
            "Inventory Reservation" to CompensationResult.success("Released reservation")
        )

        val summary = CompensationSummary.fromResults(
            failedStep = "Shipping Arrangement",
            failureReason = "Invalid address",
            stepResults = stepResults
        )

        assertEquals(1, summary.compensatedSteps.size)
        assertTrue(summary.compensatedSteps.contains("Inventory Reservation"))
        assertEquals(1, summary.failedCompensations.size)
        assertTrue(summary.failedCompensations.contains("Payment Processing"))
        assertFalse(summary.allCompensationsSuccessful)
        assertTrue(summary.hasCompensatedSteps)
        assertEquals(2, summary.totalStepsToCompensate)
    }

    @Test
    fun `fromResults with all failed compensations`() {
        val stepResults = mapOf(
            "Payment Processing" to CompensationResult.failure("Network error"),
            "Inventory Reservation" to CompensationResult.failure("Service unavailable")
        )

        val summary = CompensationSummary.fromResults(
            failedStep = "Shipping Arrangement",
            failureReason = "Invalid address",
            stepResults = stepResults
        )

        assertTrue(summary.compensatedSteps.isEmpty())
        assertEquals(2, summary.failedCompensations.size)
        assertFalse(summary.allCompensationsSuccessful)
        assertFalse(summary.hasCompensatedSteps)
        assertEquals(2, summary.totalStepsToCompensate)
    }

    @Test
    fun `stepResults contains individual compensation outcomes`() {
        val stepResults = mapOf(
            "Payment Processing" to CompensationResult.success("Voided authorization PAY-123")
        )

        val summary = CompensationSummary.fromResults(
            failedStep = "Shipping Arrangement",
            failureReason = "Invalid address",
            stepResults = stepResults
        )

        val paymentResult = summary.stepResults["Payment Processing"]
        assertTrue(paymentResult?.success == true)
        assertEquals("Voided authorization PAY-123", paymentResult?.message)
    }
}
