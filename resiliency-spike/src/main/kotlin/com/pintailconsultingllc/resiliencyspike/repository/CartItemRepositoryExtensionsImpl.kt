package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Implementation of CartItemRepositoryExtensions
 * Provides methods to load cart items with their related product information
 */
@Repository
class CartItemRepositoryExtensionsImpl(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository
) : CartItemRepositoryExtensions {

    override fun findByCartIdWithProduct(cartId: Long): Flux<CartItem> {
        return cartItemRepository.findByCartId(cartId)
            .flatMap { item -> loadProduct(item) }
    }

    override fun findByCartIdAndProductIdWithProduct(cartId: Long, productId: UUID): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item -> loadProduct(item) }
    }

    override fun findByProductIdWithProduct(productId: UUID): Flux<CartItem> {
        return cartItemRepository.findByProductId(productId)
            .flatMap { item -> loadProduct(item) }
    }

    private fun loadProduct(item: CartItem): Mono<CartItem> {
        return productRepository.findById(item.productId)
            .map { product -> item.copy(product = product) }
            .defaultIfEmpty(item) // Return item without product if product not found
    }
}
