package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration tests for ProductEventStoreRepository with PostgreSQL.
 *
 * IMPORTANT: Before running these tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ProductEventStoreRepository Integration Tests")
class ProductEventStoreRepositoryIntegrationTest {

    @Autowired
    private lateinit var eventStoreRepository: ProductEventStoreRepository

    @Nested
    @DisplayName("Save Events")
    inner class SaveEvents {

        @Test
        @DisplayName("should save single event to event store")
        fun shouldSaveSingleEvent() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "SAVE-${productId.toString().take(8)}",
                name = "Test Product",
                description = "Test description",
                priceCents = 1999
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event)))
                .verifyComplete()

            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("should save multiple events atomically")
        fun shouldSaveMultipleEventsAtomically() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "MULTI-${productId.toString().take(8)}",
                    name = "Test Product",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 1500,
                    previousPriceCents = 1000,
                    changePercentage = 50.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(eventStoreRepository.saveEvents(events))
                .verifyComplete()

            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(3)
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle empty events list")
        fun shouldHandleEmptyEventsList() {
            StepVerifier.create(eventStoreRepository.saveEvents(emptyList()))
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject events from different aggregates")
        fun shouldRejectEventsFromDifferentAggregates() {
            val productId1 = UUID.randomUUID()
            val productId2 = UUID.randomUUID()

            val mixedEvents = listOf(
                ProductCreated(
                    productId = productId1,
                    version = 1,
                    sku = "MIX1-${productId1.toString().take(8)}",
                    name = "Product 1",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId2, // Different aggregate!
                    version = 2,
                    newPriceCents = 1500,
                    previousPriceCents = 1000,
                    changePercentage = 50.0
                )
            )

            StepVerifier.create(eventStoreRepository.saveEvents(mixedEvents))
                .expectErrorMatches { error ->
                    error is IllegalArgumentException &&
                    error.message == "All events must belong to the same aggregate"
                }
                .verify()
        }
    }

    @Nested
    @DisplayName("Find Events")
    inner class FindEvents {

        @Test
        @DisplayName("should return events in version order")
        fun shouldReturnEventsInVersionOrder() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "ORDER-${productId.toString().take(8)}",
                    name = "Test",
                    description = null,
                    priceCents = 100
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 200,
                    previousPriceCents = 100,
                    changePercentage = 100.0
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .thenMany(eventStoreRepository.findEventsByAggregateId(productId))
            )
                .expectNextMatches { it.version == 1L }
                .expectNextMatches { it.version == 2L }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty for non-existent aggregate")
        fun shouldReturnEmptyForNonExistentAggregate() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(nonExistentId))
                .verifyComplete()
        }

        @Test
        @DisplayName("should deserialize ProductCreated event correctly")
        fun shouldDeserializeProductCreatedEventCorrectly() {
            val productId = UUID.randomUUID()
            val originalEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "DESER-${productId.toString().take(8)}",
                name = "Deserialize Test Product",
                description = "Test description",
                priceCents = 2999,
                status = ProductStatus.DRAFT
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(originalEvent))
                    .thenMany(eventStoreRepository.findEventsByAggregateId(productId))
            )
                .expectNextMatches { event ->
                    event is ProductCreated &&
                    event.productId == productId &&
                    event.sku == originalEvent.sku &&
                    event.name == "Deserialize Test Product" &&
                    event.description == "Test description" &&
                    event.priceCents == 2999
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should deserialize ProductPriceChanged event correctly")
        fun shouldDeserializeProductPriceChangedEventCorrectly() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "PRICE-${productId.toString().take(8)}",
                    name = "Price Test",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 1999,
                    previousPriceCents = 1000,
                    changePercentage = 99.9
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .thenMany(eventStoreRepository.findEventsByAggregateId(productId))
            )
                .expectNextMatches { it is ProductCreated }
                .expectNextMatches { event ->
                    event is ProductPriceChanged &&
                    event.newPriceCents == 1999 &&
                    event.previousPriceCents == 1000 &&
                    event.changePercentage == 99.9
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Stream Version")
    inner class StreamVersion {

        @Test
        @DisplayName("should track stream version correctly")
        fun shouldTrackStreamVersionCorrectly() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "VER-${productId.toString().take(8)}",
                name = "Test",
                description = null,
                priceCents = 100
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event))
                    .then(eventStoreRepository.getStreamVersion(productId))
            )
                .expectNext(1L)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return 0 for non-existent stream")
        fun shouldReturnZeroForNonExistentStream() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.getStreamVersion(nonExistentId))
                .expectNext(0L)
                .verifyComplete()
        }

        @Test
        @DisplayName("should increment version with multiple events")
        fun shouldIncrementVersionWithMultipleEvents() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "INCR-${productId.toString().take(8)}",
                    name = "Test",
                    description = null,
                    priceCents = 100
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 150,
                    previousPriceCents = 100,
                    changePercentage = 50.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .then(eventStoreRepository.getStreamVersion(productId))
            )
                .expectNext(3L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Stream Exists")
    inner class StreamExists {

        @Test
        @DisplayName("should return true for existing stream")
        fun shouldReturnTrueForExistingStream() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "EXIST-${productId.toString().take(8)}",
                name = "Test",
                description = null,
                priceCents = 100
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event))
                    .then(eventStoreRepository.streamExists(productId))
            )
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return false for non-existent stream")
        fun shouldReturnFalseForNonExistentStream() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.streamExists(nonExistentId))
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Find Events From Version")
    inner class FindEventsFromVersion {

        @Test
        @DisplayName("should return events after specified version")
        fun shouldReturnEventsAfterSpecifiedVersion() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "FROM-${productId.toString().take(8)}",
                    name = "Test",
                    description = null,
                    priceCents = 100
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 200,
                    previousPriceCents = 100,
                    changePercentage = 100.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .thenMany(eventStoreRepository.findEventsByAggregateIdFromVersion(productId, 1L))
            )
                .expectNextMatches { it.version == 2L && it is ProductPriceChanged }
                .expectNextMatches { it.version == 3L && it is ProductActivated }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when all events are before specified version")
        fun shouldReturnEmptyWhenAllEventsBeforeVersion() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "BEFORE-${productId.toString().take(8)}",
                name = "Test",
                description = null,
                priceCents = 100
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event))
                    .thenMany(eventStoreRepository.findEventsByAggregateIdFromVersion(productId, 10L))
            )
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Optimistic Concurrency Control")
    inner class OptimisticConcurrencyControl {

        @Test
        @DisplayName("should throw EventStoreConcurrencyException when expected version does not match")
        fun shouldThrowConcurrencyExceptionWhenVersionMismatch() {
            val productId = UUID.randomUUID()

            // First, save an initial event at version 1
            val initialEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "CONC-${productId.toString().take(8)}",
                name = "Test Product",
                description = null,
                priceCents = 1000
            )

            // Save the initial event
            StepVerifier.create(eventStoreRepository.saveEvents(listOf(initialEvent)))
                .verifyComplete()

            // Verify stream is at version 1
            StepVerifier.create(eventStoreRepository.getStreamVersion(productId))
                .expectNext(1L)
                .verifyComplete()

            // Attempt to save an event with version 2 but expecting version 0 (stale)
            // This simulates another process having already saved an event
            val conflictingEvent = ProductPriceChanged(
                productId = productId,
                version = 1, // This implies expectedVersion = 0, but stream is already at version 1
                newPriceCents = 1500,
                previousPriceCents = 1000,
                changePercentage = 50.0
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(conflictingEvent)))
                .expectErrorMatches { error ->
                    error is EventStoreVersionConflictException &&
                    error.aggregateId == productId &&
                    error.expectedVersion == 0L
                }
                .verify()
        }

        @Test
        @DisplayName("should succeed when expected version matches current stream version")
        fun shouldSucceedWhenVersionMatches() {
            val productId = UUID.randomUUID()

            // Save initial event at version 1
            val initialEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "MATCH-${productId.toString().take(8)}",
                name = "Test Product",
                description = null,
                priceCents = 1000
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(initialEvent)))
                .verifyComplete()

            // Save second event at version 2, expecting version 1
            val secondEvent = ProductPriceChanged(
                productId = productId,
                version = 2, // This implies expectedVersion = 1, which matches
                newPriceCents = 1500,
                previousPriceCents = 1000,
                changePercentage = 50.0
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(secondEvent)))
                .verifyComplete()

            // Verify both events are saved
            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(2)
                .verifyComplete()

            // Verify stream is at version 2
            StepVerifier.create(eventStoreRepository.getStreamVersion(productId))
                .expectNext(2L)
                .verifyComplete()
        }

        @Test
        @DisplayName("should detect concurrent modification with multiple events batch")
        fun shouldDetectConcurrencyWithMultipleEventsBatch() {
            val productId = UUID.randomUUID()

            // Save initial event
            val initialEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "BATCH-${productId.toString().take(8)}",
                name = "Test Product",
                description = null,
                priceCents = 1000
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(initialEvent)))
                .verifyComplete()

            // Attempt to save multiple events starting from wrong version
            val conflictingBatch = listOf(
                ProductPriceChanged(
                    productId = productId,
                    version = 1, // expectedVersion = 0, but stream is at 1
                    newPriceCents = 1500,
                    previousPriceCents = 1000,
                    changePercentage = 50.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 2,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(eventStoreRepository.saveEvents(conflictingBatch))
                .expectErrorMatches { error ->
                    error is EventStoreVersionConflictException &&
                    error.aggregateId == productId
                }
                .verify()

            // Verify no events from the conflicting batch were saved (atomicity)
            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(1) // Only the initial event
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Event Metadata")
    inner class EventMetadataTests {

        @Test
        @DisplayName("should save events with metadata")
        fun shouldSaveEventsWithMetadata() {
            val productId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()
            val causationId = UUID.randomUUID()
            val userId = "test-user@example.com"

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "META-${productId.toString().take(8)}",
                name = "Metadata Test Product",
                description = "Testing metadata persistence",
                priceCents = 2999
            )

            val metadata = EventMetadata(
                correlationId = correlationId,
                causationId = causationId,
                userId = userId
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event), metadata))
                .verifyComplete()

            // Verify event was saved
            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("should find events by correlation ID")
        fun shouldFindEventsByCorrelationId() {
            val productId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()
            val causationId = UUID.randomUUID()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "CORR-${productId.toString().take(8)}",
                name = "Correlation Test Product",
                description = null,
                priceCents = 1999
            )

            val metadata = EventMetadata(
                correlationId = correlationId,
                causationId = causationId,
                userId = "correlation-test-user"
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event), metadata)
                    .thenMany(eventStoreRepository.findEventsByCorrelationId(correlationId))
            )
                .expectNextMatches { foundEvent ->
                    foundEvent is ProductCreated &&
                    foundEvent.productId == productId &&
                    foundEvent.sku == event.sku
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should find multiple events with same correlation ID")
        fun shouldFindMultipleEventsWithSameCorrelationId() {
            val productId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "MULTI-CORR-${productId.toString().take(8)}",
                    name = "Multi Correlation Test",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 1500,
                    previousPriceCents = 1000,
                    changePercentage = 50.0
                )
            )

            val metadata = EventMetadata(
                correlationId = correlationId,
                causationId = UUID.randomUUID(),
                userId = "multi-event-user"
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events, metadata)
                    .thenMany(eventStoreRepository.findEventsByCorrelationId(correlationId))
            )
                .expectNextCount(2)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when correlation ID not found")
        fun shouldReturnEmptyWhenCorrelationIdNotFound() {
            val nonExistentCorrelationId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.findEventsByCorrelationId(nonExistentCorrelationId))
                .verifyComplete()
        }

        @Test
        @DisplayName("should isolate events by different correlation IDs")
        fun shouldIsolateEventsByDifferentCorrelationIds() {
            val productId1 = UUID.randomUUID()
            val productId2 = UUID.randomUUID()
            val correlationId1 = UUID.randomUUID()
            val correlationId2 = UUID.randomUUID()

            val event1 = ProductCreated(
                productId = productId1,
                version = 1,
                sku = "ISO1-${productId1.toString().take(8)}",
                name = "Isolation Test 1",
                description = null,
                priceCents = 1000
            )

            val event2 = ProductCreated(
                productId = productId2,
                version = 1,
                sku = "ISO2-${productId2.toString().take(8)}",
                name = "Isolation Test 2",
                description = null,
                priceCents = 2000
            )

            val metadata1 = EventMetadata(correlationId = correlationId1)
            val metadata2 = EventMetadata(correlationId = correlationId2)

            // Save events with different correlation IDs
            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event1), metadata1)
                    .then(eventStoreRepository.saveEvents(listOf(event2), metadata2))
            )
                .verifyComplete()

            // Verify correlation ID 1 only returns event 1
            StepVerifier.create(eventStoreRepository.findEventsByCorrelationId(correlationId1))
                .expectNextMatches { event ->
                    event is ProductCreated && event.productId == productId1
                }
                .verifyComplete()

            // Verify correlation ID 2 only returns event 2
            StepVerifier.create(eventStoreRepository.findEventsByCorrelationId(correlationId2))
                .expectNextMatches { event ->
                    event is ProductCreated && event.productId == productId2
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should save events without metadata")
        fun shouldSaveEventsWithoutMetadata() {
            val productId = UUID.randomUUID()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "NO-META-${productId.toString().take(8)}",
                name = "No Metadata Product",
                description = null,
                priceCents = 500
            )

            // Save without metadata (null)
            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event), null))
                .verifyComplete()

            // Verify event was saved correctly
            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextMatches { foundEvent ->
                    foundEvent is ProductCreated &&
                    foundEvent.productId == productId &&
                    foundEvent.name == "No Metadata Product"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Find Events By Type And Time Range")
    inner class FindEventsByTypeAndTimeRangeTests {

        @Test
        @DisplayName("should find events within time range")
        fun shouldFindEventsWithinTimeRange() {
            val productId = UUID.randomUUID()
            val beforeSave = OffsetDateTime.now()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "TIME-${productId.toString().take(8)}",
                name = "Time Range Test Product",
                description = null,
                priceCents = 1999
            )

            // Save the event
            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event)))
                .verifyComplete()

            val afterSave = OffsetDateTime.now().plusSeconds(1)

            // Query for ProductCreated events within the time range
            StepVerifier.create(
                eventStoreRepository.findEventsByTypeAndTimeRange(
                    eventType = "ProductCreated",
                    startTime = beforeSave,
                    endTime = afterSave
                )
            )
                .expectNextMatches { foundEvent ->
                    foundEvent is ProductCreated &&
                    foundEvent.productId == productId &&
                    foundEvent.sku == event.sku
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when no events match time range")
        fun shouldReturnEmptyWhenNoEventsMatchTimeRange() {
            val productId = UUID.randomUUID()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "PAST-${productId.toString().take(8)}",
                name = "Past Event Product",
                description = null,
                priceCents = 999
            )

            // Save the event
            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event)))
                .verifyComplete()

            // Query for a time range in the future (no events should match)
            val futureStart = OffsetDateTime.now().plusHours(1)
            val futureEnd = OffsetDateTime.now().plusHours(2)

            StepVerifier.create(
                eventStoreRepository.findEventsByTypeAndTimeRange(
                    eventType = "ProductCreated",
                    startTime = futureStart,
                    endTime = futureEnd
                )
            )
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when event type does not match")
        fun shouldReturnEmptyWhenEventTypeDoesNotMatch() {
            val productId = UUID.randomUUID()
            val beforeSave = OffsetDateTime.now()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "TYPE-${productId.toString().take(8)}",
                name = "Type Mismatch Test",
                description = null,
                priceCents = 500
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event)))
                .verifyComplete()

            val afterSave = OffsetDateTime.now().plusSeconds(1)

            // Query for a different event type
            StepVerifier.create(
                eventStoreRepository.findEventsByTypeAndTimeRange(
                    eventType = "ProductPriceChanged",
                    startTime = beforeSave,
                    endTime = afterSave
                )
            )
                .verifyComplete()
        }

        @Test
        @DisplayName("should find multiple events of same type within time range")
        fun shouldFindMultipleEventsOfSameTypeWithinTimeRange() {
            val productId1 = UUID.randomUUID()
            val productId2 = UUID.randomUUID()
            val beforeSave = OffsetDateTime.now()

            val event1 = ProductCreated(
                productId = productId1,
                version = 1,
                sku = "MULTI1-${productId1.toString().take(8)}",
                name = "Multi Time Range 1",
                description = null,
                priceCents = 1000
            )

            val event2 = ProductCreated(
                productId = productId2,
                version = 1,
                sku = "MULTI2-${productId2.toString().take(8)}",
                name = "Multi Time Range 2",
                description = null,
                priceCents = 2000
            )

            // Save events for different aggregates
            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event1))
                    .then(eventStoreRepository.saveEvents(listOf(event2)))
            )
                .verifyComplete()

            val afterSave = OffsetDateTime.now().plusSeconds(1)

            // Query should return both ProductCreated events
            StepVerifier.create(
                eventStoreRepository.findEventsByTypeAndTimeRange(
                    eventType = "ProductCreated",
                    startTime = beforeSave,
                    endTime = afterSave
                )
            )
                .expectNextMatches { it is ProductCreated }
                .expectNextMatches { it is ProductCreated }
                .verifyComplete()
        }

        @Test
        @DisplayName("should filter by event type when multiple types exist in time range")
        fun shouldFilterByEventTypeWhenMultipleTypesExist() {
            val productId = UUID.randomUUID()
            val beforeSave = OffsetDateTime.now()

            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "FILTER-${productId.toString().take(8)}",
                    name = "Filter Test Product",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 1500,
                    previousPriceCents = 1000,
                    changePercentage = 50.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(eventStoreRepository.saveEvents(events))
                .verifyComplete()

            val afterSave = OffsetDateTime.now().plusSeconds(1)

            // Query only for ProductPriceChanged events
            StepVerifier.create(
                eventStoreRepository.findEventsByTypeAndTimeRange(
                    eventType = "ProductPriceChanged",
                    startTime = beforeSave,
                    endTime = afterSave
                )
            )
                .expectNextMatches { event ->
                    event is ProductPriceChanged &&
                    event.productId == productId &&
                    event.newPriceCents == 1500
                }
                .verifyComplete()
        }
    }
}
