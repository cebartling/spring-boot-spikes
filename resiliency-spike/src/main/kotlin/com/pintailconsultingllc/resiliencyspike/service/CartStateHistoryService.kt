package com.pintailconsultingllc.resiliencyspike.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartStateHistory
import com.pintailconsultingllc.resiliencyspike.repository.CartStateHistoryRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * Service for managing cart state history and events
 * Tracks the lifecycle and changes to shopping carts
 */
@Service
class CartStateHistoryService(
    private val cartStateHistoryRepository: CartStateHistoryRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * Record a cart event
     */
    fun recordEvent(
        cartId: Long,
        eventType: CartEventType,
        eventData: Map<String, Any>? = null
    ): Mono<CartStateHistory> {
        val event = CartStateHistory(
            cartId = cartId,
            eventType = eventType,
            eventData = eventData?.let { objectMapper.writeValueAsString(it) }
        )
        return cartStateHistoryRepository.save(event)
    }

    /**
     * Record a status change event
     */
    fun recordStatusChange(
        cartId: Long,
        eventType: CartEventType,
        previousStatus: String,
        newStatus: String,
        eventData: Map<String, Any>? = null
    ): Mono<CartStateHistory> {
        val event = CartStateHistory(
            cartId = cartId,
            eventType = eventType,
            previousStatus = previousStatus,
            newStatus = newStatus,
            eventData = eventData?.let { objectMapper.writeValueAsString(it) }
        )
        return cartStateHistoryRepository.save(event)
    }

    /**
     * Record an item-related event
     */
    fun recordItemEvent(
        cartId: Long,
        eventType: CartEventType,
        itemData: Map<String, String>
    ): Mono<CartStateHistory> {
        return recordEvent(cartId, eventType, itemData)
    }

    /**
     * Find all events for a cart
     */
    fun findCartHistory(cartId: Long): Flux<CartStateHistory> {
        return cartStateHistoryRepository.findByCartIdOrderByCreatedAtDesc(cartId)
    }

    /**
     * Find recent events for a cart (within last N hours)
     */
    fun findRecentCartEvents(cartId: Long, hoursBack: Long = 24): Flux<CartStateHistory> {
        val since = OffsetDateTime.now().minusHours(hoursBack)
        return cartStateHistoryRepository.findRecentEvents(cartId, since)
    }

    /**
     * Find events by type for a cart
     */
    fun findEventsByType(cartId: Long, eventType: CartEventType): Flux<CartStateHistory> {
        return cartStateHistoryRepository.findByCartIdAndEventType(cartId, eventType)
    }

    /**
     * Find the most recent event for a cart
     */
    fun findMostRecentEvent(cartId: Long): Mono<CartStateHistory> {
        return cartStateHistoryRepository.findMostRecentEvent(cartId)
    }

    /**
     * Count events by type for a cart
     */
    fun countEventsByType(cartId: Long, eventType: CartEventType): Mono<Long> {
        return cartStateHistoryRepository.countByCartIdAndEventType(cartId, eventType)
    }

    /**
     * Count total events for a cart
     */
    fun countCartEvents(cartId: Long): Mono<Long> {
        return cartStateHistoryRepository.countByCartId(cartId)
    }

    /**
     * Find all events within a date range
     */
    fun findEventsInDateRange(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<CartStateHistory> {
        return cartStateHistoryRepository.findEventsInDateRange(startDate, endDate)
    }

    /**
     * Find conversion events within a date range
     */
    fun findConversionEvents(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<CartStateHistory> {
        return cartStateHistoryRepository.findConversionEvents(startDate, endDate)
    }

    /**
     * Find abandonment events within a date range
     */
    fun findAbandonmentEvents(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<CartStateHistory> {
        return cartStateHistoryRepository.findAbandonmentEvents(startDate, endDate)
    }

    /**
     * Get cart activity summary (count of each event type)
     */
    fun getCartActivitySummary(cartId: Long): Mono<Map<CartEventType, Long>> {
        return cartStateHistoryRepository.findByCartId(cartId)
            .collectMultimap({ it.eventType }, { it })
            .map { multimap ->
                multimap.mapValues { it.value.size.toLong() }
            }
    }

    /**
     * Find carts with high activity (many events)
     */
    fun findHighActivityCarts(minEventCount: Long, since: OffsetDateTime): Flux<Long> {
        return cartStateHistoryRepository.findEventsInDateRange(since, OffsetDateTime.now())
            .collectMultimap({ it.cartId }, { it })
            .flatMapMany { multimap ->
                Flux.fromIterable(
                    multimap.filter { it.value.size >= minEventCount }
                        .keys
                )
            }
    }

    /**
     * Calculate cart conversion rate for a period
     */
    fun calculateConversionRate(startDate: OffsetDateTime, endDate: OffsetDateTime): Mono<Double> {
        val createdCarts = cartStateHistoryRepository
            .findEventsInDateRange(startDate, endDate)
            .filter { it.eventType == CartEventType.CREATED }
            .count()

        val convertedCarts = cartStateHistoryRepository
            .findConversionEvents(startDate, endDate)
            .count()

        return Mono.zip(createdCarts, convertedCarts)
            .map { (created, converted) ->
                if (created > 0) {
                    (converted.toDouble() / created.toDouble()) * 100.0
                } else {
                    0.0
                }
            }
    }

    /**
     * Calculate cart abandonment rate for a period
     */
    fun calculateAbandonmentRate(startDate: OffsetDateTime, endDate: OffsetDateTime): Mono<Double> {
        val createdCarts = cartStateHistoryRepository
            .findEventsInDateRange(startDate, endDate)
            .filter { it.eventType == CartEventType.CREATED }
            .count()

        val abandonedCarts = cartStateHistoryRepository
            .findAbandonmentEvents(startDate, endDate)
            .count()

        return Mono.zip(createdCarts, abandonedCarts)
            .map { (created, abandoned) ->
                if (created > 0) {
                    (abandoned.toDouble() / created.toDouble()) * 100.0
                } else {
                    0.0
                }
            }
    }
}
