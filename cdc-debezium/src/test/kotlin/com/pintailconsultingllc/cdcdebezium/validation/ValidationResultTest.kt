package com.pintailconsultingllc.cdcdebezium.validation

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationResultTest {

    @Nested
    inner class Success {

        @Test
        fun `creates successful result with rule id`() {
            val result = ValidationResult.success("RULE_001")

            assertTrue(result.valid)
            assertEquals("RULE_001", result.ruleId)
            assertNull(result.message)
            assertTrue(result.details.isEmpty())
        }

        @Test
        fun `creates successful result with message`() {
            val result = ValidationResult.success("RULE_001", "Validation passed")

            assertTrue(result.valid)
            assertEquals("Validation passed", result.message)
        }
    }

    @Nested
    inner class Failure {

        @Test
        fun `creates failure result with rule id and message`() {
            val result = ValidationResult.failure("RULE_001", "Validation failed")

            assertFalse(result.valid)
            assertEquals("RULE_001", result.ruleId)
            assertEquals("Validation failed", result.message)
            assertTrue(result.details.isEmpty())
        }

        @Test
        fun `creates failure result with details`() {
            val details = mapOf("errors" to listOf("error1", "error2"))
            val result = ValidationResult.failure("RULE_001", "Validation failed", details)

            assertEquals(details, result.details)
        }
    }
}

class AggregatedValidationResultTest {

    @Nested
    inner class FromResults {

        @Test
        fun `creates valid aggregated result when all results pass`() {
            val results = listOf(
                ValidationResult.success("RULE_001"),
                ValidationResult.success("RULE_002")
            )

            val aggregated = AggregatedValidationResult.fromResults(
                results = results,
                eventId = "test-id",
                entityType = "customer"
            )

            assertTrue(aggregated.valid)
            assertEquals("test-id", aggregated.eventId)
            assertEquals("customer", aggregated.entityType)
            assertEquals(2, aggregated.results.size)
            assertEquals(0, aggregated.failureCount)
            assertTrue(aggregated.failures.isEmpty())
        }

        @Test
        fun `creates invalid aggregated result when any result fails`() {
            val results = listOf(
                ValidationResult.success("RULE_001"),
                ValidationResult.failure("RULE_002", "Failed")
            )

            val aggregated = AggregatedValidationResult.fromResults(
                results = results,
                eventId = "test-id",
                entityType = "customer"
            )

            assertFalse(aggregated.valid)
            assertEquals(1, aggregated.failureCount)
            assertEquals("RULE_002", aggregated.failures.first().ruleId)
        }

        @Test
        fun `counts multiple failures correctly`() {
            val results = listOf(
                ValidationResult.failure("RULE_001", "Failed 1"),
                ValidationResult.success("RULE_002"),
                ValidationResult.failure("RULE_003", "Failed 3")
            )

            val aggregated = AggregatedValidationResult.fromResults(
                results = results,
                eventId = "test-id",
                entityType = "customer"
            )

            assertFalse(aggregated.valid)
            assertEquals(2, aggregated.failureCount)
            assertEquals(listOf("RULE_001", "RULE_003"), aggregated.failures.map { it.ruleId })
        }
    }
}
