package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Extension methods for CartItemRepository to load relationships
 */
interface CartItemRepositoryExtensions {

    /**
     * Find all items in a cart with their product details loaded
     */
    fun findByCartIdWithProduct(cartId: Long): Flux<CartItem>

    /**
     * Find a specific cart item with its product details loaded
     */
    fun findByCartIdAndProductIdWithProduct(cartId: Long, productId: UUID): Mono<CartItem>

    /**
     * Find all items for a product with product details loaded
     */
    fun findByProductIdWithProduct(productId: UUID): Flux<CartItem>
}
