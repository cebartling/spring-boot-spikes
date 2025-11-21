package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartStateHistory
import com.pintailconsultingllc.resiliencyspike.service.CartStateHistoryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@ExtendWith(SpringExtension::class, MockitoExtension::class)
@WebFluxTest(CartStateHistoryController::class)
@DisplayName("CartStateHistoryController API Contract Tests")
class CartStateHistoryControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var cartStateHistoryService: CartStateHistoryService

    private val testHistoryEvent = CartStateHistory(
        id = 1L,
        cartId = 100L,
        eventType = CartEventType.CREATED,
        previousStatus = null,
        newStatus = "ACTIVE",
        eventData = null,
        createdAt = OffsetDateTime.now()
    )

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history - should get all cart history")
    fun testGetCartHistory() {
        whenever(cartStateHistoryService.findCartHistory(100L))
            .thenReturn(Flux.just(testHistoryEvent))

        webTestClient.get()
            .uri("/api/v1/carts/100/history")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartStateHistoryService).findCartHistory(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/recent - should get recent cart events")
    fun testGetRecentCartEvents() {
        whenever(cartStateHistoryService.findRecentCartEvents(100L, 24L))
            .thenReturn(Flux.just(testHistoryEvent))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/recent")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartStateHistoryService).findRecentCartEvents(100L, 24L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/recent?hoursBack=48 - should get recent events with custom hours")
    fun testGetRecentCartEventsWithCustomHours() {
        whenever(cartStateHistoryService.findRecentCartEvents(100L, 48L))
            .thenReturn(Flux.just(testHistoryEvent))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/recent?hoursBack=48")
            .exchange()
            .expectStatus().isOk

        verify(cartStateHistoryService).findRecentCartEvents(100L, 48L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/type/{eventType} - should get events by type")
    fun testGetEventsByType() {
        val itemAddedEvent = testHistoryEvent.copy(
            id = 2L,
            eventType = CartEventType.ITEM_ADDED
        )

        whenever(cartStateHistoryService.findEventsByType(100L, CartEventType.ITEM_ADDED))
            .thenReturn(Flux.just(itemAddedEvent))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/type/ITEM_ADDED")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartStateHistoryService).findEventsByType(100L, CartEventType.ITEM_ADDED)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/latest - should get most recent event")
    fun testGetMostRecentEvent() {
        whenever(cartStateHistoryService.findMostRecentEvent(100L))
            .thenReturn(Mono.just(testHistoryEvent))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/latest")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.cartId").isEqualTo(100)
            .jsonPath("$.eventType").isEqualTo("CREATED")

        verify(cartStateHistoryService).findMostRecentEvent(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/count/{eventType} - should count events by type")
    fun testCountEventsByType() {
        whenever(cartStateHistoryService.countEventsByType(100L, CartEventType.ITEM_ADDED))
            .thenReturn(Mono.just(5L))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/count/ITEM_ADDED")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isEqualTo(5)

        verify(cartStateHistoryService).countEventsByType(100L, CartEventType.ITEM_ADDED)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/count - should count total events")
    fun testCountCartEvents() {
        whenever(cartStateHistoryService.countCartEvents(100L))
            .thenReturn(Mono.just(10L))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/count")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isEqualTo(10)

        verify(cartStateHistoryService).countCartEvents(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history/summary - should get cart activity summary")
    fun testGetCartActivitySummary() {
        val eventCounts = mapOf(
            CartEventType.CREATED to 1L,
            CartEventType.ITEM_ADDED to 3L,
            CartEventType.ITEM_REMOVED to 1L,
            CartEventType.CONVERTED to 1L
        )

        whenever(cartStateHistoryService.getCartActivitySummary(100L))
            .thenReturn(Mono.just(eventCounts))
        whenever(cartStateHistoryService.countCartEvents(100L))
            .thenReturn(Mono.just(6L))

        webTestClient.get()
            .uri("/api/v1/carts/100/history/summary")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.cartId").isEqualTo(100)
            .jsonPath("$.totalEvents").isEqualTo(6)
            .jsonPath("$.eventCounts.CREATED").isEqualTo(1)
            .jsonPath("$.eventCounts.ITEM_ADDED").isEqualTo(3)
            .jsonPath("$.eventCounts.ITEM_REMOVED").isEqualTo(1)
            .jsonPath("$.eventCounts.CONVERTED").isEqualTo(1)

        verify(cartStateHistoryService).getCartActivitySummary(100L)
        verify(cartStateHistoryService).countCartEvents(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history - should return empty list when no history")
    fun testGetCartHistoryEmpty() {
        whenever(cartStateHistoryService.findCartHistory(100L))
            .thenReturn(Flux.empty())

        webTestClient.get()
            .uri("/api/v1/carts/100/history")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(0)

        verify(cartStateHistoryService).findCartHistory(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/history - should return multiple events in order")
    fun testGetCartHistoryMultipleEvents() {
        val event1 = testHistoryEvent
        val event2 = testHistoryEvent.copy(
            id = 2L,
            eventType = CartEventType.ITEM_ADDED,
            createdAt = OffsetDateTime.now().plusMinutes(5)
        )
        val event3 = testHistoryEvent.copy(
            id = 3L,
            eventType = CartEventType.CONVERTED,
            previousStatus = "ACTIVE",
            newStatus = "CONVERTED",
            createdAt = OffsetDateTime.now().plusMinutes(10)
        )

        whenever(cartStateHistoryService.findCartHistory(100L))
            .thenReturn(Flux.just(event1, event2, event3))

        webTestClient.get()
            .uri("/api/v1/carts/100/history")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(3)

        verify(cartStateHistoryService).findCartHistory(100L)
    }
}
