package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createAddressEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressValidationRuleTest {

    private lateinit var rule: AddressValidationRule

    @BeforeEach
    fun setUp() {
        rule = AddressValidationRule()
    }

    @Test
    fun `has correct rule id`() {
        assertEquals("ADDRESS_001", rule.ruleId)
    }

    @Test
    fun `does not continue on failure`() {
        assertFalse(rule.continueOnFailure)
    }

    @Nested
    inner class ValidEvents {

        @Test
        fun `passes validation for valid event`() {
            val event = createAddressEvent()

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Address validation passed", result.message)
        }

        @Test
        fun `passes validation for delete event with null fields`() {
            val event = createAddressEvent(
                street = null,
                city = null,
                type = null,
                postalCode = null,
                deleted = "true"
            )

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Delete events bypass address validation", result.message)
        }

        @Test
        fun `passes validation for delete event with operation d`() {
            val event = createAddressEvent(
                street = null,
                city = null,
                type = null,
                postalCode = null,
                operation = "d"
            )

            val result = rule.validate(event)

            assertTrue(result.valid)
        }

        @ParameterizedTest
        @ValueSource(strings = ["billing", "shipping", "home", "work"])
        fun `accepts valid address types`(addressType: String) {
            val event = createAddressEvent(type = addressType)

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }

    @Nested
    inner class InvalidStreet {

        @Test
        fun `fails validation when street is null for non-delete event`() {
            val event = createAddressEvent(street = null)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("street is required for non-delete events"))
        }

        @Test
        fun `fails validation when street is blank for non-delete event`() {
            val event = createAddressEvent(street = "   ")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("street is required for non-delete events"))
        }
    }

    @Nested
    inner class InvalidCity {

        @Test
        fun `fails validation when city is null for non-delete event`() {
            val event = createAddressEvent(city = null)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("city is required for non-delete events"))
        }

        @Test
        fun `fails validation when city is blank for non-delete event`() {
            val event = createAddressEvent(city = "   ")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("city is required for non-delete events"))
        }
    }

    @Nested
    inner class InvalidPostalCode {

        @Test
        fun `fails validation when postalCode is null for non-delete event`() {
            val event = createAddressEvent(postalCode = null)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("postalCode is required for non-delete events"))
        }

        @Test
        fun `fails validation when postalCode is blank for non-delete event`() {
            val event = createAddressEvent(postalCode = "   ")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("postalCode is required for non-delete events"))
        }
    }

    @Nested
    inner class InvalidType {

        @Test
        fun `fails validation when type is null for non-delete event`() {
            val event = createAddressEvent(type = null)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.contains("type is required for non-delete events"))
        }

        @Test
        fun `fails validation when type is invalid for non-delete event`() {
            val event = createAddressEvent(type = "invalid-type")

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.any { it.contains("Invalid address type") })
        }
    }

    @Nested
    inner class PostalCodeFormat {

        @ParameterizedTest
        @ValueSource(strings = ["12345", "12345-6789"])
        fun `accepts valid US postal codes`(postalCode: String) {
            val event = createAddressEvent(postalCode = postalCode, country = "USA")

            val result = rule.validate(event)

            assertTrue(result.valid)
        }

        @Test
        fun `warns for invalid US postal code format`() {
            val event = createAddressEvent(postalCode = "invalid", country = "USA")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertTrue(result.message?.contains("warnings") == true)
        }

        @ParameterizedTest
        @ValueSource(strings = ["A1A 1A1", "A1A-1A1", "A1A1A1"])
        fun `accepts valid Canadian postal codes`(postalCode: String) {
            val event = createAddressEvent(postalCode = postalCode, country = "CANADA")

            val result = rule.validate(event)

            assertTrue(result.valid)
        }

        @Test
        fun `warns for invalid Canadian postal code format`() {
            val event = createAddressEvent(postalCode = "12345", country = "CANADA")

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertTrue(result.message?.contains("warnings") == true)
        }
    }

    @Nested
    inner class MultipleErrors {

        @Test
        fun `collects all errors when multiple validations fail`() {
            val event = createAddressEvent(
                street = null,
                city = null,
                postalCode = null,
                type = null
            )

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertEquals(4, errors.size)
            assertTrue(errors.contains("street is required for non-delete events"))
            assertTrue(errors.contains("city is required for non-delete events"))
            assertTrue(errors.contains("postalCode is required for non-delete events"))
            assertTrue(errors.contains("type is required for non-delete events"))
        }
    }
}
