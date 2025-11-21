package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Extension methods for ShoppingCartRepository to load relationships
 */
interface ShoppingCartRepositoryExtensions {

    /**
     * Find a cart by ID with its items loaded
     */
    fun findByIdWithItems(cartId: Long): Mono<ShoppingCart>

    /**
     * Find a cart by UUID with its items loaded
     */
    fun findByCartUuidWithItems(cartUuid: java.util.UUID): Mono<ShoppingCart>

    /**
     * Find a cart by session ID with its items loaded
     */
    fun findBySessionIdWithItems(sessionId: String): Mono<ShoppingCart>

    /**
     * Find all carts for a user with items loaded
     */
    fun findByUserIdWithItems(userId: String): Flux<ShoppingCart>
}
