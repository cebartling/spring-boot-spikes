package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import com.pintailconsultingllc.resiliencyspike.dto.AddItemToCartRequest
import com.pintailconsultingllc.resiliencyspike.dto.ApplyItemDiscountRequest
import com.pintailconsultingllc.resiliencyspike.dto.UpdateItemMetadataRequest
import com.pintailconsultingllc.resiliencyspike.dto.UpdateItemQuantityRequest
import com.pintailconsultingllc.resiliencyspike.service.CartItemService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(SpringExtension::class, MockitoExtension::class)
@WebFluxTest(CartItemController::class)
@DisplayName("CartItemController API Contract Tests")
class CartItemControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var cartItemService: CartItemService

    private val productId = UUID.randomUUID()

    private val testCartItem = CartItem(
        id = 1L,
        cartId = 100L,
        productId = productId,
        sku = "WIDGET-001",
        productName = "Test Widget",
        quantity = 2,
        unitPriceCents = 2500L,
        lineTotalCents = 5000L,
        discountAmountCents = 0L,
        metadata = null,
        addedAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items - should get all cart items")
    fun testGetCartItems() {
        whenever(cartItemService.findCartItems(100L))
            .thenReturn(Flux.just(testCartItem))

        webTestClient.get()
            .uri("/api/v1/carts/100/items")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartItemService).findCartItems(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/{productId} - should get specific cart item")
    fun testGetCartItem() {
        whenever(cartItemService.findCartItem(100L, productId))
            .thenReturn(Mono.just(testCartItem))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/$productId")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.cartId").isEqualTo(100)
            .jsonPath("$.productId").isEqualTo(productId.toString())
            .jsonPath("$.sku").isEqualTo("WIDGET-001")

        verify(cartItemService).findCartItem(100L, productId)
    }

    @Test
    @DisplayName("POST /api/v1/carts/{cartId}/items - should add item to cart")
    fun testAddItemToCart() {
        val request = AddItemToCartRequest(
            productId = productId,
            quantity = 2
        )

        whenever(cartItemService.addItemToCart(100L, productId, 2))
            .thenReturn(Mono.just(testCartItem))

        webTestClient.post()
            .uri("/api/v1/carts/100/items")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId.toString())
            .jsonPath("$.quantity").isEqualTo(2)

        verify(cartItemService).addItemToCart(100L, productId, 2)
    }

    @Test
    @DisplayName("PUT /api/v1/carts/{cartId}/items/{productId}/quantity - should update item quantity")
    fun testUpdateItemQuantity() {
        val request = UpdateItemQuantityRequest(quantity = 5)
        val updatedItem = testCartItem.copy(quantity = 5, lineTotalCents = 12500L)

        whenever(cartItemService.updateItemQuantity(100L, productId, 5))
            .thenReturn(Mono.just(updatedItem))

        webTestClient.put()
            .uri("/api/v1/carts/100/items/$productId/quantity")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(5)
            .jsonPath("$.lineTotalCents").isEqualTo(12500)

        verify(cartItemService).updateItemQuantity(100L, productId, 5)
    }

    @Test
    @DisplayName("PUT /api/v1/carts/{cartId}/items/{productId}/discount - should apply item discount")
    fun testApplyItemDiscount() {
        val request = ApplyItemDiscountRequest(discountAmountCents = 500L)
        val discountedItem = testCartItem.copy(discountAmountCents = 500L)

        whenever(cartItemService.applyItemDiscount(100L, productId, 500L))
            .thenReturn(Mono.just(discountedItem))

        webTestClient.put()
            .uri("/api/v1/carts/100/items/$productId/discount")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.discountAmountCents").isEqualTo(500)

        verify(cartItemService).applyItemDiscount(100L, productId, 500L)
    }

    @Test
    @DisplayName("PUT /api/v1/carts/{cartId}/items/{productId}/metadata - should update item metadata")
    fun testUpdateItemMetadata() {
        val request = UpdateItemMetadataRequest(metadata = """{"gift_wrap": true}""")
        val updatedItem = testCartItem.copy(metadata = """{"gift_wrap": true}""")

        whenever(cartItemService.updateItemMetadata(100L, productId, """{"gift_wrap": true}"""))
            .thenReturn(Mono.just(updatedItem))

        webTestClient.put()
            .uri("/api/v1/carts/100/items/$productId/metadata")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk

        verify(cartItemService).updateItemMetadata(100L, productId, """{"gift_wrap": true}""")
    }

    @Test
    @DisplayName("DELETE /api/v1/carts/{cartId}/items/{productId} - should remove item from cart")
    fun testRemoveItemFromCart() {
        whenever(cartItemService.removeItemFromCart(100L, productId))
            .thenReturn(Mono.empty())

        webTestClient.delete()
            .uri("/api/v1/carts/100/items/$productId")
            .exchange()
            .expectStatus().isNoContent

        verify(cartItemService).removeItemFromCart(100L, productId)
    }

    @Test
    @DisplayName("DELETE /api/v1/carts/{cartId}/items - should clear all cart items")
    fun testClearCart() {
        whenever(cartItemService.clearCart(100L))
            .thenReturn(Mono.empty())

        webTestClient.delete()
            .uri("/api/v1/carts/100/items")
            .exchange()
            .expectStatus().isNoContent

        verify(cartItemService).clearCart(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/totals - should get cart totals")
    fun testGetCartTotals() {
        whenever(cartItemService.calculateCartTotal(100L))
            .thenReturn(Mono.just(15000L))
        whenever(cartItemService.calculateCartItemCount(100L))
            .thenReturn(Mono.just(3))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/totals")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.subtotalCents").isEqualTo(15000)
            .jsonPath("$.totalAmountCents").isEqualTo(15000)
            .jsonPath("$.itemCount").isEqualTo(3)

        verify(cartItemService).calculateCartTotal(100L)
        verify(cartItemService).calculateCartItemCount(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/count - should count cart items")
    fun testCountCartItems() {
        whenever(cartItemService.countCartItems(100L))
            .thenReturn(Mono.just(5L))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/count")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isEqualTo(5)

        verify(cartItemService).countCartItems(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/discounted - should get discounted items")
    fun testGetDiscountedItems() {
        val discountedItem = testCartItem.copy(discountAmountCents = 500L)

        whenever(cartItemService.findDiscountedItems(100L))
            .thenReturn(Flux.just(discountedItem))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/discounted")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartItemService).findDiscountedItems(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/high-value - should get high value items")
    fun testGetHighValueItems() {
        whenever(cartItemService.findHighValueItems(100L, 10000L))
            .thenReturn(Flux.just(testCartItem))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/high-value?minPriceCents=10000")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartItemService).findHighValueItems(100L, 10000L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/bulk - should get bulk items")
    fun testGetBulkItems() {
        whenever(cartItemService.findBulkItems(100L, 10))
            .thenReturn(Flux.just(testCartItem))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/bulk?minQuantity=10")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(cartItemService).findBulkItems(100L, 10)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/{productId}/validate - should validate item availability")
    fun testValidateItemAvailability() {
        whenever(cartItemService.validateItemAvailability(100L, productId))
            .thenReturn(Mono.just(true))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/$productId/validate")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId.toString())
            .jsonPath("$.available").isEqualTo(true)
            .jsonPath("$.reason").doesNotExist()

        verify(cartItemService).validateItemAvailability(100L, productId)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/{productId}/validate - should return unavailable when item not available")
    fun testValidateItemAvailabilityUnavailable() {
        whenever(cartItemService.validateItemAvailability(100L, productId))
            .thenReturn(Mono.just(false))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/$productId/validate")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.productId").isEqualTo(productId.toString())
            .jsonPath("$.available").isEqualTo(false)
            .jsonPath("$.reason").isEqualTo("Product is not available or out of stock")

        verify(cartItemService).validateItemAvailability(100L, productId)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/validate - should validate all cart items")
    fun testValidateCartItems() {
        whenever(cartItemService.validateCartItems(100L))
            .thenReturn(Flux.just(
                Pair(testCartItem, true),
                Pair(testCartItem.copy(id = 2L), false)
            ))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/validate")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.cartId").isEqualTo(100)
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].available").isEqualTo(true)
            .jsonPath("$.items[1].available").isEqualTo(false)
            .jsonPath("$.allItemsValid").isEqualTo(false)

        verify(cartItemService).validateCartItems(100L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId}/items/validate - should return all valid when all items available")
    fun testValidateCartItemsAllValid() {
        whenever(cartItemService.validateCartItems(100L))
            .thenReturn(Flux.just(
                Pair(testCartItem, true),
                Pair(testCartItem.copy(id = 2L), true)
            ))

        webTestClient.get()
            .uri("/api/v1/carts/100/items/validate")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allItemsValid").isEqualTo(true)

        verify(cartItemService).validateCartItems(100L)
    }
}
