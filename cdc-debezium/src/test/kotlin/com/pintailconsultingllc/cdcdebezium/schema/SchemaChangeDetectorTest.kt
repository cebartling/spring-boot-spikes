package com.pintailconsultingllc.cdcdebezium.schema

import com.pintailconsultingllc.cdcdebezium.metrics.SchemaMetricsService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaChangeDetectorTest {

    private lateinit var schemaMetrics: SchemaMetricsService
    private lateinit var detector: SchemaChangeDetector

    @BeforeEach
    fun setUp() {
        schemaMetrics = mockk(relaxed = true)
        val objectMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .build()
        detector = SchemaChangeDetector(objectMapper, schemaMetrics)
        detector.resetTracking()
    }

    @Nested
    inner class NewFieldDetection {

        @Test
        fun `should detect new field in customer event`() {
            val jsonWithNewField = """
                {
                    "id": "123e4567-e89b-12d3-a456-426614174000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": "2024-01-15T10:30:00Z",
                    "phone_number": "+1-555-1234"
                }
            """.trimIndent()

            val changes = detector.detectChanges(
                entityType = "customer",
                rawJson = jsonWithNewField,
                kafkaOffset = 100,
                kafkaPartition = 0
            )

            assertEquals(1, changes.size)
            assertEquals(SchemaChangeType.NEW_FIELD, changes[0].changeType)
            assertEquals("phone_number", changes[0].fieldName)
            assertEquals("customer", changes[0].entityType)

            verify { schemaMetrics.recordSchemaChange("customer", SchemaChangeType.NEW_FIELD, "phone_number") }
        }

        @Test
        fun `should detect multiple new fields`() {
            val jsonWithMultipleNewFields = """
                {
                    "id": "123e4567-e89b-12d3-a456-426614174000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": "2024-01-15T10:30:00Z",
                    "phone_number": "+1-555-1234",
                    "middle_name": "James",
                    "loyalty_points": 500
                }
            """.trimIndent()

            val changes = detector.detectChanges(
                entityType = "customer",
                rawJson = jsonWithMultipleNewFields,
                kafkaOffset = 100,
                kafkaPartition = 0
            )

            assertEquals(3, changes.size)
            val fieldNames = changes.map { it.fieldName }.toSet()
            assertTrue(fieldNames.contains("phone_number"))
            assertTrue(fieldNames.contains("middle_name"))
            assertTrue(fieldNames.contains("loyalty_points"))
        }

        @Test
        fun `should not detect known fields as new`() {
            val jsonWithKnownFields = """
                {
                    "id": "123e4567-e89b-12d3-a456-426614174000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": "2024-01-15T10:30:00Z"
                }
            """.trimIndent()

            val changes = detector.detectChanges(
                entityType = "customer",
                rawJson = jsonWithKnownFields,
                kafkaOffset = 100,
                kafkaPartition = 0
            )

            assertTrue(changes.isEmpty())
        }
    }

    @Nested
    inner class SchemaVersionTracking {

        @Test
        fun `should track schema version for entity type`() {
            val version = detector.getCurrentSchema("customer")

            assertEquals("customer", version.entityType)
            assertTrue(version.fields.contains("email"))
            assertTrue(version.fields.contains("status"))
        }

        @Test
        fun `should update known fields after detecting new field`() {
            val jsonWithNewField = """
                {
                    "id": "123e4567-e89b-12d3-a456-426614174000",
                    "email": "test@example.com",
                    "new_field": "value"
                }
            """.trimIndent()

            detector.detectChanges("customer", jsonWithNewField, 100, 0)

            val knownFields = detector.getKnownFields("customer")
            assertTrue(knownFields.contains("new_field"))
        }

        @Test
        fun `should not detect same new field twice`() {
            val jsonWithNewField = """
                {
                    "id": "123e4567-e89b-12d3-a456-426614174000",
                    "email": "test@example.com",
                    "new_field": "value"
                }
            """.trimIndent()

            val firstChanges = detector.detectChanges("customer", jsonWithNewField, 100, 0)
            val secondChanges = detector.detectChanges("customer", jsonWithNewField, 101, 0)

            assertEquals(1, firstChanges.size)
            assertTrue(secondChanges.isEmpty())
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `should handle invalid JSON gracefully`() {
            val invalidJson = "{ invalid json }"

            val changes = detector.detectChanges(
                entityType = "customer",
                rawJson = invalidJson,
                kafkaOffset = 100,
                kafkaPartition = 0
            )

            assertEquals(1, changes.size)
            assertEquals(SchemaChangeType.UNKNOWN, changes[0].changeType)

            verify { schemaMetrics.recordSchemaValidationError("customer", any()) }
        }

        @Test
        fun `should handle empty JSON object`() {
            val emptyJson = "{}"

            val changes = detector.detectChanges(
                entityType = "customer",
                rawJson = emptyJson,
                kafkaOffset = 100,
                kafkaPartition = 0
            )

            assertTrue(changes.isEmpty())
        }
    }

    @Nested
    inner class MultiEntitySupport {

        @Test
        fun `should track schema changes independently per entity type`() {
            val customerJson = """
                {
                    "id": "123",
                    "email": "test@example.com",
                    "customer_new_field": "value"
                }
            """.trimIndent()

            val addressJson = """
                {
                    "id": "456",
                    "customer_id": "123",
                    "street": "123 Main St",
                    "address_new_field": "value"
                }
            """.trimIndent()

            val customerChanges = detector.detectChanges("customer", customerJson, 100, 0)
            val addressChanges = detector.detectChanges("address", addressJson, 101, 0)

            assertEquals(1, customerChanges.size)
            assertEquals("customer_new_field", customerChanges[0].fieldName)

            assertEquals(1, addressChanges.size)
            assertEquals("address_new_field", addressChanges[0].fieldName)
        }
    }

    @Nested
    inner class ResetTracking {

        @Test
        fun `should reset all tracking when resetTracking is called`() {
            val jsonWithNewField = """
                {
                    "id": "123",
                    "email": "test@example.com",
                    "new_field": "value"
                }
            """.trimIndent()

            detector.detectChanges("customer", jsonWithNewField, 100, 0)
            detector.resetTracking()

            val changesAfterReset = detector.detectChanges("customer", jsonWithNewField, 101, 0)
            assertEquals(1, changesAfterReset.size)
            assertEquals("new_field", changesAfterReset[0].fieldName)
        }
    }
}
