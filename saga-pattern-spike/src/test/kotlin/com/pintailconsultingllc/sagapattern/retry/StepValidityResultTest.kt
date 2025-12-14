package com.pintailconsultingllc.sagapattern.retry

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Unit tests for StepValidityResult.
 */
@Tag("unit")
class StepValidityResultTest {

    @Test
    fun `valid creates result indicating step is still valid`() {
        val result = StepValidityResult.valid()

        assertTrue(result.valid)
        assertNull(result.reason)
        assertFalse(result.canBeRefreshed)
    }

    @Test
    fun `expiredButRefreshable creates invalid result that can be refreshed`() {
        val result = StepValidityResult.expiredButRefreshable("Inventory reservation expired")

        assertFalse(result.valid)
        assertEquals("Inventory reservation expired", result.reason)
        assertTrue(result.canBeRefreshed)
    }

    @Test
    fun `invalidRequiresReExecution creates invalid result requiring re-execution`() {
        val result = StepValidityResult.invalidRequiresReExecution("Payment authorization no longer valid")

        assertFalse(result.valid)
        assertEquals("Payment authorization no longer valid", result.reason)
        assertFalse(result.canBeRefreshed)
    }
}
