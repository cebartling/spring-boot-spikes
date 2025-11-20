package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.*

/**
 * Reactive repository for ShoppingCart entities
 */
@Repository
interface ShoppingCartRepository : ReactiveCrudRepository<ShoppingCart, Long> {

    /**
     * Find a cart by its UUID
     */
    fun findByCartUuid(cartUuid: UUID): Mono<ShoppingCart>

    /**
     * Find a cart by session ID
     */
    fun findBySessionId(sessionId: String): Mono<ShoppingCart>

    /**
     * Find all carts for a specific user
     */
    fun findByUserId(userId: String): Flux<ShoppingCart>

    /**
     * Find an active cart by session ID
     */
    fun findBySessionIdAndStatus(sessionId: String, status: CartStatus): Mono<ShoppingCart>

    /**
     * Find an active cart by user ID
     */
    fun findByUserIdAndStatus(userId: String, status: CartStatus): Mono<ShoppingCart>

    /**
     * Find all carts by status
     */
    fun findByStatus(status: CartStatus): Flux<ShoppingCart>

    /**
     * Find carts that have expired (past their expiration time)
     */
    @Query("SELECT * FROM shopping_carts WHERE expires_at < :currentTime AND status = 'ACTIVE'")
    fun findExpiredCarts(currentTime: OffsetDateTime): Flux<ShoppingCart>

    /**
     * Find abandoned carts (inactive for a certain period)
     */
    @Query("""
        SELECT * FROM shopping_carts
        WHERE updated_at < :cutoffTime
        AND status = 'ACTIVE'
        ORDER BY updated_at ASC
    """)
    fun findAbandonedCarts(cutoffTime: OffsetDateTime): Flux<ShoppingCart>

    /**
     * Find carts created within a date range
     */
    @Query("SELECT * FROM shopping_carts WHERE created_at >= :startDate AND created_at <= :endDate ORDER BY created_at DESC")
    fun findCartsCreatedBetween(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<ShoppingCart>

    /**
     * Find carts converted within a date range
     */
    @Query("SELECT * FROM shopping_carts WHERE converted_at >= :startDate AND converted_at <= :endDate ORDER BY converted_at DESC")
    fun findCartsConvertedBetween(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<ShoppingCart>

    /**
     * Count carts by status
     */
    fun countByStatus(status: CartStatus): Mono<Long>

    /**
     * Count active carts for a user
     */
    fun countByUserIdAndStatus(userId: String, status: CartStatus): Mono<Long>

    /**
     * Find carts with items (item count > 0)
     */
    @Query("SELECT * FROM shopping_carts WHERE item_count > 0 AND status = :status")
    fun findCartsWithItems(status: CartStatus): Flux<ShoppingCart>

    /**
     * Find empty carts (item count = 0)
     */
    @Query("SELECT * FROM shopping_carts WHERE item_count = 0 AND status = 'ACTIVE'")
    fun findEmptyCarts(): Flux<ShoppingCart>
}
