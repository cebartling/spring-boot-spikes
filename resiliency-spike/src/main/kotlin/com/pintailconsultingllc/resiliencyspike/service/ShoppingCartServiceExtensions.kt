package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart
import com.pintailconsultingllc.resiliencyspike.repository.ShoppingCartRepositoryExtensionsImpl
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service extensions for working with shopping carts and their relationships
 */
@Service
class ShoppingCartServiceExtensions(
    private val cartRepositoryExtensions: ShoppingCartRepositoryExtensionsImpl
) {

    /**
     * Find a cart by ID with all its items loaded
     */
    fun findCartWithItems(cartId: Long): Mono<ShoppingCart> {
        return cartRepositoryExtensions.findByIdWithItems(cartId)
    }

    /**
     * Find a cart by UUID with all its items loaded
     */
    fun findCartWithItemsByUuid(cartUuid: UUID): Mono<ShoppingCart> {
        return cartRepositoryExtensions.findByCartUuidWithItems(cartUuid)
    }

    /**
     * Find a cart by session ID with all its items loaded
     */
    fun findCartWithItemsBySessionId(sessionId: String): Mono<ShoppingCart> {
        return cartRepositoryExtensions.findBySessionIdWithItems(sessionId)
    }

    /**
     * Find all carts for a user with items loaded
     */
    fun findUserCartsWithItems(userId: String): Flux<ShoppingCart> {
        return cartRepositoryExtensions.findByUserIdWithItems(userId)
    }
}
