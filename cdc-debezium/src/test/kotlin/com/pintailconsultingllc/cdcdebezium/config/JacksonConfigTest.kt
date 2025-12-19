package com.pintailconsultingllc.cdcdebezium.config

import com.pintailconsultingllc.cdcdebezium.TestFixtures
import com.pintailconsultingllc.cdcdebezium.TestFixtures.TEST_INSTANT
import com.pintailconsultingllc.cdcdebezium.TestFixtures.TEST_SOURCE_TIMESTAMP
import com.pintailconsultingllc.cdcdebezium.TestFixtures.TEST_UUID
import com.pintailconsultingllc.cdcdebezium.TestFixtures.buildJson
import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JacksonConfigTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = JacksonConfig().objectMapper()
    }

    private fun deserialize(json: String): CustomerCdcEvent =
        objectMapper.readValue(json, CustomerCdcEvent::class.java)

    @Nested
    inner class DateTimeSerialization {

        @Test
        fun `serializes Instant as ISO-8601 string not timestamp`() {
            val event = createEvent(updatedAt = TEST_INSTANT)
            val json = objectMapper.writeValueAsString(event)

            assertTrue(json.contains("2024-01-15T10:30:00Z"))
            assertFalse(json.contains("1705315800"))
        }

        @Test
        fun `deserializes ISO-8601 string to Instant`() {
            val json = buildJson(updatedAt = "2024-01-15T10:30:00Z")
            val event = deserialize(json)

            assertEquals(TEST_INSTANT, event.updatedAt)
        }
    }

    @Nested
    inner class KotlinDataClassSupport {

        @Test
        fun `deserializes JSON to Kotlin data class`() {
            val json = buildJson()
            val event = deserialize(json)

            assertEquals(TEST_UUID, event.id)
            assertEquals(TestFixtures.TEST_EMAIL, event.email)
            assertEquals(TestFixtures.TEST_STATUS, event.status)
        }

        @Test
        fun `handles nullable fields correctly`() {
            val json = buildJson(email = null, status = null)
            val event = deserialize(json)

            assertNotNull(event.id)
            assertNull(event.email)
            assertNull(event.status)
            assertNull(event.updatedAt)
        }
    }

    @Nested
    inner class JsonPropertyAnnotations {

        @Test
        fun `deserializes __deleted to deleted field`() {
            val json = buildJson(deleted = "true")
            val event = deserialize(json)

            assertEquals("true", event.deleted)
            assertTrue(event.isDelete())
        }

        @Test
        fun `deserializes __op to operation field`() {
            val json = buildJson(operation = "d")
            val event = deserialize(json)

            assertEquals("d", event.operation)
            assertTrue(event.isDelete())
        }

        @Test
        fun `deserializes __source_ts_ms to sourceTimestamp field`() {
            val json = buildJson(sourceTimestamp = TEST_SOURCE_TIMESTAMP)
            val event = deserialize(json)

            assertEquals(TEST_SOURCE_TIMESTAMP, event.sourceTimestamp)
        }
    }

    @Nested
    inner class UnknownProperties {

        @Test
        fun `ignores unknown properties in JSON`() {
            val json = buildJson(
                extraFields = mapOf(
                    "unknown_field" to "should be ignored",
                    "another_unknown" to 12345
                )
            )
            val event = deserialize(json)

            assertNotNull(event)
            assertEquals(TestFixtures.TEST_EMAIL, event.email)
        }
    }

    @Nested
    inner class DebeziumCdcPayload {

        @Test
        fun `deserializes complete Debezium CDC insert event`() {
            val json = buildJson(
                email = "newuser@example.com",
                status = "pending",
                updatedAt = "2024-01-15T10:30:00Z",
                operation = "c",
                sourceTimestamp = TEST_SOURCE_TIMESTAMP
            )
            val event = deserialize(json)

            assertEquals(TEST_UUID, event.id)
            assertEquals("newuser@example.com", event.email)
            assertEquals("pending", event.status)
            assertEquals("c", event.operation)
            assertFalse(event.isDelete())
        }

        @Test
        fun `deserializes complete Debezium CDC delete event with rewrite`() {
            val json = buildJson(
                email = "deleted@example.com",
                updatedAt = "2024-01-15T10:30:00Z",
                deleted = "true",
                operation = "d",
                sourceTimestamp = TEST_SOURCE_TIMESTAMP
            )
            val event = deserialize(json)

            assertEquals(TEST_UUID, event.id)
            assertTrue(event.isDelete())
        }

        @Test
        fun `deserializes Debezium CDC snapshot event`() {
            val json = buildJson(
                email = "existing@example.com",
                updatedAt = "2024-01-10T08:00:00Z",
                operation = "r",
                sourceTimestamp = TEST_SOURCE_TIMESTAMP
            )
            val event = deserialize(json)

            assertEquals("r", event.operation)
            assertFalse(event.isDelete())
        }
    }
}
