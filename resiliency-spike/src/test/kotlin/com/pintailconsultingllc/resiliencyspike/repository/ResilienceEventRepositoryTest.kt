package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.ResilienceEvent
import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
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
@DisplayName("ResilienceEventRepository Unit Tests")
class ResilienceEventRepositoryTest {

    @Mock
    private lateinit var resilienceEventRepository: ResilienceEventRepository

    @BeforeEach
    fun setUp() {
        // Mock repository is injected via @Mock annotation
    }

    @Test
    @DisplayName("Should save a resilience event and return saved entity with ID")
    fun shouldSaveResilienceEventAndReturnSavedEntity() {
        // Given
        val event = TestFixtures.createResilienceEvent(id = null)
        val savedEvent = event.copy(id = UUID.randomUUID())

        whenever(resilienceEventRepository.save(event))
            .thenReturn(Mono.just(savedEvent))

        // When
        val result = resilienceEventRepository.save(event)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id != null }
            .verifyComplete()

        verify(resilienceEventRepository).save(event)
    }

    @Test
    @DisplayName("Should find events by event type")
    fun shouldFindEventsByEventType() {
        // Given
        val eventType = "CIRCUIT_BREAKER"
        val events = listOf(
            TestFixtures.createResilienceEvent(eventType = eventType, eventName = "service-1"),
            TestFixtures.createResilienceEvent(eventType = eventType, eventName = "service-2")
        )

        whenever(resilienceEventRepository.findByEventType(eventType))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventRepository.findByEventType(eventType)

        // Then
        StepVerifier.create(result)
            .expectNext(events[0])
            .expectNext(events[1])
            .verifyComplete()

        verify(resilienceEventRepository).findByEventType(eventType)
    }

    @Test
    @DisplayName("Should find events by event name")
    fun shouldFindEventsByEventName() {
        // Given
        val eventName = "payment-service"
        val events = listOf(
            TestFixtures.createResilienceEvent(eventName = eventName)
        )

        whenever(resilienceEventRepository.findByEventName(eventName))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventRepository.findByEventName(eventName)

        // Then
        StepVerifier.create(result)
            .expectNext(events[0])
            .verifyComplete()

        verify(resilienceEventRepository).findByEventName(eventName)
    }

    @Test
    @DisplayName("Should find events by status")
    fun shouldFindEventsByStatus() {
        // Given
        val status = "FAILED"
        val events = listOf(
            TestFixtures.createResilienceEvent(status = status, eventName = "event-1"),
            TestFixtures.createResilienceEvent(status = status, eventName = "event-2")
        )

        whenever(resilienceEventRepository.findByStatus(status))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventRepository.findByStatus(status)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(resilienceEventRepository).findByStatus(status)
    }

    @Test
    @DisplayName("Should find events by event type and status")
    fun shouldFindEventsByEventTypeAndStatus() {
        // Given
        val eventType = "RATE_LIMITER"
        val status = "REJECTED"
        val events = listOf(
            TestFixtures.createResilienceEvent(eventType = eventType, status = status)
        )

        whenever(resilienceEventRepository.findByEventTypeAndStatus(eventType, status))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventRepository.findByEventTypeAndStatus(eventType, status)

        // Then
        StepVerifier.create(result)
            .expectNext(events[0])
            .verifyComplete()

        verify(resilienceEventRepository).findByEventTypeAndStatus(eventType, status)
    }

    @Test
    @DisplayName("Should find recent events with specified limit")
    fun shouldFindRecentEventsWithLimit() {
        // Given
        val limit = 10
        val recentEvents = listOf(
            TestFixtures.createResilienceEvent(eventName = "event-1"),
            TestFixtures.createResilienceEvent(eventName = "event-2")
        )

        whenever(resilienceEventRepository.findRecentEvents(limit))
            .thenReturn(Flux.fromIterable(recentEvents))

        // When
        val result = resilienceEventRepository.findRecentEvents(limit)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(resilienceEventRepository).findRecentEvents(limit)
    }

    @Test
    @DisplayName("Should find events by event type ordered by created at descending")
    fun shouldFindEventsByEventTypeOrderedByCreatedAtDesc() {
        // Given
        val eventType = "CIRCUIT_BREAKER"
        val events = listOf(
            TestFixtures.createResilienceEvent(eventType = eventType)
        )

        whenever(resilienceEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType))
            .thenReturn(Flux.fromIterable(events))

        // When
        val result = resilienceEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType)

        // Then
        StepVerifier.create(result)
            .expectNext(events[0])
            .verifyComplete()

        verify(resilienceEventRepository).findByEventTypeOrderByCreatedAtDesc(eventType)
    }

    @Test
    @DisplayName("Should find event by ID when it exists")
    fun shouldFindEventByIdWhenItExists() {
        // Given
        val eventId = UUID.randomUUID()
        val event = TestFixtures.createResilienceEvent(id = eventId)

        whenever(resilienceEventRepository.findById(eventId))
            .thenReturn(Mono.just(event))

        // When
        val result = resilienceEventRepository.findById(eventId)

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
        val result = resilienceEventRepository.findById(eventId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(resilienceEventRepository).findById(eventId)
    }

    @Test
    @DisplayName("Should delete event by ID")
    fun shouldDeleteEventById() {
        // Given
        val eventId = UUID.randomUUID()

        whenever(resilienceEventRepository.deleteById(eventId))
            .thenReturn(Mono.empty())

        // When
        val result = resilienceEventRepository.deleteById(eventId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(resilienceEventRepository).deleteById(eventId)
    }

    @Test
    @DisplayName("Should return empty Flux when no events found")
    fun shouldReturnEmptyFluxWhenNoEventsFound() {
        // Given
        val eventType = "NONEXISTENT_TYPE"

        whenever(resilienceEventRepository.findByEventType(eventType))
            .thenReturn(Flux.empty())

        // When
        val result = resilienceEventRepository.findByEventType(eventType)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(resilienceEventRepository).findByEventType(eventType)
    }
}
