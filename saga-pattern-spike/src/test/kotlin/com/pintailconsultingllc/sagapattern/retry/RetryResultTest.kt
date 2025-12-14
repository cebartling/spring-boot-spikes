package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for RetryResult.
 */
@Tag("unit")
class RetryResultTest {

    private val testOrderId = UUID.randomUUID()
    private val testExecutionId = UUID.randomUUID()
    private val testRetryAttemptId = UUID.randomUUID()

    @Test
    fun `initiated creates successful retry result`() {
        val skippedSteps = listOf("Inventory Reservation")

        val result = RetryResult.initiated(
            orderId = testOrderId,
            executionId = testExecutionId,
            retryAttemptId = testRetryAttemptId,
            resumedFromStep = "Payment Processing",
            skippedSteps = skippedSteps
        )

        assertTrue(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals(testExecutionId, result.newExecutionId)
        assertEquals(testRetryAttemptId, result.retryAttemptId)
        assertEquals("Payment Processing", result.resumedFromStep)
        assertEquals(skippedSteps, result.skippedSteps)
        assertEquals(OrderStatus.PROCESSING, result.orderStatus)
        assertNull(result.failureReason)
    }

    @Test
    fun `rejected creates failed retry result with reason`() {
        val result = RetryResult.rejected(testOrderId, "Order not in retryable state")

        assertFalse(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals("Order not in retryable state", result.failureReason)
        assertNull(result.newExecutionId)
    }

    @Test
    fun `inCooldown creates result with next available time`() {
        val nextAvailable = Instant.now().plusSeconds(300)
        val result = RetryResult.inCooldown(testOrderId, nextAvailable)

        assertFalse(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals("Retry cooldown period not elapsed", result.failureReason)
        assertEquals(nextAvailable, result.nextRetryAvailableAt)
    }

    @Test
    fun `maxRetriesExceeded creates result indicating max retries`() {
        val result = RetryResult.maxRetriesExceeded(testOrderId)

        assertFalse(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals("Maximum retry attempts exceeded", result.failureReason)
    }

    @Test
    fun `retryInProgress creates result indicating active retry`() {
        val result = RetryResult.retryInProgress(testOrderId)

        assertFalse(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals("A retry is already in progress for this order", result.failureReason)
    }

    @Test
    fun `requiresAction creates result with required actions`() {
        val requiredActions = listOf(
            RequiredAction(
                action = ActionType.UPDATE_PAYMENT_METHOD,
                description = "Update payment method",
                completed = false
            )
        )

        val result = RetryResult.requiresAction(testOrderId, requiredActions)

        assertFalse(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals("Required actions must be completed before retry", result.failureReason)
        assertEquals(requiredActions, result.requiredActions)
    }

    @Test
    fun `requiresPriceAcknowledgment creates result with price changes`() {
        val priceChanges = listOf(
            PriceChange(
                itemId = "item-1",
                itemName = "Test Item",
                originalPriceInCents = 1000,
                newPriceInCents = 1200,
                changeId = "change-1"
            )
        )

        val result = RetryResult.requiresPriceAcknowledgment(testOrderId, priceChanges)

        assertFalse(result.success)
        assertEquals(testOrderId, result.orderId)
        assertEquals("Price changes must be acknowledged before retry", result.failureReason)
        assertEquals(priceChanges, result.priceChanges)
    }
}

/**
 * Unit tests for PriceChange.
 */
@Tag("unit")
class PriceChangeTest {

    @Test
    fun `differenceInCents calculates price increase correctly`() {
        val priceChange = PriceChange(
            itemId = "item-1",
            itemName = "Test Item",
            originalPriceInCents = 1000,
            newPriceInCents = 1500,
            changeId = "change-1"
        )

        assertEquals(500, priceChange.differenceInCents)
        assertTrue(priceChange.isIncrease)
    }

    @Test
    fun `differenceInCents calculates price decrease correctly`() {
        val priceChange = PriceChange(
            itemId = "item-1",
            itemName = "Test Item",
            originalPriceInCents = 1500,
            newPriceInCents = 1000,
            changeId = "change-1"
        )

        assertEquals(-500, priceChange.differenceInCents)
        assertFalse(priceChange.isIncrease)
    }

    @Test
    fun `no price change returns zero difference`() {
        val priceChange = PriceChange(
            itemId = "item-1",
            itemName = "Test Item",
            originalPriceInCents = 1000,
            newPriceInCents = 1000,
            changeId = "change-1"
        )

        assertEquals(0, priceChange.differenceInCents)
        assertFalse(priceChange.isIncrease)
    }
}
