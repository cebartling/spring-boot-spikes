package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@DisplayName("EventDeserializer")
class EventDeserializerTest {

    private val deserializer = EventDeserializer()

    @Nested
    @DisplayName("deserialize")
    inner class Deserialize {

        @Test
        @DisplayName("should deserialize ProductCreated event")
        fun shouldDeserializeProductCreatedEvent() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "$eventId",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 1,
                    "sku": "TEST-001",
                    "name": "Test Product",
                    "description": "A test product",
                    "priceCents": 1999,
                    "status": "DRAFT"
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductCreated", 1, json)

            assertIs<ProductCreated>(event)
            assertEquals(productId, event.productId)
            assertEquals(eventId, event.eventId)
            assertEquals("TEST-001", event.sku)
            assertEquals("Test Product", event.name)
            assertEquals("A test product", event.description)
            assertEquals(1999, event.priceCents)
        }

        @Test
        @DisplayName("should deserialize ProductCreated with null description")
        fun shouldDeserializeProductCreatedWithNullDescription() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 1,
                    "sku": "NULL-DESC",
                    "name": "No Description",
                    "description": null,
                    "priceCents": 500,
                    "status": "DRAFT"
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductCreated", 1, json)

            assertIs<ProductCreated>(event)
            assertNull(event.description)
        }

        @Test
        @DisplayName("should deserialize ProductUpdated event")
        fun shouldDeserializeProductUpdatedEvent() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 2,
                    "name": "Updated Name",
                    "description": "Updated description",
                    "previousName": "Old Name",
                    "previousDescription": "Old description"
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductUpdated", 1, json)

            assertIs<ProductUpdated>(event)
            assertEquals(productId, event.productId)
            assertEquals("Updated Name", event.name)
            assertEquals("Updated description", event.description)
            assertEquals("Old Name", event.previousName)
        }

        @Test
        @DisplayName("should deserialize ProductUpdated with null descriptions")
        fun shouldDeserializeProductUpdatedWithNullDescriptions() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 2,
                    "name": "Updated Name",
                    "description": null,
                    "previousName": "Old Name",
                    "previousDescription": null
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductUpdated", 1, json)

            assertIs<ProductUpdated>(event)
            assertNull(event.description)
            assertNull(event.previousDescription)
        }

        @Test
        @DisplayName("should deserialize ProductPriceChanged event")
        fun shouldDeserializeProductPriceChangedEvent() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 3,
                    "newPriceCents": 2999,
                    "previousPriceCents": 1999,
                    "changePercentage": 50.025
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductPriceChanged", 1, json)

            assertIs<ProductPriceChanged>(event)
            assertEquals(productId, event.productId)
            assertEquals(2999, event.newPriceCents)
            assertEquals(1999, event.previousPriceCents)
            assertEquals(50.025, event.changePercentage)
        }

        @Test
        @DisplayName("should deserialize ProductActivated event")
        fun shouldDeserializeProductActivatedEvent() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 2,
                    "previousStatus": "DRAFT"
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductActivated", 1, json)

            assertIs<ProductActivated>(event)
            assertEquals(productId, event.productId)
        }

        @Test
        @DisplayName("should deserialize ProductDiscontinued event")
        fun shouldDeserializeProductDiscontinuedEvent() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 3,
                    "previousStatus": "ACTIVE",
                    "reason": "Out of stock permanently"
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductDiscontinued", 1, json)

            assertIs<ProductDiscontinued>(event)
            assertEquals("Out of stock permanently", event.reason)
        }

        @Test
        @DisplayName("should deserialize ProductDeleted event")
        fun shouldDeserializeProductDeletedEvent() {
            val productId = UUID.randomUUID()
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "$productId",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "version": 4,
                    "deletedBy": "admin-user"
                }
            """.trimIndent()

            val event = deserializer.deserialize("ProductDeleted", 1, json)

            assertIs<ProductDeleted>(event)
            assertEquals("admin-user", event.deletedBy)
        }

        @Test
        @DisplayName("should throw exception for unknown event type")
        fun shouldThrowExceptionForUnknownEventType() {
            val json = """{"data": "test"}"""

            val exception = assertFailsWith<EventDeserializationException> {
                deserializer.deserialize("UnknownEvent", 1, json)
            }

            assertEquals("Unknown event type: UnknownEvent", exception.message)
        }

        @Test
        @DisplayName("should throw exception for invalid JSON")
        fun shouldThrowExceptionForInvalidJson() {
            val invalidJson = "not valid json"

            assertFailsWith<EventDeserializationException> {
                deserializer.deserialize("ProductCreated", 1, invalidJson)
            }
        }

        @Test
        @DisplayName("should throw exception for missing required fields")
        fun shouldThrowExceptionForMissingRequiredFields() {
            val json = """
                {
                    "eventId": "${UUID.randomUUID()}",
                    "productId": "${UUID.randomUUID()}"
                }
            """.trimIndent()

            assertFailsWith<EventDeserializationException> {
                deserializer.deserialize("ProductCreated", 1, json)
            }
        }
    }

    @Nested
    @DisplayName("roundtrip serialization")
    inner class RoundtripSerialization {

        private val serializer = EventSerializer()

        @Test
        @DisplayName("should serialize and deserialize ProductCreated correctly")
        fun shouldRoundtripProductCreated() {
            val original = ProductCreated(
                productId = UUID.randomUUID(),
                version = 1,
                sku = "ROUND-001",
                name = "Roundtrip Test",
                description = "Testing roundtrip",
                priceCents = 4999
            )

            val json = serializer.serialize(original)
            val deserialized = deserializer.deserialize(
                serializer.getEventTypeName(original),
                serializer.getEventVersion(original),
                json
            )

            assertIs<ProductCreated>(deserialized)
            assertEquals(original.productId, deserialized.productId)
            assertEquals(original.sku, deserialized.sku)
            assertEquals(original.name, deserialized.name)
            assertEquals(original.description, deserialized.description)
            assertEquals(original.priceCents, deserialized.priceCents)
        }

        @Test
        @DisplayName("should serialize and deserialize ProductPriceChanged correctly")
        fun shouldRoundtripProductPriceChanged() {
            val original = ProductPriceChanged(
                productId = UUID.randomUUID(),
                version = 5,
                newPriceCents = 7999,
                previousPriceCents = 5999,
                changePercentage = 33.34
            )

            val json = serializer.serialize(original)
            val deserialized = deserializer.deserialize(
                serializer.getEventTypeName(original),
                serializer.getEventVersion(original),
                json
            )

            assertIs<ProductPriceChanged>(deserialized)
            assertEquals(original.productId, deserialized.productId)
            assertEquals(original.newPriceCents, deserialized.newPriceCents)
            assertEquals(original.previousPriceCents, deserialized.previousPriceCents)
            assertEquals(original.changePercentage, deserialized.changePercentage)
        }
    }
}
