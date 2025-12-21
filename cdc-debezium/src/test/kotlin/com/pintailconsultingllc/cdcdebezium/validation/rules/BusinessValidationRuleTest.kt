package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessValidationRuleTest {

    private lateinit var rule: BusinessValidationRule

    @BeforeEach
    fun setUp() {
        rule = BusinessValidationRule()
    }

    @Test
    fun `has correct rule id`() {
        assertEquals("BUSINESS_001", rule.ruleId)
    }

    @Test
    fun `continues on failure`() {
        assertTrue(rule.continueOnFailure)
    }

    @Nested
    inner class DeleteEvents {

        @Test
        fun `bypasses validation for delete events with deleted flag`() {
            val event = createEvent(deleted = "true", status = "invalid-status")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Delete events bypass business rules", result.message)
        }

        @Test
        fun `bypasses validation for delete events with operation d`() {
            val event = createEvent(operation = "d", status = null)

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }

    @Nested
    inner class ValidStatuses {

        @ParameterizedTest
        @ValueSource(strings = ["active", "inactive", "pending", "suspended", "DELETED"])
        fun `accepts valid status values`(validStatus: String) {
            val event = createEvent(email = "user@company.com", status = validStatus)

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }

    @Nested
    inner class InvalidStatuses {

        @ParameterizedTest
        @ValueSource(strings = ["unknown", "invalid", "ACTIVE", "Active", ""])
        fun `rejects invalid status values`(invalidStatus: String) {
            val event = createEvent(email = "user@company.com", status = invalidStatus)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.any { it.contains("Invalid status: $invalidStatus") })
        }
    }

    @Nested
    inner class TestEmailWarnings {

        @Test
        fun `adds warning for test com domain`() {
            val event = createEvent(email = "user@test.com", status = "active")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertTrue(result.message?.contains("warnings") == true)
        }

        @Test
        fun `adds warning for example com domain`() {
            val event = createEvent(email = "user@example.com", status = "active")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertTrue(result.message?.contains("warnings") == true)
        }

        @Test
        fun `no warning for regular domain`() {
            val event = createEvent(email = "user@company.com", status = "active")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Business validation passed", result.message)
        }
    }

    @Nested
    inner class NullStatus {

        @Test
        fun `passes validation when status is null`() {
            val event = createEvent(email = "user@company.com", status = null)

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }
}
