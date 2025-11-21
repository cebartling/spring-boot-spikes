package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.CartItemService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Cart Items", description = "Shopping cart item management APIs")
class CartItemController(
    private val cartItemService: CartItemService
) {

    /**
     * Get all items in a cart
     */
    @Operation(summary = "Get all cart items", description = "Retrieves all items in a shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart items retrieved successfully")
    ])
    @GetMapping
    fun getCartItems(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Flux<CartItemResponse> {
        return cartItemService.findCartItems(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get a specific item in a cart
     */
    @Operation(summary = "Get cart item", description = "Retrieves a specific item from a shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart item found", content = [Content(schema = Schema(implementation = CartItemResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart item not found")
    ])
    @GetMapping("/{productId}")
    fun getCartItem(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
    ): Mono<CartItemResponse> {
        return cartItemService.findCartItem(cartId, productId)
            .map { it.toResponse() }
    }

    /**
     * Add an item to cart
     */
    @Operation(summary = "Add item to cart", description = "Adds a product to the shopping cart or increases quantity if already present")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Item added successfully", content = [Content(schema = Schema(implementation = CartItemResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid input")
    ])
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addItemToCart(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @RequestBody request: AddItemToCartRequest
    ): Mono<CartItemResponse> {
        return cartItemService.addItemToCart(cartId, request.productId, request.quantity)
            .map { it.toResponse() }
    }

    /**
     * Update item quantity
     */
    @Operation(summary = "Update item quantity", description = "Updates the quantity of a cart item")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Quantity updated successfully", content = [Content(schema = Schema(implementation = CartItemResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart item not found")
    ])
    @PutMapping("/{productId}/quantity")
    fun updateItemQuantity(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID,
        @RequestBody request: UpdateItemQuantityRequest
    ): Mono<CartItemResponse> {
        return cartItemService.updateItemQuantity(cartId, productId, request.quantity)
            .map { it.toResponse() }
    }

    /**
     * Apply discount to item
     */
    @Operation(summary = "Apply item discount", description = "Applies a discount to a specific cart item")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Discount applied successfully", content = [Content(schema = Schema(implementation = CartItemResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart item not found")
    ])
    @PutMapping("/{productId}/discount")
    fun applyItemDiscount(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID,
        @RequestBody request: ApplyItemDiscountRequest
    ): Mono<CartItemResponse> {
        return cartItemService.applyItemDiscount(cartId, productId, request.discountAmountCents)
            .map { it.toResponse() }
    }

    /**
     * Update item metadata
     */
    @Operation(summary = "Update item metadata", description = "Updates the metadata for a cart item")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Metadata updated successfully", content = [Content(schema = Schema(implementation = CartItemResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart item not found")
    ])
    @PutMapping("/{productId}/metadata")
    fun updateItemMetadata(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID,
        @RequestBody request: UpdateItemMetadataRequest
    ): Mono<CartItemResponse> {
        return cartItemService.updateItemMetadata(cartId, productId, request.metadata)
            .map { it.toResponse() }
    }

    /**
     * Remove an item from cart
     */
    @Operation(summary = "Remove item from cart", description = "Removes a specific item from the shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Item removed successfully"),
        ApiResponse(responseCode = "404", description = "Cart item not found")
    ])
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeItemFromCart(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
    ): Mono<Void> {
        return cartItemService.removeItemFromCart(cartId, productId)
    }

    /**
     * Clear all items from cart
     */
    @Operation(summary = "Clear cart", description = "Removes all items from the shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Cart cleared successfully")
    ])
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearCart(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<Void> {
        return cartItemService.clearCart(cartId)
    }

    /**
     * Get cart totals
     */
    @Operation(summary = "Get cart totals", description = "Calculates and retrieves total amounts for the shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart totals calculated successfully", content = [Content(schema = Schema(implementation = CartTotalsResponse::class))])
    ])
    @GetMapping("/totals")
    fun getCartTotals(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<CartTotalsResponse> {
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
    @Operation(summary = "Count cart items", description = "Returns the total number of items in the shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Item count retrieved successfully")
    ])
    @GetMapping("/count")
    fun countCartItems(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<Long> {
        return cartItemService.countCartItems(cartId)
    }

    /**
     * Get discounted items
     */
    @Operation(summary = "Get discounted items", description = "Retrieves all cart items that have discounts applied")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Discounted items retrieved successfully")
    ])
    @GetMapping("/discounted")
    fun getDiscountedItems(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Flux<CartItemResponse> {
        return cartItemService.findDiscountedItems(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get high value items
     */
    @Operation(summary = "Get high value items", description = "Retrieves cart items with unit price above the specified minimum")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "High value items retrieved successfully")
    ])
    @GetMapping("/high-value")
    fun getHighValueItems(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Minimum price in cents") @RequestParam minPriceCents: Long
    ): Flux<CartItemResponse> {
        return cartItemService.findHighValueItems(cartId, minPriceCents)
            .map { it.toResponse() }
    }

    /**
     * Get bulk items
     */
    @Operation(summary = "Get bulk items", description = "Retrieves cart items with quantity above the specified minimum")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Bulk items retrieved successfully")
    ])
    @GetMapping("/bulk")
    fun getBulkItems(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Minimum quantity threshold") @RequestParam minQuantity: Int
    ): Flux<CartItemResponse> {
        return cartItemService.findBulkItems(cartId, minQuantity)
            .map { it.toResponse() }
    }

    /**
     * Validate item availability
     */
    @Operation(summary = "Validate item availability", description = "Checks if a specific cart item is available for purchase")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Validation completed", content = [Content(schema = Schema(implementation = ItemAvailabilityResponse::class))])
    ])
    @GetMapping("/{productId}/validate")
    fun validateItemAvailability(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
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
    @Operation(summary = "Validate all cart items", description = "Checks availability for all items in the shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Validation completed", content = [Content(schema = Schema(implementation = CartValidationResponse::class))])
    ])
    @GetMapping("/validate")
    fun validateCartItems(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<CartValidationResponse> {
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
