package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.domain.CartStatus
import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import com.pintailconsultingllc.resiliencyspike.repository.ShoppingCartRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShoppingCartService Tests")
class ShoppingCartServiceTest {

    @Mock
    private lateinit var cartRepository: ShoppingCartRepository

    @Mock
    private lateinit var cartStateHistoryService: CartStateHistoryService

    @InjectMocks
    private lateinit var shoppingCartService: ShoppingCartService

    @Test
    @DisplayName("Should create a new cart successfully")
    fun testCreateCart() {
        // Given
        val sessionId = "test-session-123"
        val cart = TestFixtures.createShoppingCart(sessionId = sessionId)

        whenever(cartRepository.save(any())).thenReturn(Mono.just(cart))
        whenever(cartStateHistoryService.recordEvent(any(), any(), anyOrNull())).thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.createCart(sessionId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.sessionId == sessionId && it.status == CartStatus.ACTIVE }
            .verifyComplete()

        verify(cartRepository).save(any())
        verify(cartStateHistoryService).recordEvent(any(), any(), anyOrNull())
    }

    @Test
    @DisplayName("Should create cart with user ID")
    fun testCreateCartWithUserId() {
        // Given
        val sessionId = "test-session-123"
        val userId = "user-123"
        val cart = TestFixtures.createShoppingCart(sessionId = sessionId, userId = userId)

        whenever(cartRepository.save(any())).thenReturn(Mono.just(cart))
        whenever(cartStateHistoryService.recordEvent(any(), any(), anyOrNull())).thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.createCart(sessionId, userId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.userId == userId }
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find or create cart when cart exists")
    fun testFindOrCreateCartWhenExists() {
        // Given
        val sessionId = "test-session-123"
        val existingCart = TestFixtures.createShoppingCart(sessionId = sessionId)

        whenever(cartRepository.findBySessionIdAndStatus(eq(sessionId), eq(CartStatus.ACTIVE)))
            .thenReturn(Mono.just(existingCart))
        // Add fallback stubs in case switchIfEmpty evaluates eagerly
        whenever(cartRepository.save(any())).thenReturn(Mono.just(existingCart))
        whenever(cartStateHistoryService.recordEvent(any(), any(), anyOrNull()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.findOrCreateCart(sessionId)

        // Then
        StepVerifier.create(result)
            .expectNext(existingCart)
            .verifyComplete()

        verify(cartRepository).findBySessionIdAndStatus(eq(sessionId), eq(CartStatus.ACTIVE))
    }

    @Test
    @DisplayName("Should create cart when cart does not exist")
    fun testFindOrCreateCartWhenNotExists() {
        // Given
        val sessionId = "test-session-123"
        val newCart = TestFixtures.createShoppingCart(sessionId = sessionId)

        whenever(cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE))
            .thenReturn(Mono.empty())
        whenever(cartRepository.save(any())).thenReturn(Mono.just(newCart))
        whenever(cartStateHistoryService.recordEvent(any(), any(), anyOrNull()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.findOrCreateCart(sessionId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.sessionId == sessionId }
            .verifyComplete()

        verify(cartRepository).save(any())
    }

    @Test
    @DisplayName("Should find cart by ID")
    fun testFindCartById() {
        // Given
        val cartId = 1L
        val cart = TestFixtures.createShoppingCart(id = cartId)

        whenever(cartRepository.findById(cartId)).thenReturn(Mono.just(cart))

        // When
        val result = shoppingCartService.findCartById(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(cart)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find cart by UUID")
    fun testFindCartByUuid() {
        // Given
        val cartUuid = UUID.randomUUID()
        val cart = TestFixtures.createShoppingCart(cartUuid = cartUuid)

        whenever(cartRepository.findByCartUuid(cartUuid)).thenReturn(Mono.just(cart))

        // When
        val result = shoppingCartService.findCartByUuid(cartUuid)

        // Then
        StepVerifier.create(result)
            .expectNext(cart)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should update cart status to ABANDONED")
    fun testAbandonCart() {
        // Given
        val cartId = 1L
        val originalCart = TestFixtures.createShoppingCart(id = cartId, status = CartStatus.ACTIVE)
        val abandonedCart = originalCart.copy(status = CartStatus.ABANDONED)

        whenever(cartRepository.findById(cartId)).thenReturn(Mono.just(originalCart))
        whenever(cartRepository.save(any())).thenReturn(Mono.just(abandonedCart))
        whenever(cartStateHistoryService.recordStatusChange(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.abandonCart(cartId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.status == CartStatus.ABANDONED }
            .verifyComplete()

        verify(cartStateHistoryService).recordStatusChange(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    @DisplayName("Should update cart status to CONVERTED")
    fun testConvertCart() {
        // Given
        val cartId = 1L
        val originalCart = TestFixtures.createShoppingCart(id = cartId, status = CartStatus.ACTIVE)
        val convertedCart = originalCart.copy(status = CartStatus.CONVERTED, convertedAt = OffsetDateTime.now())

        whenever(cartRepository.findById(cartId)).thenReturn(Mono.just(originalCart))
        whenever(cartRepository.save(any())).thenReturn(Mono.just(convertedCart))
        whenever(cartStateHistoryService.recordStatusChange(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.convertCart(cartId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.status == CartStatus.CONVERTED && it.convertedAt != null }
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find expired carts")
    fun testFindExpiredCarts() {
        // Given
        val expiredCart = TestFixtures.createShoppingCart(
            expiresAt = OffsetDateTime.now().minusHours(1),
            status = CartStatus.ACTIVE
        )

        whenever(cartRepository.findExpiredCarts(any())).thenReturn(Flux.just(expiredCart))

        // When
        val result = shoppingCartService.findExpiredCarts()

        // Then
        StepVerifier.create(result)
            .expectNext(expiredCart)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find abandoned carts")
    fun testFindAbandonedCarts() {
        // Given
        val abandonedCart = TestFixtures.createShoppingCart(
            updatedAt = OffsetDateTime.now().minusHours(25),
            status = CartStatus.ACTIVE
        )

        whenever(cartRepository.findAbandonedCarts(any())).thenReturn(Flux.just(abandonedCart))

        // When
        val result = shoppingCartService.findAbandonedCarts(24)

        // Then
        StepVerifier.create(result)
            .expectNext(abandonedCart)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should process expired carts")
    fun testProcessExpiredCarts() {
        // Given
        val expiredCart1 = TestFixtures.createShoppingCart(id = 1L, expiresAt = OffsetDateTime.now().minusHours(1))
        val expiredCart2 = TestFixtures.createShoppingCart(id = 2L, expiresAt = OffsetDateTime.now().minusHours(2))

        whenever(cartRepository.findExpiredCarts(any())).thenReturn(Flux.just(expiredCart1, expiredCart2))
        whenever(cartRepository.findById(any<Long>())).thenReturn(Mono.just(expiredCart1))
        whenever(cartRepository.save(any())).thenReturn(Mono.just(expiredCart1.copy(status = CartStatus.EXPIRED)))
        whenever(cartStateHistoryService.recordStatusChange(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = shoppingCartService.processExpiredCarts()

        // Then
        StepVerifier.create(result)
            .expectNext(2L)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should associate cart with user")
    fun testAssociateCartWithUser() {
        // Given
        val cartId = 1L
        val userId = "user-123"
        val cart = TestFixtures.createShoppingCart(id = cartId, userId = null)
        val updatedCart = cart.copy(userId = userId)

        whenever(cartRepository.findById(cartId)).thenReturn(Mono.just(cart))
        whenever(cartRepository.save(any())).thenReturn(Mono.just(updatedCart))

        // When
        val result = shoppingCartService.associateCartWithUser(cartId, userId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.userId == userId }
            .verifyComplete()
    }

    @Test
    @DisplayName("Should update cart expiration time")
    fun testUpdateCartExpiration() {
        // Given
        val cartId = 1L
        val newExpiration = OffsetDateTime.now().plusDays(7)
        val cart = TestFixtures.createShoppingCart(id = cartId)
        val updatedCart = cart.copy(expiresAt = newExpiration)

        whenever(cartRepository.findById(cartId)).thenReturn(Mono.just(cart))
        whenever(cartRepository.save(any())).thenReturn(Mono.just(updatedCart))

        // When
        val result = shoppingCartService.updateCartExpiration(cartId, newExpiration)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.expiresAt == newExpiration }
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find carts by status")
    fun testFindCartsByStatus() {
        // Given
        val activeCart1 = TestFixtures.createShoppingCart(id = 1L, status = CartStatus.ACTIVE)
        val activeCart2 = TestFixtures.createShoppingCart(id = 2L, status = CartStatus.ACTIVE)

        whenever(cartRepository.findByStatus(CartStatus.ACTIVE))
            .thenReturn(Flux.just(activeCart1, activeCart2))

        // When
        val result = shoppingCartService.findCartsByStatus(CartStatus.ACTIVE)

        // Then
        StepVerifier.create(result)
            .expectNext(activeCart1, activeCart2)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should count carts by status")
    fun testCountCartsByStatus() {
        // Given
        whenever(cartRepository.countByStatus(CartStatus.ACTIVE)).thenReturn(Mono.just(5L))

        // When
        val result = shoppingCartService.countCartsByStatus(CartStatus.ACTIVE)

        // Then
        StepVerifier.create(result)
            .expectNext(5L)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should delete cart")
    fun testDeleteCart() {
        // Given
        val cartId = 1L
        whenever(cartRepository.deleteById(cartId)).thenReturn(Mono.empty())

        // When
        val result = shoppingCartService.deleteCart(cartId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(cartRepository).deleteById(cartId)
    }
}
