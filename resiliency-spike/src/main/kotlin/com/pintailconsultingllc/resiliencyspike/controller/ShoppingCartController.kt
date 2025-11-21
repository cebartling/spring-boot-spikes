package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.ShoppingCartService
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
 * REST controller for shopping cart operations
 */
@RestController
@RequestMapping("/api/v1/carts")
@Tag(name = "Shopping Cart", description = "Shopping cart lifecycle and management APIs")
class ShoppingCartController(
    private val shoppingCartService: ShoppingCartService
) {

    /**
     * Create a new shopping cart
     */
    @Operation(summary = "Create a new shopping cart", description = "Creates a new shopping cart for a session")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Cart created successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid input")
    ])
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCart(@RequestBody request: CreateCartRequest): Mono<ShoppingCartResponse> {
        return shoppingCartService.createCart(request.sessionId, request.userId, request.expiresAt)
            .map { it.toResponse() }
    }

    /**
     * Get cart by ID
     */
    @Operation(summary = "Get cart by ID", description = "Retrieves a shopping cart by its internal ID")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart found", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @GetMapping("/{cartId}")
    fun getCartById(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.findCartById(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get cart by UUID
     */
    @Operation(summary = "Get cart by UUID", description = "Retrieves a shopping cart by its unique identifier")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart found", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @GetMapping("/uuid/{cartUuid}")
    fun getCartByUuid(@Parameter(description = "Cart UUID") @PathVariable cartUuid: UUID): Mono<ShoppingCartResponse> {
        return shoppingCartService.findCartByUuid(cartUuid)
            .map { it.toResponse() }
    }

    /**
     * Get or create cart for session
     */
    @Operation(summary = "Get or create cart for session", description = "Retrieves existing cart or creates a new one for the session")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart retrieved or created", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))])
    ])
    @GetMapping("/session/{sessionId}")
    fun getOrCreateCartForSession(
        @Parameter(description = "Session identifier") @PathVariable sessionId: String,
        @Parameter(description = "Optional user identifier") @RequestParam(required = false) userId: String?
    ): Mono<ShoppingCartResponse> {
        return shoppingCartService.findOrCreateCart(sessionId, userId)
            .map { it.toResponse() }
    }

    /**
     * Get cart by session ID
     */
    @Operation(summary = "Get cart by session", description = "Retrieves the current shopping cart for a session")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart found", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @GetMapping("/session/{sessionId}/current")
    fun getCartBySession(@Parameter(description = "Session identifier") @PathVariable sessionId: String): Mono<ShoppingCartResponse> {
        return shoppingCartService.findCartBySessionId(sessionId)
            .map { it.toResponse() }
    }

    /**
     * Get carts by user ID
     */
    @Operation(summary = "Get carts by user", description = "Retrieves all shopping carts for a specific user")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Carts retrieved successfully")
    ])
    @GetMapping("/user/{userId}")
    fun getCartsByUserId(@Parameter(description = "User identifier") @PathVariable userId: String): Flux<ShoppingCartResponse> {
        return shoppingCartService.findCartsByUserId(userId)
            .map { it.toResponse() }
    }

    /**
     * Get carts by status
     */
    @Operation(summary = "Get carts by status", description = "Retrieves all shopping carts with a specific status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Carts retrieved successfully")
    ])
    @GetMapping("/status/{status}")
    fun getCartsByStatus(@Parameter(description = "Cart status") @PathVariable status: CartStatus): Flux<ShoppingCartResponse> {
        return shoppingCartService.findCartsByStatus(status)
            .map { it.toResponse() }
    }

    /**
     * Associate cart with user
     */
    @Operation(summary = "Associate cart with user", description = "Links a shopping cart to a user account")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart associated successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @PutMapping("/{cartId}/user")
    fun associateCartWithUser(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @RequestBody request: AssociateCartWithUserRequest
    ): Mono<ShoppingCartResponse> {
        return shoppingCartService.associateCartWithUser(cartId, request.userId)
            .map { it.toResponse() }
    }

    /**
     * Update cart expiration
     */
    @Operation(summary = "Update cart expiration", description = "Updates the expiration time for a shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Expiration updated successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @PutMapping("/{cartId}/expiration")
    fun updateCartExpiration(
        @Parameter(description = "Cart internal ID") @PathVariable cartId: Long,
        @RequestBody request: UpdateCartExpirationRequest
    ): Mono<ShoppingCartResponse> {
        return shoppingCartService.updateCartExpiration(cartId, request.expiresAt)
            .map { it.toResponse() }
    }

    /**
     * Abandon a cart
     */
    @Operation(summary = "Abandon a cart", description = "Marks a shopping cart as abandoned")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart abandoned successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @PostMapping("/{cartId}/abandon")
    fun abandonCart(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.abandonCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Convert a cart
     */
    @Operation(summary = "Convert a cart", description = "Marks a shopping cart as converted to an order")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart converted successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @PostMapping("/{cartId}/convert")
    fun convertCart(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.convertCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Expire a cart
     */
    @Operation(summary = "Expire a cart", description = "Marks a shopping cart as expired")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart expired successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @PostMapping("/{cartId}/expire")
    fun expireCart(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.expireCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Restore a cart to active status
     */
    @Operation(summary = "Restore a cart", description = "Restores a shopping cart to active status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart restored successfully", content = [Content(schema = Schema(implementation = ShoppingCartResponse::class))]),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @PostMapping("/{cartId}/restore")
    fun restoreCart(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<ShoppingCartResponse> {
        return shoppingCartService.restoreCart(cartId)
            .map { it.toResponse() }
    }

    /**
     * Get expired carts
     */
    @Operation(summary = "Get expired carts", description = "Retrieves all carts that have passed their expiration time")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Expired carts retrieved successfully")
    ])
    @GetMapping("/expired")
    fun getExpiredCarts(): Flux<ShoppingCartResponse> {
        return shoppingCartService.findExpiredCarts()
            .map { it.toResponse() }
    }

    /**
     * Get abandoned carts
     */
    @Operation(summary = "Get abandoned carts", description = "Retrieves carts that have been inactive for the specified number of hours")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Abandoned carts retrieved successfully")
    ])
    @GetMapping("/abandoned")
    fun getAbandonedCarts(
        @Parameter(description = "Hours of inactivity (default: 24)") @RequestParam(defaultValue = "24") hoursInactive: Long
    ): Flux<ShoppingCartResponse> {
        return shoppingCartService.findAbandonedCarts(hoursInactive)
            .map { it.toResponse() }
    }

    /**
     * Process expired carts (mark them as expired)
     */
    @Operation(summary = "Process expired carts", description = "Finds and marks all expired carts as expired")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Expired carts processed successfully, returns count")
    ])
    @PostMapping("/process-expired")
    fun processExpiredCarts(): Mono<Long> {
        return shoppingCartService.processExpiredCarts()
    }

    /**
     * Process abandoned carts (mark them as abandoned)
     */
    @Operation(summary = "Process abandoned carts", description = "Finds and marks inactive carts as abandoned")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Abandoned carts processed successfully, returns count")
    ])
    @PostMapping("/process-abandoned")
    fun processAbandonedCarts(
        @Parameter(description = "Hours of inactivity threshold (default: 24)") @RequestParam(defaultValue = "24") hoursInactive: Long
    ): Mono<Long> {
        return shoppingCartService.processAbandonedCarts(hoursInactive)
    }

    /**
     * Get carts with items
     */
    @Operation(summary = "Get carts with items", description = "Retrieves carts that contain items, filtered by status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Carts with items retrieved successfully")
    ])
    @GetMapping("/with-items")
    fun getCartsWithItems(
        @Parameter(description = "Cart status filter (default: ACTIVE)") @RequestParam(defaultValue = "ACTIVE") status: CartStatus
    ): Flux<ShoppingCartResponse> {
        return shoppingCartService.findCartsWithItems(status)
            .map { it.toResponse() }
    }

    /**
     * Get empty carts
     */
    @Operation(summary = "Get empty carts", description = "Retrieves all carts that have no items")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Empty carts retrieved successfully")
    ])
    @GetMapping("/empty")
    fun getEmptyCarts(): Flux<ShoppingCartResponse> {
        return shoppingCartService.findEmptyCarts()
            .map { it.toResponse() }
    }

    /**
     * Count carts by status
     */
    @Operation(summary = "Count carts by status", description = "Returns the count of carts with a specific status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cart count retrieved successfully")
    ])
    @GetMapping("/count/{status}")
    fun countCartsByStatus(@Parameter(description = "Cart status") @PathVariable status: CartStatus): Mono<Long> {
        return shoppingCartService.countCartsByStatus(status)
    }

    /**
     * Get cart statistics
     */
    @Operation(summary = "Get cart statistics", description = "Retrieves aggregate statistics for all shopping carts")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Statistics retrieved successfully", content = [Content(schema = Schema(implementation = CartStatisticsResponse::class))])
    ])
    @GetMapping("/statistics")
    fun getCartStatistics(): Mono<CartStatisticsResponse> {
        return Mono.zip(
            shoppingCartService.countCartsByStatus(CartStatus.ACTIVE),
            shoppingCartService.countCartsByStatus(CartStatus.ABANDONED),
            shoppingCartService.countCartsByStatus(CartStatus.CONVERTED),
            shoppingCartService.countCartsByStatus(CartStatus.EXPIRED)
        ).flatMap { tuple ->
            val active = tuple.t1
            val abandoned = tuple.t2
            val converted = tuple.t3
            val expired = tuple.t4
            val total = active + abandoned + converted + expired

            Mono.just(
                CartStatisticsResponse(
                    totalCarts = total,
                    activeCarts = active,
                    abandonedCarts = abandoned,
                    convertedCarts = converted,
                    expiredCarts = expired
                )
            )
        }
    }

    /**
     * Delete a cart
     */
    @Operation(summary = "Delete a cart", description = "Permanently deletes a shopping cart")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Cart deleted successfully"),
        ApiResponse(responseCode = "404", description = "Cart not found")
    ])
    @DeleteMapping("/{cartId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCart(@Parameter(description = "Cart internal ID") @PathVariable cartId: Long): Mono<Void> {
        return shoppingCartService.deleteCart(cartId)
    }
}
