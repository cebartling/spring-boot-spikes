package com.pintailconsultingllc.resiliencyspike.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import com.pintailconsultingllc.resiliencyspike.repository.CartStateHistoryRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("CartStateHistoryService Tests")
class CartStateHistoryServiceTest {

    @Mock
    private lateinit var cartStateHistoryRepository: CartStateHistoryRepository

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var cartStateHistoryService: CartStateHistoryService

    @Test
    @DisplayName("Should record cart event")
    fun testRecordEvent() {
        // Given
        val cartId = 1L
        val eventType = CartEventType.ITEM_ADDED
        val eventData = mapOf("product_id" to "123", "quantity" to "2")
        val history = TestFixtures.createCartStateHistory(cartId = cartId, eventType = eventType)

        whenever(objectMapper.writeValueAsString(eventData)).thenReturn("""{"product_id":"123","quantity":"2"}""")
        whenever(cartStateHistoryRepository.save(any())).thenReturn(Mono.just(history))

        // When
        val result = cartStateHistoryService.recordEvent(cartId, eventType, eventData)

        // Then
        StepVerifier.create(result)
            .expectNext(history)
            .verifyComplete()

        verify(cartStateHistoryRepository).save(any())
    }

    @Test
    @DisplayName("Should record status change event")
    fun testRecordStatusChange() {
        // Given
        val cartId = 1L
        val eventType = CartEventType.CONVERTED
        val previousStatus = "ACTIVE"
        val newStatus = "CONVERTED"
        val history = TestFixtures.createCartStateHistory(
            cartId = cartId,
            eventType = eventType,
            previousStatus = previousStatus,
            newStatus = newStatus
        )

        whenever(cartStateHistoryRepository.save(any())).thenReturn(Mono.just(history))

        // When
        val result = cartStateHistoryService.recordStatusChange(cartId, eventType, previousStatus, newStatus)

        // Then
        StepVerifier.create(result)
            .expectNext(history)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find cart history")
    fun testFindCartHistory() {
        // Given
        val cartId = 1L
        val event1 = TestFixtures.createCartStateHistory(id = 1L, cartId = cartId, eventType = CartEventType.CREATED)
        val event2 = TestFixtures.createCartStateHistory(id = 2L, cartId = cartId, eventType = CartEventType.ITEM_ADDED)

        whenever(cartStateHistoryRepository.findByCartIdOrderByCreatedAtDesc(cartId))
            .thenReturn(Flux.just(event1, event2))

        // When
        val result = cartStateHistoryService.findCartHistory(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(event1, event2)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find recent cart events")
    fun testFindRecentCartEvents() {
        // Given
        val cartId = 1L
        val hoursBack = 24L
        val recentEvent = TestFixtures.createCartStateHistory(cartId = cartId)

        whenever(cartStateHistoryRepository.findRecentEvents(any(), any()))
            .thenReturn(Flux.just(recentEvent))

        // When
        val result = cartStateHistoryService.findRecentCartEvents(cartId, hoursBack)

        // Then
        StepVerifier.create(result)
            .expectNext(recentEvent)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should count events by type")
    fun testCountEventsByType() {
        // Given
        val cartId = 1L
        val eventType = CartEventType.ITEM_ADDED
        whenever(cartStateHistoryRepository.countByCartIdAndEventType(cartId, eventType))
            .thenReturn(Mono.just(5L))

        // When
        val result = cartStateHistoryService.countEventsByType(cartId, eventType)

        // Then
        StepVerifier.create(result)
            .expectNext(5L)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should calculate conversion rate")
    fun testCalculateConversionRate() {
        // Given
        val startDate = OffsetDateTime.now().minusDays(7)
        val endDate = OffsetDateTime.now()

        val createdEvent1 = TestFixtures.createCartStateHistory(eventType = CartEventType.CREATED)
        val createdEvent2 = TestFixtures.createCartStateHistory(eventType = CartEventType.CREATED)
        val convertedEvent = TestFixtures.createCartStateHistory(eventType = CartEventType.CONVERTED)

        whenever(cartStateHistoryRepository.findEventsInDateRange(startDate, endDate))
            .thenReturn(Flux.just(createdEvent1, createdEvent2))
        whenever(cartStateHistoryRepository.findConversionEvents(startDate, endDate))
            .thenReturn(Flux.just(convertedEvent))

        // When
        val result = cartStateHistoryService.calculateConversionRate(startDate, endDate)

        // Then
        StepVerifier.create(result)
            .expectNext(50.0) // 1 converted out of 2 created = 50%
            .verifyComplete()
    }

    @Test
    @DisplayName("Should calculate abandonment rate")
    fun testCalculateAbandonmentRate() {
        // Given
        val startDate = OffsetDateTime.now().minusDays(7)
        val endDate = OffsetDateTime.now()

        val createdEvent1 = TestFixtures.createCartStateHistory(eventType = CartEventType.CREATED)
        val createdEvent2 = TestFixtures.createCartStateHistory(eventType = CartEventType.CREATED)
        val createdEvent3 = TestFixtures.createCartStateHistory(eventType = CartEventType.CREATED)
        val createdEvent4 = TestFixtures.createCartStateHistory(eventType = CartEventType.CREATED)
        val abandonedEvent = TestFixtures.createCartStateHistory(eventType = CartEventType.ABANDONED)

        whenever(cartStateHistoryRepository.findEventsInDateRange(startDate, endDate))
            .thenReturn(Flux.just(createdEvent1, createdEvent2, createdEvent3, createdEvent4))
        whenever(cartStateHistoryRepository.findAbandonmentEvents(startDate, endDate))
            .thenReturn(Flux.just(abandonedEvent))

        // When
        val result = cartStateHistoryService.calculateAbandonmentRate(startDate, endDate)

        // Then
        StepVerifier.create(result)
            .expectNext(25.0) // 1 abandoned out of 4 created = 25%
            .verifyComplete()
    }
}
