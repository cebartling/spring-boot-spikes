package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Implementation of ShoppingCartRepositoryExtensions
 * Provides methods to load shopping carts with their related items
 */
@Repository
class ShoppingCartRepositoryExtensionsImpl(
    private val shoppingCartRepository: ShoppingCartRepository,
    private val cartItemRepository: CartItemRepository
) : ShoppingCartRepositoryExtensions {

    override fun findByIdWithItems(cartId: Long): Mono<ShoppingCart> {
        return shoppingCartRepository.findById(cartId)
            .flatMap { cart -> loadCartItems(cart) }
    }

    override fun findByCartUuidWithItems(cartUuid: UUID): Mono<ShoppingCart> {
        return shoppingCartRepository.findByCartUuid(cartUuid)
            .flatMap { cart -> loadCartItems(cart) }
    }

    override fun findBySessionIdWithItems(sessionId: String): Mono<ShoppingCart> {
        return shoppingCartRepository.findBySessionId(sessionId)
            .flatMap { cart -> loadCartItems(cart) }
    }

    override fun findByUserIdWithItems(userId: String): Flux<ShoppingCart> {
        return shoppingCartRepository.findByUserId(userId)
            .flatMap { cart -> loadCartItems(cart) }
    }

    private fun loadCartItems(cart: ShoppingCart): Mono<ShoppingCart> {
        return if (cart.id == null) {
            Mono.just(cart)
        } else {
            cartItemRepository.findByCartId(cart.id)
                .collectList()
                .map { items -> cart.copy(items = items) }
        }
    }
}
