package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import com.pintailconsultingllc.resiliencyspike.repository.ResilienceEventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("ResilienceEventService Unit Tests")
class ResilienceEventServiceTest {

    @Mock
    private lateinit var resilienceEventRepository: ResilienceEventRepository

    private lateinit var resilienceEventService: ResilienceEventService

    @BeforeEach
    fun setUp() {
        resilienceEventService = ResilienceEventService(resilienceEventRepository)
    }

    @Test
    @DisplayName("Should save a resilience event successfully")
    fun shouldSaveResilienceEventSuccessfully() {
        // Given
        val event = TestFixtures.createResilienceEvent(
            id = null,
            eventType = "CIRCUIT_BREAKER",
            eventName = "payment-service",
            status = "OPEN"
        )
        val savedEvent = event.copy(id = UUID.randomUUID())

        whenever(resilienceEventRepository.save(any())).thenReturn(Mono.just(savedEvent))

        // When
        val result = resilienceEventService.saveEvent(event)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id != null && it.eventName == "payment-service" }
            .verifyComplete()

        verify(resilienceEventRepository).save(event)
    }

    @Test
    @DisplayName("Should find all events by type")
    fun shouldFindAllEventsByType() {
        // Given
        val eventType = "CIRCUIT_BREAKER"
        val events = listOf(
            TestFixtures.createResilienceEvent(eventType = eventType, eventName = "service-1"),
            TestFixtures.createResilienceEvent(eventType = eventType, eventName = "service-2"),
            TestFixtures.createResilienceEvent(eventType = eventType, eventName = "service-3")
        )

        whenever(resilienceEventRepository.findByEventType(eventType))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventService.findEventsByType(eventType)

        // Then
        StepVerifier.create(result)
            .expectNext(events[0])
            .expectNext(events[1])
            .expectNext(events[2])
            .verifyComplete()

        verify(resilienceEventRepository).findByEventType(eventType)
    }

    @Test
    @DisplayName("Should find recent events with default limit")
    fun shouldFindRecentEventsWithDefaultLimit() {
        // Given
        val recentEvents = listOf(
            TestFixtures.createResilienceEvent(eventName = "event-1"),
            TestFixtures.createResilienceEvent(eventName = "event-2")
        )

        whenever(resilienceEventRepository.findRecentEvents(50))
            .thenReturn(Flux.fromIterable(recentEvents))

        // When
        val result = resilienceEventService.findRecentEvents()

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(resilienceEventRepository).findRecentEvents(50)
    }

    @Test
    @DisplayName("Should find recent events with custom limit")
    fun shouldFindRecentEventsWithCustomLimit() {
        // Given
        val limit = 10
        val recentEvents = listOf(TestFixtures.createResilienceEvent())

        whenever(resilienceEventRepository.findRecentEvents(limit))
            .thenReturn(Flux.fromIterable(recentEvents))

        // When
        val result = resilienceEventService.findRecentEvents(limit)

        // Then
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(resilienceEventRepository).findRecentEvents(limit)
    }

    @Test
    @DisplayName("Should find event by ID when event exists")
    fun shouldFindEventByIdWhenEventExists() {
        // Given
        val eventId = UUID.randomUUID()
        val event = TestFixtures.createResilienceEvent(id = eventId, eventName = "test-event")

        whenever(resilienceEventRepository.findById(eventId))
            .thenReturn(Mono.just(event))

        // When
        val result = resilienceEventService.findEventById(eventId)

        // Then
        StepVerifier.create(result)
            .expectNext(event)
            .verifyComplete()

        verify(resilienceEventRepository).findById(eventId)
    }

    @Test
    @DisplayName("Should return empty Mono when event ID does not exist")
    fun shouldReturnEmptyMonoWhenEventIdDoesNotExist() {
        // Given
        val eventId = UUID.randomUUID()

        whenever(resilienceEventRepository.findById(eventId))
            .thenReturn(Mono.empty())

        // When
        val result = resilienceEventService.findEventById(eventId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(resilienceEventRepository).findById(eventId)
    }

    @Test
    @DisplayName("Should find all events by status")
    fun shouldFindAllEventsByStatus() {
        // Given
        val status = "FAILED"
        val failedEvents = listOf(
            TestFixtures.createResilienceEvent(status = status, eventName = "event-1"),
            TestFixtures.createResilienceEvent(status = status, eventName = "event-2")
        )

        whenever(resilienceEventRepository.findByStatus(status))
            .thenReturn(Flux.fromIterable(failedEvents))

        // When
        val result = resilienceEventService.findEventsByStatus(status)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(resilienceEventRepository).findByStatus(status)
    }

    @Test
    @DisplayName("Should find events by type and status")
    fun shouldFindEventsByTypeAndStatus() {
        // Given
        val eventType = "RATE_LIMITER"
        val status = "REJECTED"
        val events = listOf(
            TestFixtures.createResilienceEvent(eventType = eventType, status = status)
        )

        whenever(resilienceEventRepository.findByEventTypeAndStatus(eventType, status))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventService.findEventsByTypeAndStatus(eventType, status)

        // Then
        StepVerifier.create(result)
            .expectNext(events[0])
            .verifyComplete()

        verify(resilienceEventRepository).findByEventTypeAndStatus(eventType, status)
    }

    @Test
    @DisplayName("Should delete all events successfully")
    fun shouldDeleteAllEventsSuccessfully() {
        // Given
        whenever(resilienceEventRepository.deleteAll())
            .thenReturn(Mono.empty())

        // When
        val result = resilienceEventService.deleteAllEvents()

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(resilienceEventRepository).deleteAll()
    }

    @Test
    @DisplayName("Should handle repository error when saving event")
    fun shouldHandleRepositoryErrorWhenSavingEvent() {
        // Given
        val event = TestFixtures.createResilienceEvent()
        val error = RuntimeException("Database connection failed")

        whenever(resilienceEventRepository.save(any()))
            .thenReturn(Mono.error(error))

        // When
        val result = resilienceEventService.saveEvent(event)

        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException::class.java)
            .verify()

        verify(resilienceEventRepository).save(event)
    }

    @Test
    @DisplayName("Should return empty Flux when no events found by type")
    fun shouldReturnEmptyFluxWhenNoEventsFoundByType() {
        // Given
        val eventType = "NONEXISTENT_TYPE"

        whenever(resilienceEventRepository.findByEventType(eventType))
            .thenReturn(Flux.empty())

        // When
        val result = resilienceEventService.findEventsByType(eventType)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(resilienceEventRepository).findByEventType(eventType)
    }
}
