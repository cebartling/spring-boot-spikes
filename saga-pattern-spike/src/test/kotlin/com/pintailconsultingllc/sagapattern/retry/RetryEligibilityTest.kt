package com.pintailconsultingllc.sagapattern.retry

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for RetryEligibility.
 */
@Tag("unit")
class RetryEligibilityTest {

    @Test
    fun `eligible creates eligible response with attempts remaining`() {
        val expiresAt = Instant.now().plusSeconds(3600)
        val eligibility = RetryEligibility.eligible(
            attemptsRemaining = 2,
            expiresAt = expiresAt
        )

        assertTrue(eligibility.eligible)
        assertEquals(2, eligibility.retryAttemptsRemaining)
        assertEquals(expiresAt, eligibility.expiresAt)
        assertNull(eligibility.reason)
        assertTrue(eligibility.blockers.isEmpty())
    }

    @Test
    fun `eligible with required actions`() {
        val requiredActions = listOf(
            RequiredAction(
                action = ActionType.UPDATE_PAYMENT_METHOD,
                description = "Please update your payment method",
                completed = false
            )
        )

        val eligibility = RetryEligibility.eligible(
            attemptsRemaining = 3,
            requiredActions = requiredActions
        )

        assertTrue(eligibility.eligible)
        assertEquals(1, eligibility.requiredActions.size)
        assertEquals(ActionType.UPDATE_PAYMENT_METHOD, eligibility.requiredActions[0].action)
    }

    @Test
    fun `ineligible creates response with reason and blockers`() {
        val blockers = listOf(
            RetryBlocker(
                type = BlockerType.FRAUD_DETECTED,
                message = "Fraud detected on this order",
                resolvable = false
            )
        )

        val eligibility = RetryEligibility.ineligible(
            reason = "Cannot retry due to fraud detection",
            blockers = blockers
        )

        assertFalse(eligibility.eligible)
        assertEquals("Cannot retry due to fraud detection", eligibility.reason)
        assertEquals(IneligibilityReason.OTHER, eligibility.ineligibilityReason)
        assertEquals(1, eligibility.blockers.size)
        assertEquals(BlockerType.FRAUD_DETECTED, eligibility.blockers[0].type)
    }

    @Test
    fun `inCooldown creates response with next available time`() {
        val nextAvailable = Instant.now().plusSeconds(300)
        val eligibility = RetryEligibility.inCooldown(
            nextAvailableAt = nextAvailable,
            attemptsRemaining = 2
        )

        assertFalse(eligibility.eligible)
        assertEquals("Retry cooldown period not elapsed", eligibility.reason)
        assertEquals(IneligibilityReason.IN_COOLDOWN, eligibility.ineligibilityReason)
        assertEquals(nextAvailable, eligibility.nextRetryAvailableAt)
        assertEquals(2, eligibility.retryAttemptsRemaining)
    }

    @Test
    fun `maxRetriesExceeded creates response with zero attempts`() {
        val eligibility = RetryEligibility.maxRetriesExceeded()

        assertFalse(eligibility.eligible)
        assertEquals("Maximum retry attempts exceeded", eligibility.reason)
        assertEquals(IneligibilityReason.MAX_RETRIES_EXCEEDED, eligibility.ineligibilityReason)
        assertEquals(0, eligibility.retryAttemptsRemaining)
    }

    @Test
    fun `retryInProgress creates response indicating active retry`() {
        val eligibility = RetryEligibility.retryInProgress()

        assertFalse(eligibility.eligible)
        assertEquals("A retry is already in progress", eligibility.reason)
        assertEquals(IneligibilityReason.RETRY_IN_PROGRESS, eligibility.ineligibilityReason)
    }
}

/**
 * Unit tests for RetryBlocker.
 */
@Tag("unit")
class RetryBlockerTest {

    @Test
    fun `blocker with fraud detection is not resolvable`() {
        val blocker = RetryBlocker(
            type = BlockerType.FRAUD_DETECTED,
            message = "Fraud detected",
            resolvable = false
        )

        assertEquals(BlockerType.FRAUD_DETECTED, blocker.type)
        assertEquals("Fraud detected", blocker.message)
        assertFalse(blocker.resolvable)
    }

    @Test
    fun `blocker with item unavailable is resolvable`() {
        val blocker = RetryBlocker(
            type = BlockerType.ITEM_UNAVAILABLE,
            message = "Item out of stock",
            resolvable = true
        )

        assertEquals(BlockerType.ITEM_UNAVAILABLE, blocker.type)
        assertTrue(blocker.resolvable)
    }
}

/**
 * Unit tests for RequiredAction.
 */
@Tag("unit")
class RequiredActionTest {

    @Test
    fun `required action starts as not completed`() {
        val action = RequiredAction(
            action = ActionType.UPDATE_PAYMENT_METHOD,
            description = "Update your payment method"
        )

        assertEquals(ActionType.UPDATE_PAYMENT_METHOD, action.action)
        assertEquals("Update your payment method", action.description)
        assertFalse(action.completed)
    }

    @Test
    fun `required action can be marked completed`() {
        val action = RequiredAction(
            action = ActionType.VERIFY_ADDRESS,
            description = "Verify your shipping address",
            completed = true
        )

        assertTrue(action.completed)
    }
}
