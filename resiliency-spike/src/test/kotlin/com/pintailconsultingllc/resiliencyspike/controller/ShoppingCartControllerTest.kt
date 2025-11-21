package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart
import com.pintailconsultingllc.resiliencyspike.dto.AssociateCartWithUserRequest
import com.pintailconsultingllc.resiliencyspike.dto.CreateCartRequest
import com.pintailconsultingllc.resiliencyspike.dto.UpdateCartExpirationRequest
import com.pintailconsultingllc.resiliencyspike.service.ShoppingCartService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
@WebFluxTest(ShoppingCartController::class)
@DisplayName("ShoppingCartController API Contract Tests")
class ShoppingCartControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var shoppingCartService: ShoppingCartService

    private val testCart = ShoppingCart(
        id = 1L,
        cartUuid = UUID.randomUUID(),
        sessionId = "session-123",
        userId = "user-456",
        status = CartStatus.ACTIVE,
        currencyCode = "USD",
        subtotalCents = 5000L,
        taxAmountCents = 500L,
        discountAmountCents = 0L,
        totalAmountCents = 5500L,
        itemCount = 2,
        metadata = null,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
        expiresAt = OffsetDateTime.now().plusDays(1),
        convertedAt = null
    )

    @Test
    @DisplayName("POST /api/v1/carts - should create a new cart")
    fun testCreateCart() {
        val request = CreateCartRequest(
            sessionId = "session-123",
            userId = "user-456",
            expiresAt = null
        )

        whenever(shoppingCartService.createCart(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Mono.just(testCart))

        webTestClient.post()
            .uri("/api/v1/carts")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.sessionId").isEqualTo("session-123")
            .jsonPath("$.userId").isEqualTo("user-456")
            .jsonPath("$.status").isEqualTo("ACTIVE")

        verify(shoppingCartService).createCart("session-123", "user-456", null)
    }

    @Test
    @DisplayName("GET /api/v1/carts/{cartId} - should get cart by ID")
    fun testGetCartById() {
        whenever(shoppingCartService.findCartById(1L))
            .thenReturn(Mono.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/1")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.sessionId").isEqualTo("session-123")

        verify(shoppingCartService).findCartById(1L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/uuid/{cartUuid} - should get cart by UUID")
    fun testGetCartByUuid() {
        val uuid = testCart.cartUuid

        whenever(shoppingCartService.findCartByUuid(uuid))
            .thenReturn(Mono.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/uuid/$uuid")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.cartUuid").isEqualTo(uuid.toString())

        verify(shoppingCartService).findCartByUuid(uuid)
    }

    @Test
    @DisplayName("GET /api/v1/carts/session/{sessionId} - should get or create cart for session")
    fun testGetOrCreateCartForSession() {
        whenever(shoppingCartService.findOrCreateCart(any(), anyOrNull()))
            .thenReturn(Mono.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/session/session-123")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.sessionId").isEqualTo("session-123")

        verify(shoppingCartService).findOrCreateCart("session-123", null)
    }

    @Test
    @DisplayName("GET /api/v1/carts/session/{sessionId}?userId={userId} - should get or create cart with user")
    fun testGetOrCreateCartForSessionWithUser() {
        whenever(shoppingCartService.findOrCreateCart(any(), anyOrNull()))
            .thenReturn(Mono.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/session/session-123?userId=user-456")
            .exchange()
            .expectStatus().isOk

        verify(shoppingCartService).findOrCreateCart("session-123", "user-456")
    }

    @Test
    @DisplayName("GET /api/v1/carts/session/{sessionId}/current - should get current cart by session")
    fun testGetCartBySession() {
        whenever(shoppingCartService.findCartBySessionId("session-123"))
            .thenReturn(Mono.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/session/session-123/current")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.sessionId").isEqualTo("session-123")

        verify(shoppingCartService).findCartBySessionId("session-123")
    }

    @Test
    @DisplayName("GET /api/v1/carts/user/{userId} - should get carts by user ID")
    fun testGetCartsByUserId() {
        whenever(shoppingCartService.findCartsByUserId("user-456"))
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/user/user-456")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(shoppingCartService).findCartsByUserId("user-456")
    }

    @Test
    @DisplayName("GET /api/v1/carts/status/{status} - should get carts by status")
    fun testGetCartsByStatus() {
        whenever(shoppingCartService.findCartsByStatus(CartStatus.ACTIVE))
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/status/ACTIVE")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(shoppingCartService).findCartsByStatus(CartStatus.ACTIVE)
    }

    @Test
    @DisplayName("PUT /api/v1/carts/{cartId}/user - should associate cart with user")
    fun testAssociateCartWithUser() {
        val request = AssociateCartWithUserRequest(userId = "new-user")
        val updatedCart = testCart.copy(userId = "new-user")

        whenever(shoppingCartService.associateCartWithUser(1L, "new-user"))
            .thenReturn(Mono.just(updatedCart))

        webTestClient.put()
            .uri("/api/v1/carts/1/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo("new-user")

        verify(shoppingCartService).associateCartWithUser(1L, "new-user")
    }

    @Test
    @DisplayName("PUT /api/v1/carts/{cartId}/expiration - should update cart expiration")
    fun testUpdateCartExpiration() {
        val newExpiration = OffsetDateTime.now().plusDays(3)
        val request = UpdateCartExpirationRequest(expiresAt = newExpiration)
        val updatedCart = testCart.copy(expiresAt = newExpiration)

        whenever(shoppingCartService.updateCartExpiration(any(), any()))
            .thenReturn(Mono.just(updatedCart))

        webTestClient.put()
            .uri("/api/v1/carts/1/expiration")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk

        verify(shoppingCartService).updateCartExpiration(any(), any())
    }

    @Test
    @DisplayName("POST /api/v1/carts/{cartId}/abandon - should abandon cart")
    fun testAbandonCart() {
        val abandonedCart = testCart.copy(status = CartStatus.ABANDONED)

        whenever(shoppingCartService.abandonCart(1L))
            .thenReturn(Mono.just(abandonedCart))

        webTestClient.post()
            .uri("/api/v1/carts/1/abandon")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ABANDONED")

        verify(shoppingCartService).abandonCart(1L)
    }

    @Test
    @DisplayName("POST /api/v1/carts/{cartId}/convert - should convert cart")
    fun testConvertCart() {
        val convertedCart = testCart.copy(
            status = CartStatus.CONVERTED,
            convertedAt = OffsetDateTime.now()
        )

        whenever(shoppingCartService.convertCart(1L))
            .thenReturn(Mono.just(convertedCart))

        webTestClient.post()
            .uri("/api/v1/carts/1/convert")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CONVERTED")

        verify(shoppingCartService).convertCart(1L)
    }

    @Test
    @DisplayName("POST /api/v1/carts/{cartId}/expire - should expire cart")
    fun testExpireCart() {
        val expiredCart = testCart.copy(status = CartStatus.EXPIRED)

        whenever(shoppingCartService.expireCart(1L))
            .thenReturn(Mono.just(expiredCart))

        webTestClient.post()
            .uri("/api/v1/carts/1/expire")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("EXPIRED")

        verify(shoppingCartService).expireCart(1L)
    }

    @Test
    @DisplayName("POST /api/v1/carts/{cartId}/restore - should restore cart")
    fun testRestoreCart() {
        val restoredCart = testCart.copy(status = CartStatus.ACTIVE)

        whenever(shoppingCartService.restoreCart(1L))
            .thenReturn(Mono.just(restoredCart))

        webTestClient.post()
            .uri("/api/v1/carts/1/restore")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ACTIVE")

        verify(shoppingCartService).restoreCart(1L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/expired - should get expired carts")
    fun testGetExpiredCarts() {
        val expiredCart = testCart.copy(status = CartStatus.EXPIRED)

        whenever(shoppingCartService.findExpiredCarts())
            .thenReturn(Flux.just(expiredCart))

        webTestClient.get()
            .uri("/api/v1/carts/expired")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(shoppingCartService).findExpiredCarts()
    }

    @Test
    @DisplayName("GET /api/v1/carts/abandoned - should get abandoned carts")
    fun testGetAbandonedCarts() {
        whenever(shoppingCartService.findAbandonedCarts(24L))
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/abandoned")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(shoppingCartService).findAbandonedCarts(24L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/abandoned?hoursInactive=48 - should get abandoned carts with custom hours")
    fun testGetAbandonedCartsWithCustomHours() {
        whenever(shoppingCartService.findAbandonedCarts(48L))
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/abandoned?hoursInactive=48")
            .exchange()
            .expectStatus().isOk

        verify(shoppingCartService).findAbandonedCarts(48L)
    }

    @Test
    @DisplayName("POST /api/v1/carts/process-expired - should process expired carts")
    fun testProcessExpiredCarts() {
        whenever(shoppingCartService.processExpiredCarts())
            .thenReturn(Mono.just(5L))

        webTestClient.post()
            .uri("/api/v1/carts/process-expired")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isEqualTo(5)

        verify(shoppingCartService).processExpiredCarts()
    }

    @Test
    @DisplayName("POST /api/v1/carts/process-abandoned - should process abandoned carts")
    fun testProcessAbandonedCarts() {
        whenever(shoppingCartService.processAbandonedCarts(24L))
            .thenReturn(Mono.just(3L))

        webTestClient.post()
            .uri("/api/v1/carts/process-abandoned")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isEqualTo(3)

        verify(shoppingCartService).processAbandonedCarts(24L)
    }

    @Test
    @DisplayName("GET /api/v1/carts/with-items - should get carts with items")
    fun testGetCartsWithItems() {
        whenever(shoppingCartService.findCartsWithItems(CartStatus.ACTIVE))
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/with-items")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(shoppingCartService).findCartsWithItems(CartStatus.ACTIVE)
    }

    @Test
    @DisplayName("GET /api/v1/carts/with-items?status=CONVERTED - should get converted carts with items")
    fun testGetCartsWithItemsWithStatus() {
        whenever(shoppingCartService.findCartsWithItems(CartStatus.CONVERTED))
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/with-items?status=CONVERTED")
            .exchange()
            .expectStatus().isOk

        verify(shoppingCartService).findCartsWithItems(CartStatus.CONVERTED)
    }

    @Test
    @DisplayName("GET /api/v1/carts/empty - should get empty carts")
    fun testGetEmptyCarts() {
        whenever(shoppingCartService.findEmptyCarts())
            .thenReturn(Flux.just(testCart))

        webTestClient.get()
            .uri("/api/v1/carts/empty")
            .exchange()
            .expectStatus().isOk

        verify(shoppingCartService).findEmptyCarts()
    }

    @Test
    @DisplayName("GET /api/v1/carts/count/{status} - should count carts by status")
    fun testCountCartsByStatus() {
        whenever(shoppingCartService.countCartsByStatus(CartStatus.ACTIVE))
            .thenReturn(Mono.just(10L))

        webTestClient.get()
            .uri("/api/v1/carts/count/ACTIVE")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isEqualTo(10)

        verify(shoppingCartService).countCartsByStatus(CartStatus.ACTIVE)
    }

    @Test
    @DisplayName("GET /api/v1/carts/statistics - should get cart statistics")
    fun testGetCartStatistics() {
        whenever(shoppingCartService.countCartsByStatus(CartStatus.ACTIVE))
            .thenReturn(Mono.just(10L))
        whenever(shoppingCartService.countCartsByStatus(CartStatus.ABANDONED))
            .thenReturn(Mono.just(3L))
        whenever(shoppingCartService.countCartsByStatus(CartStatus.CONVERTED))
            .thenReturn(Mono.just(5L))
        whenever(shoppingCartService.countCartsByStatus(CartStatus.EXPIRED))
            .thenReturn(Mono.just(2L))

        webTestClient.get()
            .uri("/api/v1/carts/statistics")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalCarts").isEqualTo(20)
            .jsonPath("$.activeCarts").isEqualTo(10)
            .jsonPath("$.abandonedCarts").isEqualTo(3)
            .jsonPath("$.convertedCarts").isEqualTo(5)
            .jsonPath("$.expiredCarts").isEqualTo(2)

        verify(shoppingCartService).countCartsByStatus(CartStatus.ACTIVE)
        verify(shoppingCartService).countCartsByStatus(CartStatus.ABANDONED)
        verify(shoppingCartService).countCartsByStatus(CartStatus.CONVERTED)
        verify(shoppingCartService).countCartsByStatus(CartStatus.EXPIRED)
    }

    @Test
    @DisplayName("DELETE /api/v1/carts/{cartId} - should delete cart")
    fun testDeleteCart() {
        whenever(shoppingCartService.deleteCart(1L))
            .thenReturn(Mono.empty())

        webTestClient.delete()
            .uri("/api/v1/carts/1")
            .exchange()
            .expectStatus().isNoContent

        verify(shoppingCartService).deleteCart(1L)
    }
}
