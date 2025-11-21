package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartStateHistory
import com.pintailconsultingllc.resiliencyspike.service.CartStateHistoryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
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
import java.time.format.DateTimeFormatter

@ExtendWith(SpringExtension::class, MockitoExtension::class)
@WebFluxTest(CartAnalyticsController::class)
@DisplayName("CartAnalyticsController API Contract Tests")
class CartAnalyticsControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var cartStateHistoryService: CartStateHistoryService

    private val startDate = OffsetDateTime.parse("2025-01-01T00:00:00Z")
    private val endDate = OffsetDateTime.parse("2025-01-31T23:59:59Z")

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private fun createHistoryEvent(
        id: Long,
        cartId: Long,
        eventType: CartEventType,
        createdAt: OffsetDateTime = OffsetDateTime.now()
    ) = CartStateHistory(
        id = id,
        cartId = cartId,
        eventType = eventType,
        previousStatus = null,
        newStatus = null,
        eventData = null,
        createdAt = createdAt
    )

    @Test
    @DisplayName("GET /api/v1/analytics/carts/events - should get events in date range")
    fun testGetEventsInDateRange() {
        val event1 = createHistoryEvent(1L, 100L, CartEventType.CREATED, startDate.plusDays(1))
        val event2 = createHistoryEvent(2L, 101L, CartEventType.CREATED, startDate.plusDays(2))

        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(Flux.just(event1, event2))

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/events")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(2)

        verify(cartStateHistoryService).findEventsInDateRange(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/conversions - should get conversion events")
    fun testGetConversionEvents() {
        val conversionEvent = createHistoryEvent(1L, 100L, CartEventType.CONVERTED, startDate.plusDays(5))

        whenever(cartStateHistoryService.findConversionEvents(any(), any()))
            .thenReturn(Flux.just(conversionEvent))

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/conversions")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartStateHistoryService).findConversionEvents(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/abandonments - should get abandonment events")
    fun testGetAbandonmentEvents() {
        val abandonmentEvent = createHistoryEvent(1L, 100L, CartEventType.ABANDONED, startDate.plusDays(3))

        whenever(cartStateHistoryService.findAbandonmentEvents(any(), any()))
            .thenReturn(Flux.just(abandonmentEvent))

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/abandonments")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartStateHistoryService).findAbandonmentEvents(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/conversion-rate - should calculate conversion rate")
    fun testGetConversionRate() {
        // Mock the three Mono calls
        whenever(cartStateHistoryService.calculateConversionRate(any(), any()))
            .thenReturn(Mono.just(0.25)) // 25% conversion rate

        val createdEvents = Flux.just(
            createHistoryEvent(1L, 100L, CartEventType.CREATED),
            createHistoryEvent(2L, 101L, CartEventType.CREATED),
            createHistoryEvent(3L, 102L, CartEventType.CREATED),
            createHistoryEvent(4L, 103L, CartEventType.CREATED)
        )

        val conversionEvents = Flux.just(
            createHistoryEvent(5L, 100L, CartEventType.CONVERTED)
        )

        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(createdEvents)
        whenever(cartStateHistoryService.findConversionEvents(any(), any()))
            .thenReturn(conversionEvents)

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/conversion-rate")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.conversionRate").isEqualTo(0.25)
            .jsonPath("$.totalCreated").isEqualTo(4)
            .jsonPath("$.totalConverted").isEqualTo(1)
            .jsonPath("$.startDate").exists()
            .jsonPath("$.endDate").exists()

        verify(cartStateHistoryService).calculateConversionRate(any(), any())
        verify(cartStateHistoryService).findEventsInDateRange(any(), any())
        verify(cartStateHistoryService).findConversionEvents(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/conversion-rate - should handle zero conversions")
    fun testGetConversionRateZero() {
        whenever(cartStateHistoryService.calculateConversionRate(any(), any()))
            .thenReturn(Mono.just(0.0))

        val createdEvents = Flux.just(
            createHistoryEvent(1L, 100L, CartEventType.CREATED),
            createHistoryEvent(2L, 101L, CartEventType.CREATED)
        )

        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(createdEvents)
        whenever(cartStateHistoryService.findConversionEvents(any(), any()))
            .thenReturn(Flux.empty())

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/conversion-rate")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.conversionRate").isEqualTo(0.0)
            .jsonPath("$.totalCreated").isEqualTo(2)
            .jsonPath("$.totalConverted").isEqualTo(0)

        verify(cartStateHistoryService).calculateConversionRate(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/abandonment-rate - should calculate abandonment rate")
    fun testGetAbandonmentRate() {
        whenever(cartStateHistoryService.calculateAbandonmentRate(any(), any()))
            .thenReturn(Mono.just(0.5)) // 50% abandonment rate

        val createdEvents = Flux.just(
            createHistoryEvent(1L, 100L, CartEventType.CREATED),
            createHistoryEvent(2L, 101L, CartEventType.CREATED),
            createHistoryEvent(3L, 102L, CartEventType.CREATED),
            createHistoryEvent(4L, 103L, CartEventType.CREATED)
        )

        val abandonmentEvents = Flux.just(
            createHistoryEvent(5L, 100L, CartEventType.ABANDONED),
            createHistoryEvent(6L, 101L, CartEventType.ABANDONED)
        )

        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(createdEvents)
        whenever(cartStateHistoryService.findAbandonmentEvents(any(), any()))
            .thenReturn(abandonmentEvents)

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/abandonment-rate")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.abandonmentRate").isEqualTo(0.5)
            .jsonPath("$.totalCreated").isEqualTo(4)
            .jsonPath("$.totalAbandoned").isEqualTo(2)
            .jsonPath("$.startDate").exists()
            .jsonPath("$.endDate").exists()

        verify(cartStateHistoryService).calculateAbandonmentRate(any(), any())
        verify(cartStateHistoryService).findEventsInDateRange(any(), any())
        verify(cartStateHistoryService).findAbandonmentEvents(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/abandonment-rate - should handle zero abandonments")
    fun testGetAbandonmentRateZero() {
        whenever(cartStateHistoryService.calculateAbandonmentRate(any(), any()))
            .thenReturn(Mono.just(0.0))

        val createdEvents = Flux.just(
            createHistoryEvent(1L, 100L, CartEventType.CREATED),
            createHistoryEvent(2L, 101L, CartEventType.CREATED),
            createHistoryEvent(3L, 102L, CartEventType.CREATED)
        )

        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(createdEvents)
        whenever(cartStateHistoryService.findAbandonmentEvents(any(), any()))
            .thenReturn(Flux.empty())

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/abandonment-rate")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.abandonmentRate").isEqualTo(0.0)
            .jsonPath("$.totalCreated").isEqualTo(3)
            .jsonPath("$.totalAbandoned").isEqualTo(0)

        verify(cartStateHistoryService).calculateAbandonmentRate(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/conversion-rate - should handle 100% conversion")
    fun testGetConversionRatePerfect() {
        whenever(cartStateHistoryService.calculateConversionRate(any(), any()))
            .thenReturn(Mono.just(1.0))

        val createdEvents = Flux.just(
            createHistoryEvent(1L, 100L, CartEventType.CREATED),
            createHistoryEvent(2L, 101L, CartEventType.CREATED)
        )

        val conversionEvents = Flux.just(
            createHistoryEvent(3L, 100L, CartEventType.CONVERTED),
            createHistoryEvent(4L, 101L, CartEventType.CONVERTED)
        )

        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(createdEvents)
        whenever(cartStateHistoryService.findConversionEvents(any(), any()))
            .thenReturn(conversionEvents)

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/conversion-rate")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.conversionRate").isEqualTo(1.0)
            .jsonPath("$.totalCreated").isEqualTo(2)
            .jsonPath("$.totalConverted").isEqualTo(2)

        verify(cartStateHistoryService).calculateConversionRate(any(), any())
    }

    @Test
    @DisplayName("GET /api/v1/analytics/carts/events - should handle empty result set")
    fun testGetEventsInDateRangeEmpty() {
        whenever(cartStateHistoryService.findEventsInDateRange(any(), any()))
            .thenReturn(Flux.empty())

        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/analytics/carts/events")
                    .queryParam("startDate", formatter.format(startDate))
                    .queryParam("endDate", formatter.format(endDate))
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(0)

        verify(cartStateHistoryService).findEventsInDateRange(any(), any())
    }
}
