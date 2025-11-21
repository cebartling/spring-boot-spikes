package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.CartStateHistoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Cart History", description = "Shopping cart state history and event tracking APIs")
class CartStateHistoryController(
    private val cartStateHistoryService: CartStateHistoryService
) {

    /**
     * Get cart history (all events)
     */
    @Operation(summary = "Get cart history", description = "Retrieves all state change events for a shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart history retrieved successfully")
    ])
    @GetMapping
    fun getCartHistory(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findCartHistory(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get recent cart events
     */
    @Operation(summary = "Get recent cart events", description = "Retrieves cart events from the last N hours")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Recent events retrieved successfully")
    ])
    @GetMapping("/recent")
    fun getRecentCartEvents(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Hours to look back (default: 24)") @RequestParam(defaultValue = "24") hoursBack: Long
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findRecentCartEvents(cartId, hoursBack)
            .map { it.toResponse() }
    }

    /**
     * Get events by type
     */
    @Operation(summary = "Get events by type", description = "Retrieves all cart events of a specific type")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Events retrieved successfully")
    ])
    @GetMapping("/type/{eventType}")
    fun getEventsByType(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Event type") @PathVariable eventType: CartEventType
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findEventsByType(cartId, eventType)
            .map { it.toResponse() }
    }

    /**
     * Get most recent event
     */
    @Operation(summary = "Get latest event", description = "Retrieves the most recent event for a shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Latest event retrieved", content = [Content(schema = Schema(implementation = CartStateHistoryResponse::class))]),
        ApiResponse(responseCode = "404", description = "No events found")
    ])
    @GetMapping("/latest")
    fun getMostRecentEvent(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<CartStateHistoryResponse> {
        return cartStateHistoryService.findMostRecentEvent(cartId)
            .map { it.toResponse() }
    }

    /**
     * Count events by type
     */
    @Operation(summary = "Count events by type", description = "Returns the count of events of a specific type for a cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Event count retrieved successfully")
    ])
    @GetMapping("/count/{eventType}")
    fun countEventsByType(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Event type") @PathVariable eventType: CartEventType
    ): Mono<Long> {
        return cartStateHistoryService.countEventsByType(cartId, eventType)
    }

    /**
     * Count total events
     */
    @Operation(summary = "Count total events", description = "Returns the total count of all events for a cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Total event count retrieved successfully")
    ])
    @GetMapping("/count")
    fun countCartEvents(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<Long> {
        return cartStateHistoryService.countCartEvents(cartId)
    }

    /**
     * Get cart activity summary
     */
    @Operation(summary = "Get cart activity summary", description = "Retrieves aggregated activity statistics for a cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Activity summary retrieved successfully", content = [Content(schema = Schema(implementation = CartActivitySummaryResponse::class))])
    ])
    @GetMapping("/summary")
    fun getCartActivitySummary(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<CartActivitySummaryResponse> {
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
@Tag(name = "Cart Analytics", description = "Shopping cart analytics and conversion tracking APIs")
class CartAnalyticsController(
    private val cartStateHistoryService: CartStateHistoryService
) {

    /**
     * Get events within date range
     */
    @Operation(summary = "Get events in date range", description = "Retrieves all cart events within a specified date range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Events retrieved successfully")
    ])
    @GetMapping("/events")
    fun getEventsInDateRange(
        @Parameter(description = "Start date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @Parameter(description = "End date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findEventsInDateRange(startDate, endDate)
            .map { it.toResponse() }
    }

    /**
     * Get conversion events within date range
     */
    @Operation(summary = "Get conversion events", description = "Retrieves cart conversion events within a date range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Conversion events retrieved successfully")
    ])
    @GetMapping("/conversions")
    fun getConversionEvents(
        @Parameter(description = "Start date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @Parameter(description = "End date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findConversionEvents(startDate, endDate)
            .map { it.toResponse() }
    }

    /**
     * Get abandonment events within date range
     */
    @Operation(summary = "Get abandonment events", description = "Retrieves cart abandonment events within a date range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Abandonment events retrieved successfully")
    ])
    @GetMapping("/abandonments")
    fun getAbandonmentEvents(
        @Parameter(description = "Start date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @Parameter(description = "End date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
    ): Flux<CartStateHistoryResponse> {
        return cartStateHistoryService.findAbandonmentEvents(startDate, endDate)
            .map { it.toResponse() }
    }

    /**
     * Calculate conversion rate
     */
    @Operation(summary = "Calculate conversion rate", description = "Calculates the cart conversion rate for a date range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Conversion rate calculated successfully", content = [Content(schema = Schema(implementation = ConversionRateResponse::class))])
    ])
    @GetMapping("/conversion-rate")
    fun getConversionRate(
        @Parameter(description = "Start date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @Parameter(description = "End date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
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
    @Operation(summary = "Calculate abandonment rate", description = "Calculates the cart abandonment rate for a date range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Abandonment rate calculated successfully", content = [Content(schema = Schema(implementation = AbandonmentRateResponse::class))])
    ])
    @GetMapping("/abandonment-rate")
    fun getAbandonmentRate(
        @Parameter(description = "Start date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: OffsetDateTime,
        @Parameter(description = "End date (ISO 8601 format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: OffsetDateTime
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
