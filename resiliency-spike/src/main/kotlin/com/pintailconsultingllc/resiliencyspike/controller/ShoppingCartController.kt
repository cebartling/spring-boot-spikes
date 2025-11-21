package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.ShoppingCartService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * REST controller for shopping cart operations
 */
@RestController
@RequestMapping("/api/v1/carts")
class ShoppingCartController(
    private val shoppingCartService: ShoppingCartService
) {

    /**
     * Create a new shopping cart
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCart(@RequestBody request: CreateCartRequest): Mono<ShoppingCartResponse> {
        return shoppingCartService.createCart(request.sessionId, request.userId, request.expiresAt)
            .map { it.toResponse() }
    }

    /**
     * Get cart by ID
     */
    @GetMapping("/{cartId}")
    fun getCartById(@PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.findCartById(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get cart by UUID
     */
    @GetMapping("/uuid/{cartUuid}")
    fun getCartByUuid(@PathVariable cartUuid: UUID): Mono<ShoppingCartResponse> {
        return shoppingCartService.findCartByUuid(cartUuid)
            .map { it.toResponse() }
    }

    /**
     * Get or create cart for session
     */
    @GetMapping("/session/{sessionId}")
    fun getOrCreateCartForSession(
        @PathVariable sessionId: String,
        @RequestParam(required = false) userId: String?
    ): Mono<ShoppingCartResponse> {
        return shoppingCartService.findOrCreateCart(sessionId, userId)
            .map { it.toResponse() }
    }

    /**
     * Get cart by session ID
     */
    @GetMapping("/session/{sessionId}/current")
    fun getCartBySession(@PathVariable sessionId: String): Mono<ShoppingCartResponse> {
        return shoppingCartService.findCartBySessionId(sessionId)
            .map { it.toResponse() }
    }

    /**
     * Get carts by user ID
     */
    @GetMapping("/user/{userId}")
    fun getCartsByUserId(@PathVariable userId: String): Flux<ShoppingCartResponse> {
        return shoppingCartService.findCartsByUserId(userId)
            .map { it.toResponse() }
    }

    /**
     * Get carts by status
     */
    @GetMapping("/status/{status}")
    fun getCartsByStatus(@PathVariable status: CartStatus): Flux<ShoppingCartResponse> {
        return shoppingCartService.findCartsByStatus(status)
            .map { it.toResponse() }
    }

    /**
     * Associate cart with user
     */
    @PutMapping("/{cartId}/user")
    fun associateCartWithUser(
        @PathVariable cartId: Long,
        @RequestBody request: AssociateCartWithUserRequest
    ): Mono<ShoppingCartResponse> {
        return shoppingCartService.associateCartWithUser(cartId, request.userId)
            .map { it.toResponse() }
    }

    /**
     * Update cart expiration
     */
    @PutMapping("/{cartId}/expiration")
    fun updateCartExpiration(
        @PathVariable cartId: Long,
        @RequestBody request: UpdateCartExpirationRequest
    ): Mono<ShoppingCartResponse> {
        return shoppingCartService.updateCartExpiration(cartId, request.expiresAt)
            .map { it.toResponse() }
    }

    /**
     * Abandon a cart
     */
    @PostMapping("/{cartId}/abandon")
    fun abandonCart(@PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.abandonCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Convert a cart
     */
    @PostMapping("/{cartId}/convert")
    fun convertCart(@PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.convertCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Expire a cart
     */
    @PostMapping("/{cartId}/expire")
    fun expireCart(@PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.expireCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Restore a cart to active status
     */
    @PostMapping("/{cartId}/restore")
    fun restoreCart(@PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.restoreCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get expired carts
     */
    @GetMapping("/expired")
    fun getExpiredCarts(): Flux<ShoppingCartResponse> {
        return shoppingCartService.findExpiredCarts()
            .map { it.toResponse() }
    }

    /**
     * Get abandoned carts
     */
    @GetMapping("/abandoned")
    fun getAbandonedCarts(
        @RequestParam(defaultValue = "24") hoursInactive: Long
    ): Flux<ShoppingCartResponse> {
        return shoppingCartService.findAbandonedCarts(hoursInactive)
            .map { it.toResponse() }
    }

    /**
     * Process expired carts (mark them as expired)
     */
    @PostMapping("/process-expired")
    fun processExpiredCarts(): Mono<Long> {
        return shoppingCartService.processExpiredCarts()
    }

    /**
     * Process abandoned carts (mark them as abandoned)
     */
    @PostMapping("/process-abandoned")
    fun processAbandonedCarts(
        @RequestParam(defaultValue = "24") hoursInactive: Long
    ): Mono<Long> {
        return shoppingCartService.processAbandonedCarts(hoursInactive)
    }

    /**
     * Get carts with items
     */
    @GetMapping("/with-items")
    fun getCartsWithItems(
        @RequestParam(defaultValue = "ACTIVE") status: CartStatus
    ): Flux<ShoppingCartResponse> {
        return shoppingCartService.findCartsWithItems(status)
            .map { it.toResponse() }
    }

    /**
     * Get empty carts
     */
    @GetMapping("/empty")
    fun getEmptyCarts(): Flux<ShoppingCartResponse> {
        return shoppingCartService.findEmptyCarts()
            .map { it.toResponse() }
    }

    /**
     * Count carts by status
     */
    @GetMapping("/count/{status}")
    fun countCartsByStatus(@PathVariable status: CartStatus): Mono<Long> {
        return shoppingCartService.countCartsByStatus(status)
    }

    /**
     * Get cart statistics
     */
    @GetMapping("/statistics")
    fun getCartStatistics(): Mono<CartStatisticsResponse> {
        return Mono.zip(
            shoppingCartService.countCartsByStatus(CartStatus.ACTIVE),
            shoppingCartService.countCartsByStatus(CartStatus.ABANDONED),
            shoppingCartService.countCartsByStatus(CartStatus.CONVERTED),
            shoppingCartService.countCartsByStatus(CartStatus.EXPIRED)
        ).flatMap { tuple ->
            val active = tuple.t1
            val abandoned = tuple.t2
            val converted = tuple.t3
            val expired = tuple.t4
            val total = active + abandoned + converted + expired

            Mono.just(
                CartStatisticsResponse(
                    totalCarts = total,
                    activeCarts = active,
                    abandonedCarts = abandoned,
                    convertedCarts = converted,
                    expiredCarts = expired
                )
            )
        }
    }

    /**
     * Delete a cart
     */
    @DeleteMapping("/{cartId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCart(@PathVariable cartId: Long): Mono<Void> {
        return shoppingCartService.deleteCart(cartId)
    }
}
