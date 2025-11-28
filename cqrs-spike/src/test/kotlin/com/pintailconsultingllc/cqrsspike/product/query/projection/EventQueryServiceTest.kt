package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.DomainEventEntity
import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.DomainEventR2dbcRepository
import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.EventDeserializer
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("EventQueryService")
class EventQueryServiceTest {

    @Mock
    private lateinit var domainEventRepository: DomainEventR2dbcRepository

    @Mock
    private lateinit var eventDeserializer: EventDeserializer

    private lateinit var eventQueryService: EventQueryService

    @BeforeEach
    fun setUp() {
        eventQueryService = EventQueryService(domainEventRepository, eventDeserializer)
    }

    @Nested
    @DisplayName("findEventsAfterEventId")
    inner class FindEventsAfterEventId {

        @Test
        @DisplayName("should return events after the specified event ID")
        fun shouldReturnEventsAfterEventId() {
            val afterEventId = UUID.randomUUID()
            val eventEntity = createEventEntity()
            val productEvent = createProductCreatedEvent()

            whenever(domainEventRepository.findEventsAfterEventId(eq(afterEventId), eq(100)))
                .thenReturn(Flux.just(eventEntity))
            whenever(eventDeserializer.deserialize(any(), any(), any()))
                .thenReturn(productEvent)

            StepVerifier.create(eventQueryService.findEventsAfterEventId(afterEventId, 100))
                .expectNextMatches { storedEvent ->
                    storedEvent.eventId == eventEntity.eventId &&
                        storedEvent.event == productEvent
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should query all events when afterEventId is null")
        fun shouldQueryAllEventsWhenAfterEventIdIsNull() {
            val eventEntity = createEventEntity()
            val productEvent = createProductCreatedEvent()

            whenever(domainEventRepository.findAllEventsOrdered(eq(100), eq(0L)))
                .thenReturn(Flux.just(eventEntity))
            whenever(eventDeserializer.deserialize(any(), any(), any()))
                .thenReturn(productEvent)

            StepVerifier.create(eventQueryService.findEventsAfterEventId(null, 100))
                .expectNextCount(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty flux when no events available")
        fun shouldReturnEmptyFluxWhenNoEventsAvailable() {
            val afterEventId = UUID.randomUUID()

            whenever(domainEventRepository.findEventsAfterEventId(eq(afterEventId), eq(100)))
                .thenReturn(Flux.empty())

            StepVerifier.create(eventQueryService.findEventsAfterEventId(afterEventId, 100))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findAllEventsOrdered")
    inner class FindAllEventsOrdered {

        @Test
        @DisplayName("should return events with pagination")
        fun shouldReturnEventsWithPagination() {
            val eventEntity = createEventEntity()
            val productEvent = createProductCreatedEvent()

            whenever(domainEventRepository.findAllEventsOrdered(eq(50), eq(100L)))
                .thenReturn(Flux.just(eventEntity))
            whenever(eventDeserializer.deserialize(any(), any(), any()))
                .thenReturn(productEvent)

            StepVerifier.create(eventQueryService.findAllEventsOrdered(50, 100))
                .expectNextCount(1)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("countEventsAfterEventId")
    inner class CountEventsAfterEventId {

        @Test
        @DisplayName("should return count of events after event ID")
        fun shouldReturnCountOfEventsAfterEventId() {
            val eventId = UUID.randomUUID()

            whenever(domainEventRepository.countEventsAfterEventId(eq(eventId)))
                .thenReturn(Mono.just(42L))

            StepVerifier.create(eventQueryService.countEventsAfterEventId(eventId))
                .expectNext(42L)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return total count when event ID is null")
        fun shouldReturnTotalCountWhenEventIdIsNull() {
            whenever(domainEventRepository.countAllEvents())
                .thenReturn(Mono.just(100L))

            StepVerifier.create(eventQueryService.countEventsAfterEventId(null))
                .expectNext(100L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getLatestEvent")
    inner class GetLatestEvent {

        @Test
        @DisplayName("should return the latest event")
        fun shouldReturnLatestEvent() {
            val eventEntity = createEventEntity()
            val productEvent = createProductCreatedEvent()

            whenever(domainEventRepository.findLatestEvent())
                .thenReturn(Mono.just(eventEntity))
            whenever(eventDeserializer.deserialize(any(), any(), any()))
                .thenReturn(productEvent)

            StepVerifier.create(eventQueryService.getLatestEvent())
                .expectNextMatches { it.eventId == eventEntity.eventId }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when no events exist")
        fun shouldReturnEmptyWhenNoEventsExist() {
            whenever(domainEventRepository.findLatestEvent())
                .thenReturn(Mono.empty())

            StepVerifier.create(eventQueryService.getLatestEvent())
                .verifyComplete()
        }
    }

    // Helper methods

    private fun createEventEntity(): DomainEventEntity {
        return DomainEventEntity(
            eventId = UUID.randomUUID(),
            streamId = UUID.randomUUID(),
            eventType = "ProductCreated",
            eventVersion = 1,
            aggregateVersion = 1,
            eventData = "{}",
            metadata = null,
            occurredAt = OffsetDateTime.now(),
            causationId = null,
            correlationId = null,
            userId = null
        )
    }

    private fun createProductCreatedEvent(): ProductCreated {
        return ProductCreated(
            eventId = UUID.randomUUID(),
            productId = UUID.randomUUID(),
            sku = "TEST-001",
            name = "Test Product",
            description = null,
            priceCents = 1999,
            status = ProductStatus.DRAFT,
            occurredAt = OffsetDateTime.now(),
            version = 1
        )
    }
}
