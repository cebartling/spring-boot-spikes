package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.CartItemService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * REST controller for cart item operations
 */
@RestController
@RequestMapping("/api/v1/carts/{cartId}/items")
class CartItemController(
    private val cartItemService: CartItemService
) {

    /**
     * Get all items in a cart
     */
    @GetMapping
    fun getCartItems(@PathVariable cartId: Long): Flux<CartItemResponse> {
        return cartItemService.findCartItems(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get a specific item in a cart
     */
    @GetMapping("/{productId}")
    fun getCartItem(
        @PathVariable cartId: Long,
        @PathVariable productId: UUID
    ): Mono<CartItemResponse> {
        return cartItemService.findCartItem(cartId, productId)
            .map { it.toResponse() }
    }

    /**
     * Add an item to cart
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addItemToCart(
        @PathVariable cartId: Long,
        @RequestBody request: AddItemToCartRequest
    ): Mono<CartItemResponse> {
        return cartItemService.addItemToCart(cartId, request.productId, request.quantity)
            .map { it.toResponse() }
    }

    /**
     * Update item quantity
     */
    @PutMapping("/{productId}/quantity")
    fun updateItemQuantity(
        @PathVariable cartId: Long,
        @PathVariable productId: UUID,
        @RequestBody request: UpdateItemQuantityRequest
    ): Mono<CartItemResponse> {
        return cartItemService.updateItemQuantity(cartId, productId, request.quantity)
            .map { it.toResponse() }
    }

    /**
     * Apply discount to item
     */
    @PutMapping("/{productId}/discount")
    fun applyItemDiscount(
        @PathVariable cartId: Long,
        @PathVariable productId: UUID,
        @RequestBody request: ApplyItemDiscountRequest
    ): Mono<CartItemResponse> {
        return cartItemService.applyItemDiscount(cartId, productId, request.discountAmountCents)
            .map { it.toResponse() }
    }

    /**
     * Update item metadata
     */
    @PutMapping("/{productId}/metadata")
    fun updateItemMetadata(
        @PathVariable cartId: Long,
        @PathVariable productId: UUID,
        @RequestBody request: UpdateItemMetadataRequest
    ): Mono<CartItemResponse> {
        return cartItemService.updateItemMetadata(cartId, productId, request.metadata)
            .map { it.toResponse() }
    }

    /**
     * Remove an item from cart
     */
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeItemFromCart(
        @PathVariable cartId: Long,
        @PathVariable productId: UUID
    ): Mono<Void> {
        return cartItemService.removeItemFromCart(cartId, productId)
    }

    /**
     * Clear all items from cart
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearCart(@PathVariable cartId: Long): Mono<Void> {
        return cartItemService.clearCart(cartId)
    }

    /**
     * Get cart totals
     */
    @GetMapping("/totals")
    fun getCartTotals(@PathVariable cartId: Long): Mono<CartTotalsResponse> {
        return Mono.zip(
            cartItemService.calculateCartTotal(cartId),
            cartItemService.calculateCartItemCount(cartId)
        ).map { tuple ->
            val totalCents = tuple.t1
            val itemCount = tuple.t2
            CartTotalsResponse(
                subtotalCents = totalCents,
                taxAmountCents = 0, // TODO: Implement tax calculation
                discountAmountCents = 0, // TODO: Implement discount aggregation
                totalAmountCents = totalCents,
                itemCount = itemCount
            )
        }
    }

    /**
     * Count items in cart
     */
    @GetMapping("/count")
    fun countCartItems(@PathVariable cartId: Long): Mono<Long> {
        return cartItemService.countCartItems(cartId)
    }

    /**
     * Get discounted items
     */
    @GetMapping("/discounted")
    fun getDiscountedItems(@PathVariable cartId: Long): Flux<CartItemResponse> {
        return cartItemService.findDiscountedItems(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get high value items
     */
    @GetMapping("/high-value")
    fun getHighValueItems(
        @PathVariable cartId: Long,
        @RequestParam minPriceCents: Long
    ): Flux<CartItemResponse> {
        return cartItemService.findHighValueItems(cartId, minPriceCents)
            .map { it.toResponse() }
    }

    /**
     * Get bulk items
     */
    @GetMapping("/bulk")
    fun getBulkItems(
        @PathVariable cartId: Long,
        @RequestParam minQuantity: Int
    ): Flux<CartItemResponse> {
        return cartItemService.findBulkItems(cartId, minQuantity)
            .map { it.toResponse() }
    }

    /**
     * Validate item availability
     */
    @GetMapping("/{productId}/validate")
    fun validateItemAvailability(
        @PathVariable cartId: Long,
        @PathVariable productId: UUID
    ): Mono<ItemAvailabilityResponse> {
        return cartItemService.validateItemAvailability(cartId, productId)
            .map { available ->
                ItemAvailabilityResponse(
                    productId = productId,
                    available = available,
                    reason = if (!available) "Product is not available or out of stock" else null
                )
            }
    }

    /**
     * Validate all cart items
     */
    @GetMapping("/validate")
    fun validateCartItems(@PathVariable cartId: Long): Mono<CartValidationResponse> {
        return cartItemService.validateCartItems(cartId)
            .collectList()
            .map { validationResults ->
                val items = validationResults.map { (item, isValid) ->
                    ItemAvailabilityResponse(
                        productId = item.productId,
                        available = isValid,
                        reason = if (!isValid) "Product is not available or out of stock" else null
                    )
                }
                CartValidationResponse(
                    cartId = cartId,
                    items = items,
                    allItemsValid = items.all { it.available }
                )
            }
    }
}
