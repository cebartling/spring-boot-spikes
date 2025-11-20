package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import com.pintailconsultingllc.resiliencyspike.repository.CartItemRepository
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

/**
 * Service for managing cart items
 * Demonstrates reactive database operations with R2DBC for cart item functionality
 */
@Service
class CartItemService(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val cartStateHistoryService: CartStateHistoryService
) {

    /**
     * Add an item to a cart
     * If the item already exists, update the quantity
     */
    fun addItemToCart(cartId: Long, productId: Long, quantity: Int): Mono<CartItem> {
        return productRepository.findById(productId)
            .flatMap { product ->
                cartItemRepository.findByCartIdAndProductId(cartId, productId)
                    .flatMap { existingItem ->
                        // Update existing item quantity
                        val updatedItem = existingItem.copy(quantity = existingItem.quantity + quantity)
                        cartItemRepository.save(updatedItem)
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
                    .switchIfEmpty(
                        // Create new item
                        Mono.defer {
                            val newItem = CartItem(
                                cartId = cartId,
                                productId = productId,
                                sku = product.sku,
                                productName = product.name,
                                quantity = quantity,
                                unitPrice = product.price,
                                lineTotal = product.price.multiply(BigDecimal(quantity))
                            )
                            cartItemRepository.save(newItem)
                                .flatMap { savedItem ->
                                    cartStateHistoryService.recordItemEvent(
                                        cartId,
                                        CartEventType.ITEM_ADDED,
                                        mapOf(
                                            "product_id" to productId.toString(),
                                            "quantity" to quantity.toString(),
                                            "unit_price" to product.price.toString()
                                        )
                                    ).thenReturn(savedItem)
                                }
                        }
                    )
            }
    }

    /**
     * Remove an item from a cart
     */
    fun removeItemFromCart(cartId: Long, productId: Long): Mono<Void> {
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
                        )
                    )
            }
    }

    /**
     * Update item quantity
     */
    fun updateItemQuantity(cartId: Long, productId: Long, quantity: Int): Mono<CartItem> {
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
     * Apply discount to a cart item
     */
    fun applyItemDiscount(cartId: Long, productId: Long, discountAmount: BigDecimal): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                val updatedItem = item.copy(discountAmount = discountAmount)
                cartItemRepository.save(updatedItem)
                    .flatMap { savedItem ->
                        cartStateHistoryService.recordItemEvent(
                            cartId,
                            CartEventType.ITEM_UPDATED,
                            mapOf(
                                "product_id" to productId.toString(),
                                "discount_amount" to discountAmount.toString()
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
    fun findCartItem(cartId: Long, productId: Long): Mono<CartItem> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
    }

    /**
     * Count items in a cart
     */
    fun countCartItems(cartId: Long): Mono<Long> {
        return cartItemRepository.countByCartId(cartId)
    }

    /**
     * Calculate total value of items in a cart
     */
    fun calculateCartTotal(cartId: Long): Mono<BigDecimal> {
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
                )
            )
    }

    /**
     * Find items with discounts
     */
    fun findDiscountedItems(cartId: Long): Flux<CartItem> {
        return cartItemRepository.findDiscountedItems(cartId)
    }

    /**
     * Find high-value items in cart
     */
    fun findHighValueItems(cartId: Long, minPrice: BigDecimal): Flux<CartItem> {
        return cartItemRepository.findHighValueItems(cartId, minPrice)
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
    fun updateItemMetadata(cartId: Long, productId: Long, metadata: String): Mono<CartItem> {
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
    fun validateItemAvailability(cartId: Long, productId: Long): Mono<Boolean> {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .flatMap { item ->
                productRepository.findById(productId)
                    .map { product ->
                        product.isActive && product.stockQuantity >= item.quantity
                    }
                    .defaultIfEmpty(false)
            }
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
