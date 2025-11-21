package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import com.pintailconsultingllc.resiliencyspike.repository.CartItemRepository
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("CartItemService Tests")
class CartItemServiceTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var cartStateHistoryService: CartStateHistoryService

    @InjectMocks
    private lateinit var cartItemService: CartItemService

    @Test
    @DisplayName("Should add new item to cart")
    fun testAddNewItemToCart() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val quantity = 2
        val product = TestFixtures.createProduct(id = productId, price = BigDecimal("99.99"))
        val cartItem = TestFixtures.createCartItem(
            cartId = cartId,
            productId = productId,
            quantity = quantity,
            unitPriceCents = 9999
        )

        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))
        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.empty())
        whenever(cartItemRepository.save(any())).thenReturn(Mono.just(cartItem))
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.addItemToCart(cartId, productId, quantity)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.quantity == quantity && it.productId == productId }
            .verifyComplete()

        verify(cartItemRepository).save(any())
        verify(cartStateHistoryService).recordItemEvent(any(), any(), any())
    }

    @Test
    @DisplayName("Should update quantity when item already exists in cart")
    fun testAddItemToCartUpdatesQuantity() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val existingQuantity = 2
        val additionalQuantity = 3
        val product = TestFixtures.createProduct(id = productId)
        val existingItem = TestFixtures.createCartItem(
            cartId = cartId,
            productId = productId,
            quantity = existingQuantity
        )
        val updatedItem = existingItem.copy(quantity = existingQuantity + additionalQuantity)

        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))
        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(existingItem))
        whenever(cartItemRepository.save(any())).thenReturn(Mono.just(updatedItem))
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.addItemToCart(cartId, productId, additionalQuantity)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.quantity == existingQuantity + additionalQuantity }
            .verifyComplete()

        verify(cartStateHistoryService).recordItemEvent(any(), any(), any())
    }

    @Test
    @DisplayName("Should remove item from cart")
    fun testRemoveItemFromCart() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))
        whenever(cartItemRepository.delete(cartItem)).thenReturn(Mono.empty())
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.removeItemFromCart(cartId, productId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(cartItemRepository).delete(cartItem)
        verify(cartStateHistoryService).recordItemEvent(any(), any(), any())
    }

    @Test
    @DisplayName("Should update item quantity")
    fun testUpdateItemQuantity() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val newQuantity = 5
        val existingItem = TestFixtures.createCartItem(cartId = cartId, productId = productId, quantity = 2)
        val updatedItem = existingItem.copy(quantity = newQuantity)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(existingItem))
        whenever(cartItemRepository.save(any())).thenReturn(Mono.just(updatedItem))
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.updateItemQuantity(cartId, productId, newQuantity)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.quantity == newQuantity }
            .verifyComplete()

        verify(cartStateHistoryService).recordItemEvent(any(), any(), any())
    }

    @Test
    @DisplayName("Should apply discount to item")
    fun testApplyItemDiscount() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val discountAmountCents = 1000L // $10.00
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId)
        val discountedItem = cartItem.copy(discountAmountCents = discountAmountCents)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))
        whenever(cartItemRepository.save(any())).thenReturn(Mono.just(discountedItem))
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.applyItemDiscount(cartId, productId, discountAmountCents)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.discountAmountCents == discountAmountCents }
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find all cart items")
    fun testFindCartItems() {
        // Given
        val cartId = 1L
        val item1 = TestFixtures.createCartItem(id = 1L, cartId = cartId)
        val item2 = TestFixtures.createCartItem(id = 2L, cartId = cartId)

        whenever(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(item1, item2))

        // When
        val result = cartItemService.findCartItems(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(item1, item2)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find specific cart item")
    fun testFindCartItem() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))

        // When
        val result = cartItemService.findCartItem(cartId, productId)

        // Then
        StepVerifier.create(result)
            .expectNext(cartItem)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should count cart items")
    fun testCountCartItems() {
        // Given
        val cartId = 1L
        whenever(cartItemRepository.countByCartId(cartId)).thenReturn(Mono.just(3L))

        // When
        val result = cartItemService.countCartItems(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(3L)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should calculate cart total")
    fun testCalculateCartTotal() {
        // Given
        val cartId = 1L
        val totalCents = 29997L // $299.97
        whenever(cartItemRepository.calculateCartTotal(cartId)).thenReturn(Mono.just(totalCents))

        // When
        val result = cartItemService.calculateCartTotal(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(totalCents)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should calculate cart item count")
    fun testCalculateCartItemCount() {
        // Given
        val cartId = 1L
        whenever(cartItemRepository.calculateCartItemCount(cartId)).thenReturn(Mono.just(5))

        // When
        val result = cartItemService.calculateCartItemCount(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(5)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should clear cart")
    fun testClearCart() {
        // Given
        val cartId = 1L
        whenever(cartItemRepository.deleteByCartId(cartId)).thenReturn(Mono.empty())
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.clearCart(cartId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(cartItemRepository).deleteByCartId(cartId)
        verify(cartStateHistoryService).recordItemEvent(any(), any(), any())
    }

    @Test
    @DisplayName("Should find discounted items")
    fun testFindDiscountedItems() {
        // Given
        val cartId = 1L
        val discountedItem = TestFixtures.createCartItem(cartId = cartId, discountAmountCents = 1000L)

        whenever(cartItemRepository.findDiscountedItems(cartId)).thenReturn(Flux.just(discountedItem))

        // When
        val result = cartItemService.findDiscountedItems(cartId)

        // Then
        StepVerifier.create(result)
            .expectNext(discountedItem)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find high value items")
    fun testFindHighValueItems() {
        // Given
        val cartId = 1L
        val minPriceCents = 10000L // $100.00
        val highValueItem = TestFixtures.createCartItem(cartId = cartId, unitPriceCents = 15000L)

        whenever(cartItemRepository.findHighValueItems(cartId, minPriceCents))
            .thenReturn(Flux.just(highValueItem))

        // When
        val result = cartItemService.findHighValueItems(cartId, minPriceCents)

        // Then
        StepVerifier.create(result)
            .expectNext(highValueItem)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should find bulk items")
    fun testFindBulkItems() {
        // Given
        val cartId = 1L
        val minQuantity = 5
        val bulkItem = TestFixtures.createCartItem(cartId = cartId, quantity = 10)

        whenever(cartItemRepository.findBulkItems(cartId, minQuantity)).thenReturn(Flux.just(bulkItem))

        // When
        val result = cartItemService.findBulkItems(cartId, minQuantity)

        // Then
        StepVerifier.create(result)
            .expectNext(bulkItem)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should update item metadata")
    fun testUpdateItemMetadata() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val metadata = """{"color": "red", "size": "large"}"""
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId)
        val updatedItem = cartItem.copy(metadata = metadata)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))
        whenever(cartItemRepository.save(any())).thenReturn(Mono.just(updatedItem))
        whenever(cartStateHistoryService.recordItemEvent(any(), any(), any()))
            .thenReturn(Mono.just(TestFixtures.createCartStateHistory()))

        // When
        val result = cartItemService.updateItemMetadata(cartId, productId, metadata)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.metadata == metadata }
            .verifyComplete()
    }

    @Test
    @DisplayName("Should validate item availability when product is active and in stock")
    fun testValidateItemAvailabilitySuccess() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId, quantity = 2)
        val product = TestFixtures.createProduct(id = productId, isActive = true, stockQuantity = 10)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))
        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))

        // When
        val result = cartItemService.validateItemAvailability(cartId, productId)

        // Then
        StepVerifier.create(result)
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should validate item availability returns false when insufficient stock")
    fun testValidateItemAvailabilityInsufficientStock() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId, quantity = 10)
        val product = TestFixtures.createProduct(id = productId, isActive = true, stockQuantity = 5)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))
        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))

        // When
        val result = cartItemService.validateItemAvailability(cartId, productId)

        // Then
        StepVerifier.create(result)
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should validate item availability returns false when product is inactive")
    fun testValidateItemAvailabilityInactiveProduct() {
        // Given
        val cartId = 1L
        val productId = UUID.randomUUID()
        val cartItem = TestFixtures.createCartItem(cartId = cartId, productId = productId, quantity = 2)
        val product = TestFixtures.createProduct(id = productId, isActive = false, stockQuantity = 10)

        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId))
            .thenReturn(Mono.just(cartItem))
        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))

        // When
        val result = cartItemService.validateItemAvailability(cartId, productId)

        // Then
        StepVerifier.create(result)
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    @DisplayName("Should validate all cart items")
    fun testValidateCartItems() {
        // Given
        val cartId = 1L
        val productId1 = UUID.randomUUID()
        val productId2 = UUID.randomUUID()
        val item1 = TestFixtures.createCartItem(id = 1L, cartId = cartId, productId = productId1, quantity = 2)
        val item2 = TestFixtures.createCartItem(id = 2L, cartId = cartId, productId = productId2, quantity = 5)
        val product1 = TestFixtures.createProduct(id = productId1, isActive = true, stockQuantity = 10)
        val product2 = TestFixtures.createProduct(id = productId2, isActive = true, stockQuantity = 3)

        whenever(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(item1, item2))
        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId1))
            .thenReturn(Mono.just(item1))
        whenever(cartItemRepository.findByCartIdAndProductId(cartId, productId2))
            .thenReturn(Mono.just(item2))
        whenever(productRepository.findById(productId1)).thenReturn(Mono.just(product1))
        whenever(productRepository.findById(productId2)).thenReturn(Mono.just(product2))

        // When
        val result = cartItemService.validateCartItems(cartId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { (item, isValid) -> item.id == 1L && isValid }
            .expectNextMatches { (item, isValid) -> item.id == 2L && !isValid } // Insufficient stock
            .verifyComplete()
    }
}
