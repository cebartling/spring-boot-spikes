package com.pintailconsultingllc.sagapattern.saga

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for StepResult.
 */
@Tag("unit")
class StepResultTest {

    @Test
    fun `success factory creates successful result`() {
        val data = mapOf("key" to "value", "count" to 42)

        val result = StepResult.success(data)

        assertTrue(result.success)
        assertEquals(data, result.data)
        assertNull(result.errorMessage)
        assertNull(result.errorCode)
    }

    @Test
    fun `success with empty data works`() {
        val result = StepResult.success()

        assertTrue(result.success)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `failure factory creates failed result`() {
        val result = StepResult.failure("Something went wrong", "ERR_001")

        assertFalse(result.success)
        assertEquals("Something went wrong", result.errorMessage)
        assertEquals("ERR_001", result.errorCode)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `failure without error code works`() {
        val result = StepResult.failure("Error message")

        assertFalse(result.success)
        assertEquals("Error message", result.errorMessage)
        assertNull(result.errorCode)
    }

    @Test
    fun `getData retrieves typed value correctly`() {
        val result = StepResult.success(mapOf(
            "stringValue" to "hello",
            "intValue" to 42,
            "listValue" to listOf("a", "b", "c")
        ))

        assertEquals("hello", result.getData<String>("stringValue"))
        assertEquals(42, result.getData<Int>("intValue"))
        assertEquals(listOf("a", "b", "c"), result.getData<List<String>>("listValue"))
    }

    @Test
    fun `getData returns null for missing key`() {
        val result = StepResult.success(mapOf("key" to "value"))

        assertNull(result.getData<String>("nonexistent"))
    }
}
