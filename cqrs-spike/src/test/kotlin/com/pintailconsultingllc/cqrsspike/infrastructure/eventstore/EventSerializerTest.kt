package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("EventSerializer")
class EventSerializerTest {

    private val serializer = EventSerializer()

    @Nested
    @DisplayName("serialize")
    inner class Serialize {

        @Test
        @DisplayName("should serialize ProductCreated event to JSON")
        fun shouldSerializeProductCreatedEvent() {
            val event = ProductCreated(
                eventId = UUID.randomUUID(),
                productId = UUID.randomUUID(),
                occurredAt = OffsetDateTime.now(),
                version = 1,
                sku = "TEST-001",
                name = "Test Product",
                description = "A test product description",
                priceCents = 1999,
                status = ProductStatus.DRAFT
            )

            val json = serializer.serialize(event)

            assertNotNull(json)
            assertTrue(json.contains("TEST-001"))
            assertTrue(json.contains("Test Product"))
            assertTrue(json.contains("1999"))
            assertTrue(json.contains("DRAFT"))
        }

        @Test
        @DisplayName("should serialize ProductUpdated event")
        fun shouldSerializeProductUpdatedEvent() {
            val event = ProductUpdated(
                productId = UUID.randomUUID(),
                version = 2,
                name = "Updated Name",
                description = "Updated description",
                previousName = "Old Name",
                previousDescription = "Old description"
            )

            val json = serializer.serialize(event)

            assertNotNull(json)
            assertTrue(json.contains("Updated Name"))
            assertTrue(json.contains("Old Name"))
        }

        @Test
        @DisplayName("should serialize ProductPriceChanged event")
        fun shouldSerializeProductPriceChangedEvent() {
            val event = ProductPriceChanged(
                productId = UUID.randomUUID(),
                version = 2,
                newPriceCents = 2999,
                previousPriceCents = 1999,
                changePercentage = 50.0
            )

            val json = serializer.serialize(event)

            assertNotNull(json)
            assertTrue(json.contains("2999"))
            assertTrue(json.contains("1999"))
            assertTrue(json.contains("50.0"))
        }

        @Test
        @DisplayName("should serialize event with null description")
        fun shouldSerializeEventWithNullDescription() {
            val event = ProductCreated(
                productId = UUID.randomUUID(),
                version = 1,
                sku = "NULL-DESC",
                name = "No Description Product",
                description = null,
                priceCents = 500
            )

            val json = serializer.serialize(event)

            assertNotNull(json)
            assertTrue(json.contains("null") || json.contains("description\":null"))
        }
    }

    @Nested
    @DisplayName("serializeMetadata")
    inner class SerializeMetadata {

        @Test
        @DisplayName("should serialize metadata to JSON")
        fun shouldSerializeMetadata() {
            val metadata = EventMetadata(
                causationId = UUID.randomUUID(),
                correlationId = UUID.randomUUID(),
                userId = "user-123",
                timestamp = System.currentTimeMillis()
            )

            val json = serializer.serializeMetadata(metadata)

            assertNotNull(json)
            assertTrue(json!!.contains("user-123"))
            assertTrue(json.contains("causationId"))
            assertTrue(json.contains("correlationId"))
        }

        @Test
        @DisplayName("should return null for null metadata")
        fun shouldReturnNullForNullMetadata() {
            val json = serializer.serializeMetadata(null)

            assertEquals(null, json)
        }
    }

    @Nested
    @DisplayName("getEventTypeName")
    inner class GetEventTypeName {

        @Test
        @DisplayName("should return correct type name for ProductCreated")
        fun shouldReturnCorrectTypeNameForProductCreated() {
            val event = ProductCreated(
                productId = UUID.randomUUID(),
                version = 1,
                sku = "TEST",
                name = "Test",
                description = null,
                priceCents = 100
            )

            val typeName = serializer.getEventTypeName(event)

            assertEquals("ProductCreated", typeName)
        }

        @Test
        @DisplayName("should return correct type name for ProductUpdated")
        fun shouldReturnCorrectTypeNameForProductUpdated() {
            val event = ProductUpdated(
                productId = UUID.randomUUID(),
                version = 2,
                name = "Name",
                description = null,
                previousName = "Old",
                previousDescription = null
            )

            val typeName = serializer.getEventTypeName(event)

            assertEquals("ProductUpdated", typeName)
        }

        @Test
        @DisplayName("should return correct type name for ProductPriceChanged")
        fun shouldReturnCorrectTypeNameForProductPriceChanged() {
            val event = ProductPriceChanged(
                productId = UUID.randomUUID(),
                version = 2,
                newPriceCents = 200,
                previousPriceCents = 100,
                changePercentage = 100.0
            )

            val typeName = serializer.getEventTypeName(event)

            assertEquals("ProductPriceChanged", typeName)
        }

        @Test
        @DisplayName("should return correct type name for ProductActivated")
        fun shouldReturnCorrectTypeNameForProductActivated() {
            val event = ProductActivated(
                productId = UUID.randomUUID(),
                version = 2,
                previousStatus = ProductStatus.DRAFT
            )

            val typeName = serializer.getEventTypeName(event)

            assertEquals("ProductActivated", typeName)
        }

        @Test
        @DisplayName("should return correct type name for ProductDiscontinued")
        fun shouldReturnCorrectTypeNameForProductDiscontinued() {
            val event = ProductDiscontinued(
                productId = UUID.randomUUID(),
                version = 3,
                previousStatus = ProductStatus.ACTIVE,
                reason = "Out of stock"
            )

            val typeName = serializer.getEventTypeName(event)

            assertEquals("ProductDiscontinued", typeName)
        }

        @Test
        @DisplayName("should return correct type name for ProductDeleted")
        fun shouldReturnCorrectTypeNameForProductDeleted() {
            val event = ProductDeleted(
                productId = UUID.randomUUID(),
                version = 4,
                deletedBy = "admin"
            )

            val typeName = serializer.getEventTypeName(event)

            assertEquals("ProductDeleted", typeName)
        }
    }

    @Nested
    @DisplayName("getEventVersion")
    inner class GetEventVersion {

        @Test
        @DisplayName("should return version 1 for ProductCreated")
        fun shouldReturnVersion1ForProductCreated() {
            val event = ProductCreated(
                productId = UUID.randomUUID(),
                version = 1,
                sku = "TEST",
                name = "Test",
                description = null,
                priceCents = 100
            )

            val version = serializer.getEventVersion(event)

            assertEquals(1, version)
        }

        @Test
        @DisplayName("should return version 1 for ProductPriceChanged")
        fun shouldReturnVersion1ForProductPriceChanged() {
            val event = ProductPriceChanged(
                productId = UUID.randomUUID(),
                version = 5,
                newPriceCents = 200,
                previousPriceCents = 100,
                changePercentage = 100.0
            )

            val version = serializer.getEventVersion(event)

            assertEquals(1, version)
        }
    }
}
