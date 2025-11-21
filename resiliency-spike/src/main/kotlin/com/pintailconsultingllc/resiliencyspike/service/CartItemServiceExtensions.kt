package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import com.pintailconsultingllc.resiliencyspike.repository.CartItemRepositoryExtensionsImpl
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service extensions for working with cart items and their product relationships
 */
@Service
class CartItemServiceExtensions(
    private val cartItemRepositoryExtensions: CartItemRepositoryExtensionsImpl
) {

    /**
     * Find all items in a cart with their product details loaded
     */
    fun findCartItemsWithProduct(cartId: Long): Flux<CartItem> {
        return cartItemRepositoryExtensions.findByCartIdWithProduct(cartId)
    }

    /**
     * Find a specific cart item with its product details loaded
     */
    fun findCartItemWithProduct(cartId: Long, productId: UUID): Mono<CartItem> {
        return cartItemRepositoryExtensions.findByCartIdAndProductIdWithProduct(cartId, productId)
    }

    /**
     * Find all cart items for a product with product details loaded
     */
    fun findCartItemsByProductWithProduct(productId: UUID): Flux<CartItem> {
        return cartItemRepositoryExtensions.findByProductIdWithProduct(productId)
    }
}
