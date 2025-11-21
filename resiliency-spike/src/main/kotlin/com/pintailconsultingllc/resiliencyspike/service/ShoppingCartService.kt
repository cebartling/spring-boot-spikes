package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart
import com.pintailconsultingllc.resiliencyspike.repository.ShoppingCartRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.*

/**
 * Service for managing shopping carts
 * Demonstrates reactive database operations with R2DBC for shopping cart functionality
 * Circuit breaker protection and retry logic applied to database operations
 */
@Service
class ShoppingCartService(
    private val cartRepository: ShoppingCartRepository,
    private val cartStateHistoryService: CartStateHistoryService
) {

    private val logger = LoggerFactory.getLogger(ShoppingCartService::class.java)

    /**
     * Create a new shopping cart
     */
    @Retry(name = "shoppingCart", fallbackMethod = "createCartFallback")
    @CircuitBreaker(name = "shoppingCart", fallbackMethod = "createCartFallback")
    fun createCart(sessionId: String, userId: String? = null, expiresAt: OffsetDateTime? = null): Mono<ShoppingCart> {
        val cart = ShoppingCart(
            sessionId = sessionId,
            userId = userId,
            expiresAt = expiresAt ?: OffsetDateTime.now().plusDays(7) // Default 7-day expiration
        )
        return cartRepository.save(cart)
            .flatMap { savedCart ->
                cartStateHistoryService.recordEvent(savedCart.id!!, CartEventType.CREATED)
                    .thenReturn(savedCart)
            }
    }

    private fun createCartFallback(sessionId: String, userId: String?, expiresAt: OffsetDateTime?, ex: Exception): Mono<ShoppingCart> {
        logger.error("Circuit breaker fallback for createCart - sessionId: $sessionId, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Shopping cart service is temporarily unavailable. Please try again later.", ex))
    }

    /**
     * Find or create a cart for a session
     */
    @Retry(name = "shoppingCart", fallbackMethod = "findOrCreateCartFallback")
    @CircuitBreaker(name = "shoppingCart", fallbackMethod = "findOrCreateCartFallback")
    fun findOrCreateCart(sessionId: String, userId: String? = null): Mono<ShoppingCart> {
        return cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE)
            .switchIfEmpty(createCart(sessionId, userId))
    }

    private fun findOrCreateCartFallback(sessionId: String, userId: String?, ex: Exception): Mono<ShoppingCart> {
        logger.error("Retry/Circuit breaker fallback for findOrCreateCart - sessionId: $sessionId, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to retrieve or create cart. Please try again later.", ex))
    }

    /**
     * Find a cart by ID
     */
    @Retry(name = "shoppingCart", fallbackMethod = "findCartByIdFallback")
    @CircuitBreaker(name = "shoppingCart", fallbackMethod = "findCartByIdFallback")
    fun findCartById(cartId: Long): Mono<ShoppingCart> {
        return cartRepository.findById(cartId)
    }

    private fun findCartByIdFallback(cartId: Long, ex: Exception): Mono<ShoppingCart> {
        logger.error("Retry/Circuit breaker fallback for findCartById - cartId: $cartId, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to retrieve cart. Please try again later.", ex))
    }

    /**
     * Find a cart by UUID
     */
    @Retry(name = "shoppingCart", fallbackMethod = "findCartByUuidFallback")
    @CircuitBreaker(name = "shoppingCart", fallbackMethod = "findCartByUuidFallback")
    fun findCartByUuid(cartUuid: UUID): Mono<ShoppingCart> {
        return cartRepository.findByCartUuid(cartUuid)
    }

    private fun findCartByUuidFallback(cartUuid: UUID, ex: Exception): Mono<ShoppingCart> {
        logger.error("Retry/Circuit breaker fallback for findCartByUuid - cartUuid: $cartUuid, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to retrieve cart. Please try again later.", ex))
    }

    /**
     * Find a cart by session ID
     */
    fun findCartBySessionId(sessionId: String): Mono<ShoppingCart> {
        return cartRepository.findBySessionId(sessionId)
    }

    /**
     * Find active cart by session ID
     */
    fun findActiveCartBySessionId(sessionId: String): Mono<ShoppingCart> {
        return cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE)
    }

    /**
     * Find all carts for a user
     */
    fun findCartsByUserId(userId: String): Flux<ShoppingCart> {
        return cartRepository.findByUserId(userId)
    }

    /**
     * Find active cart for a user
     */
    fun findActiveCartByUserId(userId: String): Mono<ShoppingCart> {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
    }

    /**
     * Update cart status
     */
    @Retry(name = "shoppingCart", fallbackMethod = "updateCartStatusFallback")
    @CircuitBreaker(name = "shoppingCart", fallbackMethod = "updateCartStatusFallback")
    fun updateCartStatus(cartId: Long, newStatus: CartStatus): Mono<ShoppingCart> {
        return cartRepository.findById(cartId)
            .flatMap { cart ->
                val previousStatus = cart.status
                val updatedCart = cart.copy(
                    status = newStatus,
                    convertedAt = if (newStatus == CartStatus.CONVERTED) OffsetDateTime.now() else cart.convertedAt
                )
                cartRepository.save(updatedCart)
                    .flatMap { savedCart ->
                        val eventType = when (newStatus) {
                            CartStatus.ABANDONED -> CartEventType.ABANDONED
                            CartStatus.CONVERTED -> CartEventType.CONVERTED
                            CartStatus.EXPIRED -> CartEventType.EXPIRED
                            CartStatus.ACTIVE -> CartEventType.RESTORED
                        }
                        cartStateHistoryService.recordStatusChange(
                            savedCart.id!!,
                            eventType,
                            previousStatus.toString(),
                            newStatus.toString()
                        ).thenReturn(savedCart)
                    }
            }
    }

    private fun updateCartStatusFallback(cartId: Long, newStatus: CartStatus, ex: Exception): Mono<ShoppingCart> {
        logger.error("Circuit breaker fallback for updateCartStatus - cartId: $cartId, newStatus: $newStatus, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to update cart status. Please try again later.", ex))
    }

    /**
     * Abandon a cart
     */
    fun abandonCart(cartId: Long): Mono<ShoppingCart> {
        return updateCartStatus(cartId, CartStatus.ABANDONED)
    }

    /**
     * Convert a cart (to an order)
     */
    fun convertCart(cartId: Long): Mono<ShoppingCart> {
        return updateCartStatus(cartId, CartStatus.CONVERTED)
    }

    /**
     * Expire a cart
     */
    fun expireCart(cartId: Long): Mono<ShoppingCart> {
        return updateCartStatus(cartId, CartStatus.EXPIRED)
    }

    /**
     * Restore a cart to active status
     */
    fun restoreCart(cartId: Long): Mono<ShoppingCart> {
        return updateCartStatus(cartId, CartStatus.ACTIVE)
    }

    /**
     * Find expired carts
     */
    fun findExpiredCarts(): Flux<ShoppingCart> {
        return cartRepository.findExpiredCarts(OffsetDateTime.now())
    }

    /**
     * Find abandoned carts (not updated in specified hours)
     */
    fun findAbandonedCarts(hoursInactive: Long = 24): Flux<ShoppingCart> {
        val cutoffTime = OffsetDateTime.now().minusHours(hoursInactive)
        return cartRepository.findAbandonedCarts(cutoffTime)
    }

    /**
     * Process expired carts (mark them as expired)
     */
    fun processExpiredCarts(): Mono<Long> {
        return findExpiredCarts()
            .flatMap { cart -> expireCart(cart.id!!) }
            .count()
    }

    /**
     * Process abandoned carts (mark them as abandoned)
     */
    fun processAbandonedCarts(hoursInactive: Long = 24): Mono<Long> {
        return findAbandonedCarts(hoursInactive)
            .flatMap { cart -> abandonCart(cart.id!!) }
            .count()
    }

    /**
     * Update cart expiration time
     */
    fun updateCartExpiration(cartId: Long, expiresAt: OffsetDateTime): Mono<ShoppingCart> {
        return cartRepository.findById(cartId)
            .flatMap { cart ->
                cartRepository.save(cart.copy(expiresAt = expiresAt))
            }
    }

    /**
     * Associate cart with user (for guest-to-user conversion)
     */
    fun associateCartWithUser(cartId: Long, userId: String): Mono<ShoppingCart> {
        return cartRepository.findById(cartId)
            .flatMap { cart ->
                cartRepository.save(cart.copy(userId = userId))
            }
    }

    /**
     * Find carts by status
     */
    fun findCartsByStatus(status: CartStatus): Flux<ShoppingCart> {
        return cartRepository.findByStatus(status)
    }

    /**
     * Find carts with items
     */
    fun findCartsWithItems(status: CartStatus = CartStatus.ACTIVE): Flux<ShoppingCart> {
        return cartRepository.findCartsWithItems(status)
    }

    /**
     * Find empty carts
     */
    fun findEmptyCarts(): Flux<ShoppingCart> {
        return cartRepository.findEmptyCarts()
    }

    /**
     * Count carts by status
     */
    fun countCartsByStatus(status: CartStatus): Mono<Long> {
        return cartRepository.countByStatus(status)
    }

    /**
     * Delete a cart permanently
     */
    fun deleteCart(cartId: Long): Mono<Void> {
        return cartRepository.deleteById(cartId)
    }

    /**
     * Find carts created within date range
     */
    fun findCartsCreatedBetween(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<ShoppingCart> {
        return cartRepository.findCartsCreatedBetween(startDate, endDate)
    }

    /**
     * Find carts converted within date range
     */
    fun findCartsConvertedBetween(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<ShoppingCart> {
        return cartRepository.findCartsConvertedBetween(startDate, endDate)
    }
}
