package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Reactive repository for CartItem entities
 */
@Repository
interface CartItemRepository : ReactiveCrudRepository<CartItem, Long> {

    /**
     * Find all items in a specific cart
     */
    fun findByCartId(cartId: Long): Flux<CartItem>

    /**
     * Find a specific item in a cart by product ID
     */
    fun findByCartIdAndProductId(cartId: Long, productId: UUID): Mono<CartItem>

    /**
     * Find all items for a specific product across all carts
     */
    fun findByProductId(productId: UUID): Flux<CartItem>

    /**
     * Find items by SKU
     */
    fun findBySku(sku: String): Flux<CartItem>

    /**
     * Count items in a specific cart
     */
    fun countByCartId(cartId: Long): Mono<Long>

    /**
     * Delete all items in a specific cart
     */
    fun deleteByCartId(cartId: Long): Mono<Void>

    /**
     * Calculate total value of items in a cart (in cents)
     */
    @Query("SELECT COALESCE(SUM(line_total_cents - discount_amount_cents), 0) FROM cart_items WHERE cart_id = :cartId")
    fun calculateCartTotal(cartId: Long): Mono<Long>

    /**
     * Calculate total quantity of items in a cart
     */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM cart_items WHERE cart_id = :cartId")
    fun calculateCartItemCount(cartId: Long): Mono<Int>

    /**
     * Find items with discounts
     */
    @Query("SELECT * FROM cart_items WHERE cart_id = :cartId AND discount_amount_cents > 0")
    fun findDiscountedItems(cartId: Long): Flux<CartItem>

    /**
     * Find high-value items (above a certain price threshold in cents)
     */
    @Query("SELECT * FROM cart_items WHERE cart_id = :cartId AND unit_price_cents >= :minPriceCents ORDER BY unit_price_cents DESC")
    fun findHighValueItems(cartId: Long, minPriceCents: Long): Flux<CartItem>

    /**
     * Find items with large quantities
     */
    @Query("SELECT * FROM cart_items WHERE cart_id = :cartId AND quantity >= :minQuantity ORDER BY quantity DESC")
    fun findBulkItems(cartId: Long, minQuantity: Int): Flux<CartItem>
}
