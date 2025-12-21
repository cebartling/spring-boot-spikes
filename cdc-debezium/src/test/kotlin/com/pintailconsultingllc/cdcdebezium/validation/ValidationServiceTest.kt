package com.pintailconsultingllc.cdcdebezium.validation

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.ValidationMetricsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationServiceTest {

    private lateinit var metricsService: ValidationMetricsService
    private lateinit var service: ValidationService

    @BeforeEach
    fun setUp() {
        metricsService = mockk(relaxed = true)
    }

    private fun createMockRule(
        ruleId: String,
        valid: Boolean,
        continueOnFailure: Boolean = false
    ): ValidationRule<CustomerCdcEvent> {
        return object : ValidationRule<CustomerCdcEvent> {
            override val ruleId = ruleId
            override val description = "Mock rule $ruleId"
            override val continueOnFailure = continueOnFailure

            override fun validate(event: CustomerCdcEvent): ValidationResult {
                return if (valid) {
                    ValidationResult.success(ruleId)
                } else {
                    ValidationResult.failure(ruleId, "Validation failed for $ruleId")
                }
            }
        }
    }

    @Nested
    inner class AllRulesPass {

        @Test
        fun `returns valid result when all rules pass`() {
            val rules = listOf(
                createMockRule("RULE_001", valid = true),
                createMockRule("RULE_002", valid = true)
            )
            service = ValidationService(rules, metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertTrue(result.valid)
            assertEquals(2, result.results.size)
            assertEquals(0, result.failureCount)
        }

        @Test
        fun `isValid returns true when validation passes`() {
            val rules = listOf(createMockRule("RULE_001", valid = true))
            service = ValidationService(rules, metricsService)
            val event = createEvent()

            assertTrue(service.isValid(event))
        }
    }

    @Nested
    inner class ShortCircuiting {

        @Test
        fun `stops on first failure when continueOnFailure is false`() {
            var rule2Called = false
            val rule1 = createMockRule("RULE_001", valid = false, continueOnFailure = false)
            val rule2 = object : ValidationRule<CustomerCdcEvent> {
                override val ruleId = "RULE_002"
                override val description = "Should not be called"
                override fun validate(event: CustomerCdcEvent): ValidationResult {
                    rule2Called = true
                    return ValidationResult.success(ruleId)
                }
            }

            service = ValidationService(listOf(rule1, rule2), metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertFalse(result.valid)
            assertEquals(1, result.results.size)
            assertFalse(rule2Called)
        }

        @Test
        fun `continues when rule fails with continueOnFailure true`() {
            var rule2Called = false
            val rule1 = createMockRule("RULE_001", valid = false, continueOnFailure = true)
            val rule2 = object : ValidationRule<CustomerCdcEvent> {
                override val ruleId = "RULE_002"
                override val description = "Should be called"
                override fun validate(event: CustomerCdcEvent): ValidationResult {
                    rule2Called = true
                    return ValidationResult.success(ruleId)
                }
            }

            service = ValidationService(listOf(rule1, rule2), metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertFalse(result.valid)
            assertEquals(2, result.results.size)
            assertTrue(rule2Called)
        }
    }

    @Nested
    inner class ExceptionHandling {

        @Test
        fun `handles rule exceptions gracefully`() {
            val throwingRule = object : ValidationRule<CustomerCdcEvent> {
                override val ruleId = "RULE_001"
                override val description = "Throws exception"
                override fun validate(event: CustomerCdcEvent): ValidationResult {
                    throw RuntimeException("Unexpected error")
                }
            }

            service = ValidationService(listOf(throwingRule), metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertFalse(result.valid)
            assertEquals(1, result.results.size)
            assertTrue(result.failures.first().message?.contains("Rule execution failed") == true)
        }
    }

    @Nested
    inner class MetricsRecording {

        @Test
        fun `records metrics after validation`() {
            val rules = listOf(createMockRule("RULE_001", valid = true))
            service = ValidationService(rules, metricsService)
            val event = createEvent()

            service.validate(event)

            verify(exactly = 1) { metricsService.recordValidation(any(), any()) }
        }
    }

    @Nested
    inner class EmptyRules {

        @Test
        fun `returns valid result when no rules defined`() {
            service = ValidationService(emptyList(), metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertTrue(result.valid)
            assertEquals(0, result.results.size)
        }
    }

    @Nested
    inner class EventMetadata {

        @Test
        fun `includes event id in result`() {
            val rules = listOf(createMockRule("RULE_001", valid = true))
            service = ValidationService(rules, metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertEquals(event.id.toString(), result.eventId)
        }

        @Test
        fun `includes entity type in result`() {
            val rules = listOf(createMockRule("RULE_001", valid = true))
            service = ValidationService(rules, metricsService)
            val event = createEvent()

            val result = service.validate(event)

            assertEquals("customer", result.entityType)
        }
    }
}
