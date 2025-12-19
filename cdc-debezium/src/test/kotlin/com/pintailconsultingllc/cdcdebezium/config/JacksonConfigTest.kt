package com.pintailconsultingllc.cdcdebezium.config

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
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

    @Nested
    inner class DateTimeSerialization {

        @Test
        fun `serializes Instant as ISO-8601 string not timestamp`() {
            val instant = Instant.parse("2024-01-15T10:30:00Z")
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "test@example.com",
                status = "active",
                updatedAt = instant
            )

            val json = objectMapper.writeValueAsString(event)

            assertTrue(json.contains("2024-01-15T10:30:00Z"))
            assertFalse(json.contains("1705315800"))
        }

        @Test
        fun `deserializes ISO-8601 string to Instant`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": "2024-01-15T10:30:00Z"
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals(Instant.parse("2024-01-15T10:30:00Z"), event.updatedAt)
        }
    }

    @Nested
    inner class KotlinDataClassSupport {

        @Test
        fun `deserializes JSON to Kotlin data class`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": null
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), event.id)
            assertEquals("test@example.com", event.email)
            assertEquals("active", event.status)
        }

        @Test
        fun `handles nullable fields correctly`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": null,
                    "status": null,
                    "updated_at": null
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertNotNull(event.id)
            assertNull(event.email)
            assertNull(event.status)
            assertNull(event.updatedAt)
        }
    }

    @Nested
    inner class JsonPropertyAnnotations {

        @Test
        fun `deserializes updated_at to updatedAt field`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": "2024-01-15T10:30:00Z"
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertNotNull(event.updatedAt)
        }

        @Test
        fun `deserializes __deleted to deleted field`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": null,
                    "__deleted": "true"
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals("true", event.deleted)
            assertTrue(event.isDelete())
        }

        @Test
        fun `deserializes __op to operation field`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": null,
                    "__op": "d"
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals("d", event.operation)
            assertTrue(event.isDelete())
        }

        @Test
        fun `deserializes __source_ts_ms to sourceTimestamp field`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": null,
                    "__source_ts_ms": 1705315800000
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals(1705315800000L, event.sourceTimestamp)
        }
    }

    @Nested
    inner class UnknownProperties {

        @Test
        fun `ignores unknown properties in JSON`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "test@example.com",
                    "status": "active",
                    "updated_at": null,
                    "unknown_field": "should be ignored",
                    "another_unknown": 12345
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertNotNull(event)
            assertEquals("test@example.com", event.email)
        }
    }

    @Nested
    inner class DebeziumCdcPayload {

        @Test
        fun `deserializes complete Debezium CDC insert event`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "newuser@example.com",
                    "status": "pending",
                    "updated_at": "2024-01-15T10:30:00Z",
                    "__op": "c",
                    "__source_ts_ms": 1705315800000
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), event.id)
            assertEquals("newuser@example.com", event.email)
            assertEquals("pending", event.status)
            assertEquals("c", event.operation)
            assertFalse(event.isDelete())
        }

        @Test
        fun `deserializes complete Debezium CDC delete event with rewrite`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "deleted@example.com",
                    "status": "active",
                    "updated_at": "2024-01-15T10:30:00Z",
                    "__deleted": "true",
                    "__op": "d",
                    "__source_ts_ms": 1705315800000
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), event.id)
            assertTrue(event.isDelete())
        }

        @Test
        fun `deserializes Debezium CDC snapshot event`() {
            val json = """
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "existing@example.com",
                    "status": "active",
                    "updated_at": "2024-01-10T08:00:00Z",
                    "__op": "r",
                    "__source_ts_ms": 1705315800000
                }
            """.trimIndent()

            val event = objectMapper.readValue(json, CustomerCdcEvent::class.java)

            assertEquals("r", event.operation)
            assertFalse(event.isDelete())
        }
    }
}
