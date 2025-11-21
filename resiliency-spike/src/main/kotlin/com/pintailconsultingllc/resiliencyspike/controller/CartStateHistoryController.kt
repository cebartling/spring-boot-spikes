package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.CartStateHistoryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * REST controller for cart state history and analytics operations
 */
@RestController
@RequestMapping("/api/v1/carts/{cartId}/history")
class CartStateHistoryController(
    private val cartStateHistoryService: CartStateHistoryService
) {

    /**
     * Get cart history (all events)
     */
    @GetMapping
    fun getCartHistory(@PathVariable cartId: Long): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findCartHistory(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get recent cart events
     */
    @GetMapping("/recent")
    fun getRecentCartEvents(
        @PathVariable cartId: Long,
        @RequestParam(defaultValue = "24") hoursBack: Long
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findRecentCartEvents(cartId, hoursBack)
            .map { it.toResponse() }
    }

    /**
     * Get events by type
     */
    @GetMapping("/type/{eventType}")
    fun getEventsByType(
        @PathVariable cartId: Long,
        @PathVariable eventType: CartEventType
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findEventsByType(cartId, eventType)
            .map { it.toResponse() }
    }

    /**
     * Get most recent event
     */
    @GetMapping("/latest")
    fun getMostRecentEvent(@PathVariable cartId: Long): Mono<CartStateHistoryResponse> {
        return cartStateHistoryService.findMostRecentEvent(cartId)
            .map { it.toResponse() }
    }

    /**
     * Count events by type
     */
    @GetMapping("/count/{eventType}")
    fun countEventsByType(
        @PathVariable cartId: Long,
        @PathVariable eventType: CartEventType
    ): Mono<Long> {
        return cartStateHistoryService.countEventsByType(cartId, eventType)
    }

    /**
     * Count total events
     */
    @GetMapping("/count")
    fun countCartEvents(@PathVariable cartId: Long): Mono<Long> {
        return cartStateHistoryService.countCartEvents(cartId)
    }

    /**
     * Get cart activity summary
     */
    @GetMapping("/summary")
    fun getCartActivitySummary(@PathVariable cartId: Long): Mono<CartActivitySummaryResponse> {
        return Mono.zip(
            cartStateHistoryService.getCartActivitySummary(cartId),
            cartStateHistoryService.countCartEvents(cartId)
        ).map { tuple ->
            CartActivitySummaryResponse(
                cartId = cartId,
                eventCounts = tuple.t1,
                totalEvents = tuple.t2
            )
        }
    }
}

/**
 * REST controller for cart analytics operations
 */
@RestController
@RequestMapping("/api/v1/analytics/carts")
class CartAnalyticsController(
    private val cartStateHistoryService: CartStateHistoryService
) {

    /**
     * Get events within date range
     */
    @GetMapping("/events")
    fun getEventsInDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findEventsInDateRange(startDate, endDate)
            .map { it.toResponse() }
    }

    /**
     * Get conversion events within date range
     */
    @GetMapping("/conversions")
    fun getConversionEvents(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findConversionEvents(startDate, endDate)
            .map { it.toResponse() }
    }

    /**
     * Get abandonment events within date range
     */
    @GetMapping("/abandonments")
    fun getAbandonmentEvents(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findAbandonmentEvents(startDate, endDate)
            .map { it.toResponse() }
    }

    /**
     * Calculate conversion rate
     */
    @GetMapping("/conversion-rate")
    fun getConversionRate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Mono<ConversionRateResponse> {
        return Mono.zip(
            cartStateHistoryService.calculateConversionRate(startDate, endDate),
            cartStateHistoryService.findEventsInDateRange(startDate, endDate)
                .filter { it.eventType == CartEventType.CREATED }
                .count(),
            cartStateHistoryService.findConversionEvents(startDate, endDate).count()
        ).map { tuple ->
            ConversionRateResponse(
                startDate = startDate,
                endDate = endDate,
                conversionRate = tuple.t1,
                totalCreated = tuple.t2,
                totalConverted = tuple.t3
            )
        }
    }

    /**
     * Calculate abandonment rate
     */
    @GetMapping("/abandonment-rate")
    fun getAbandonmentRate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Mono<AbandonmentRateResponse> {
        return Mono.zip(
            cartStateHistoryService.calculateAbandonmentRate(startDate, endDate),
            cartStateHistoryService.findEventsInDateRange(startDate, endDate)
                .filter { it.eventType == CartEventType.CREATED }
                .count(),
            cartStateHistoryService.findAbandonmentEvents(startDate, endDate).count()
        ).map { tuple ->
            AbandonmentRateResponse(
                startDate = startDate,
                endDate = endDate,
                abandonmentRate = tuple.t1,
                totalCreated = tuple.t2,
                totalAbandoned = tuple.t3
            )
        }
    }
}
