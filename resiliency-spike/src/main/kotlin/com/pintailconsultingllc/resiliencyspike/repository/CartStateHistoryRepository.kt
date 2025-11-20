package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartStateHistory
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * Reactive repository for CartStateHistory entities
 */
@Repository
interface CartStateHistoryRepository : ReactiveCrudRepository<CartStateHistory, Long> {

    /**
     * Find all history events for a specific cart
     */
    fun findByCartId(cartId: Long): Flux<CartStateHistory>

    /**
     * Find all history events for a cart, ordered by creation time
     */
    @Query("SELECT * FROM cart_state_history WHERE cart_id = :cartId ORDER BY created_at DESC")
    fun findByCartIdOrderByCreatedAtDesc(cartId: Long): Flux<CartStateHistory>

    /**
     * Find history events by event type
     */
    fun findByEventType(eventType: CartEventType): Flux<CartStateHistory>

    /**
     * Find history events for a cart by event type
     */
    fun findByCartIdAndEventType(cartId: Long, eventType: CartEventType): Flux<CartStateHistory>

    /**
     * Find recent events for a cart (within a time window)
     */
    @Query("""
        SELECT * FROM cart_state_history
        WHERE cart_id = :cartId
        AND created_at >= :since
        ORDER BY created_at DESC
    """)
    fun findRecentEvents(cartId: Long, since: OffsetDateTime): Flux<CartStateHistory>

    /**
     * Find events within a date range
     */
    @Query("""
        SELECT * FROM cart_state_history
        WHERE created_at >= :startDate
        AND created_at <= :endDate
        ORDER BY created_at DESC
    """)
    fun findEventsInDateRange(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<CartStateHistory>

    /**
     * Find the most recent event for a cart
     */
    @Query("""
        SELECT * FROM cart_state_history
        WHERE cart_id = :cartId
        ORDER BY created_at DESC
        LIMIT 1
    """)
    fun findMostRecentEvent(cartId: Long): Mono<CartStateHistory>

    /**
     * Count events by type for a cart
     */
    fun countByCartIdAndEventType(cartId: Long, eventType: CartEventType): Mono<Long>

    /**
     * Count total events for a cart
     */
    fun countByCartId(cartId: Long): Mono<Long>

    /**
     * Find conversion events within a date range
     */
    @Query("""
        SELECT * FROM cart_state_history
        WHERE event_type = 'CONVERTED'
        AND created_at >= :startDate
        AND created_at <= :endDate
        ORDER BY created_at DESC
    """)
    fun findConversionEvents(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<CartStateHistory>

    /**
     * Find abandoned cart events within a date range
     */
    @Query("""
        SELECT * FROM cart_state_history
        WHERE event_type = 'ABANDONED'
        AND created_at >= :startDate
        AND created_at <= :endDate
        ORDER BY created_at DESC
    """)
    fun findAbandonmentEvents(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<CartStateHistory>
}
