package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.repository.CartItemRepository
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * Service for managing cart items
 * Demonstrates reactive database operations with R2DBC for cart item functionality
 * Circuit breaker protection applied to database operations
 */
@Service
class CartItemService(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val cartStateHistoryService: CartStateHistoryService
) {

    private val logger = LoggerFactory.getLogger(CartItemService::class.java)

    /**
     * Add an item to a cart
     * If the item already exists, update the quantity
     */
    @CircuitBreaker(name = "cartItem", fallbackMethod = "addItemToCartFallback")
    fun addItemToCart(cartId: Long, productId: UUID, quantity: Int): Mono<CartItem> {
        return productRepository.findById(productId)
            .flatMap { product ->
                cartItemRepository.findByCartIdAndProductId(cartId, productId)
                    .flatMap { existingItem ->
                        // Update existing item quantity
                        updateExistingItem(cartId, existingItem, quantity, productId)
                    }
                    .switchIfEmpty(
                        // Create new item
                        createNewItem(cartId, productId, quantity, product)
                    )
            }
    }

    private fun addItemToCartFallback(cartId: Long, productId: UUID, quantity: Int, ex: Exception): Mono<CartItem> {
        logger.error("Circuit breaker fallback for addItemToCart - cartId: $cartId, productId: $productId, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to add item to cart. Please try again later.", ex))
    }

    private fun updateExistingItem(
        cartId: Long,
        existingItem: CartItem,
        additionalQuantity: Int,
        productId: UUID
    ): Mono<CartItem> {
        val updatedItem = existingItem.copy(quantity = existingItem.quantity + additionalQuantity)
        return cartItemRepository.save(updatedItem)
            .flatMap { savedItem ->
                cartStateHistoryService.recordItemEvent(
                    cartId,
                    CartEventType.QUANTITY_CHANGED,
                    mapOf(
                        "product_id" to productId.toString(),
                        "previous_quantity" to existingItem.quantity.toString(),
                        "new_quantity" to savedItem.quantity.toString()
                    )
                ).thenReturn(savedItem)
            }
    }

    private fun createNewItem(
        cartId: Long,
        productId: UUID,
        quantity: Int,
        product: Product
    ): Mono<CartItem> {
        val unitPriceCents = convertToCents(product.price)
        val lineTotalCents = unitPriceCents * quantity

        val newItem = CartItem(
            cartId = cartId,
            productId = productId,
            sku = product.sku,
            productName = product.name,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            lineTotalCents = lineTotalCents
        )
        return cartItemRepository.save(newItem)
            .flatMap { savedItem ->
                cartStateHistoryService.recordItemEvent(
                    cartId,
                    CartEventType.ITEM_ADDED,
                    mapOf(
                        "product_id" to productId.toString(),
                        "quantity" to quantity.toString(),
                        "unit_price_cents" to unitPriceCents.toString()
                    )
                ).thenReturn(savedItem)
            }
    }

    /**
     * Convert BigDecimal price to cents (Long)
     */
    private fun convertToCents(price: BigDecimal): Long {
        return price.multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * Remove an item from a cart
     */
    fun removeItemFromCart(cartId: Long, productId: UUID): Mono<Void> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                cartItemRepository.delete(item)
                    .then(
                        cartStateHistoryService.recordItemEvent(
                            cartId,
                            CartEventType.ITEM_REMOVED,
                            mapOf(
                                "product_id" to productId.toString(),
                                "quantity" to item.quantity.toString()
                            )
                        ).then()
                    )
            }
    }

    /**
     * Update item quantity
     */
    fun updateItemQuantity(cartId: Long, productId: UUID, quantity: Int): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                val previousQuantity = item.quantity
                val updatedItem = item.copy(quantity = quantity)
                cartItemRepository.save(updatedItem)
                    .flatMap { savedItem ->
                        cartStateHistoryService.recordItemEvent(
                            cartId,
                            CartEventType.QUANTITY_CHANGED,
                            mapOf(
                                "product_id" to productId.toString(),
                                "previous_quantity" to previousQuantity.toString(),
                                "new_quantity" to quantity.toString()
                            )
                        ).thenReturn(savedItem)
                    }
            }
    }

    /**
     * Apply discount to a cart item (discount amount in cents)
     */
    fun applyItemDiscount(cartId: Long, productId: UUID, discountAmountCents: Long): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                val updatedItem = item.copy(discountAmountCents = discountAmountCents)
                cartItemRepository.save(updatedItem)
                    .flatMap { savedItem ->
                        cartStateHistoryService.recordItemEvent(
                            cartId,
                            CartEventType.ITEM_UPDATED,
                            mapOf(
                                "product_id" to productId.toString(),
                                "discount_amount_cents" to discountAmountCents.toString()
                            )
                        ).thenReturn(savedItem)
                    }
            }
    }

    /**
     * Find all items in a cart
     */
    fun findCartItems(cartId: Long): Flux<CartItem> {
        return cartItemRepository.findByCartId(cartId)
    }

    /**
     * Find a specific item in a cart
     */
    fun findCartItem(cartId: Long, productId: UUID): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
    }

    /**
     * Count items in a cart
     */
    fun countCartItems(cartId: Long): Mono<Long> {
        return cartItemRepository.countByCartId(cartId)
    }

    /**
     * Calculate total value of items in a cart (in cents)
     */
    fun calculateCartTotal(cartId: Long): Mono<Long> {
        return cartItemRepository.calculateCartTotal(cartId)
    }

    /**
     * Calculate total quantity of items in a cart
     */
    fun calculateCartItemCount(cartId: Long): Mono<Int> {
        return cartItemRepository.calculateCartItemCount(cartId)
    }

    /**
     * Clear all items from a cart
     */
    fun clearCart(cartId: Long): Mono<Void> {
        return cartItemRepository.deleteByCartId(cartId)
            .then(
                cartStateHistoryService.recordItemEvent(
                    cartId,
                    CartEventType.ITEM_REMOVED,
                    mapOf("action" to "clear_cart")
                ).then()
            )
    }

    /**
     * Find items with discounts
     */
    fun findDiscountedItems(cartId: Long): Flux<CartItem> {
        return cartItemRepository.findDiscountedItems(cartId)
    }

    /**
     * Find high-value items in cart (minimum price in cents)
     */
    fun findHighValueItems(cartId: Long, minPriceCents: Long): Flux<CartItem> {
        return cartItemRepository.findHighValueItems(cartId, minPriceCents)
    }

    /**
     * Find bulk items (large quantities)
     */
    fun findBulkItems(cartId: Long, minQuantity: Int = 5): Flux<CartItem> {
        return cartItemRepository.findBulkItems(cartId, minQuantity)
    }

    /**
     * Update item metadata (product options, customizations, etc.)
     */
    fun updateItemMetadata(cartId: Long, productId: UUID, metadata: String): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                val updatedItem = item.copy(metadata = metadata)
                cartItemRepository.save(updatedItem)
                    .flatMap { savedItem ->
                        cartStateHistoryService.recordItemEvent(
                            cartId,
                            CartEventType.ITEM_UPDATED,
                            mapOf(
                                "product_id" to productId.toString(),
                                "action" to "metadata_updated"
                            )
                        ).thenReturn(savedItem)
                    }
            }
    }

    /**
     * Validate item availability (check if product still exists and has stock)
     */
    fun validateItemAvailability(cartId: Long, productId: UUID): Mono<Boolean> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                productRepository.findById(productId)
                    .map { product ->
                        product.isActive && product.stockQuantity >= item.quantity
                    }
            }
            .defaultIfEmpty(false)
    }

    /**
     * Validate all items in cart
     */
    fun validateCartItems(cartId: Long): Flux<Pair<CartItem, Boolean>> {
        return cartItemRepository.findByCartId(cartId)
            .flatMap { item ->
                validateItemAvailability(cartId, item.productId)
                    .map { isValid -> Pair(item, isValid) }
            }
    }
}
