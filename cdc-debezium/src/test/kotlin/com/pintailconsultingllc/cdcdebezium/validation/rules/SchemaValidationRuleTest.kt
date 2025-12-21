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

class SchemaValidationRuleTest {

    private lateinit var rule: SchemaValidationRule

    @BeforeEach
    fun setUp() {
        rule = SchemaValidationRule()
    }

    @Test
    fun `has correct rule id`() {
        assertEquals("SCHEMA_001", rule.ruleId)
    }

    @Test
    fun `does not continue on failure`() {
        assertFalse(rule.continueOnFailure)
    }

    @Nested
    inner class ValidEvents {

        @Test
        fun `passes validation for valid event`() {
            val event = createEvent(email = "valid@company.com", status = "active")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Schema validation passed", result.message)
        }

        @Test
        fun `passes validation for delete event with null email`() {
            val event = createEvent(email = null, status = null, deleted = "true")

            val result = rule.validate(event)

            assertTrue(result.valid)
        }

        @Test
        fun `passes validation for delete event with operation d`() {
            val event = createEvent(email = null, status = null, operation = "d")

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }

    @Nested
    inner class InvalidEmail {

        @Test
        fun `fails validation when email is null for non-delete event`() {
            val event = createEvent(email = null, status = "active")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("email is required for non-delete events"))
        }

        @Test
        fun `fails validation when email is blank for non-delete event`() {
            val event = createEvent(email = "   ", status = "active")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("email is required for non-delete events"))
        }

        @ParameterizedTest
        @ValueSource(strings = ["invalid-email", "no-at-sign", "@nodomain", "missing@.com", "test@", "spaces in@email.com"])
        fun `fails validation for invalid email format`(invalidEmail: String) {
            val event = createEvent(email = invalidEmail, status = "active")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.any { it.contains("email format is invalid") })
        }
    }

    @Nested
    inner class InvalidStatus {

        @Test
        fun `fails validation when status is null for non-delete event`() {
            val event = createEvent(email = "valid@company.com", status = null)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("status is required for non-delete events"))
        }

        @Test
        fun `fails validation when status is blank for non-delete event`() {
            val event = createEvent(email = "valid@company.com", status = "   ")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("status is required for non-delete events"))
        }
    }

    @Nested
    inner class MultipleErrors {

        @Test
        fun `collects all errors when multiple validations fail`() {
            val event = createEvent(email = null, status = null)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertEquals(2, errors.size)
            assertTrue(errors.contains("email is required for non-delete events"))
            assertTrue(errors.contains("status is required for non-delete events"))
        }
    }

    @Nested
    inner class ValidEmailFormats {

        @ParameterizedTest
        @ValueSource(strings = ["user@example.com", "test.user@company.org", "user+tag@domain.co", "a@b.io"])
        fun `accepts valid email formats`(validEmail: String) {
            val event = createEvent(email = validEmail, status = "active")

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }
}
