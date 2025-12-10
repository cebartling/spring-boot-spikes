package com.pintailconsultingllc.sagapattern.saga

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for CompensationResult.
 */
class CompensationResultTest {

    @Test
    fun `success factory creates successful result`() {
        val result = CompensationResult.success("Compensation completed")

        assertTrue(result.success)
        assertEquals("Compensation completed", result.message)
    }

    @Test
    fun `success without message works`() {
        val result = CompensationResult.success()

        assertTrue(result.success)
        assertNull(result.message)
    }

    @Test
    fun `failure factory creates failed result`() {
        val result = CompensationResult.failure("Compensation failed")

        assertFalse(result.success)
        assertEquals("Compensation failed", result.message)
    }
}
