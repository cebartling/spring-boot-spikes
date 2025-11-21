package com.pintailconsultingllc.resiliencyspike.fixtures

import com.pintailconsultingllc.resiliencyspike.domain.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * Test fixtures for creating test data
 */
object TestFixtures {

    fun createResilienceEvent(
        id: UUID? = UUID.randomUUID(),
        eventType: String = "CIRCUIT_BREAKER",
        eventName: String = "test-circuit-breaker",
        status: String = "SUCCESS",
        errorMessage: String? = null,
        metadata: String? = """{"key": "value"}""",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): ResilienceEvent {
        return ResilienceEvent(
            id = id,
            eventType = eventType,
            eventName = eventName,
            status = status,
            errorMessage = errorMessage,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createCircuitBreakerState(
        id: UUID? = UUID.randomUUID(),
        circuitBreakerName: String = "test-circuit-breaker",
        state: String = "CLOSED",
        failureCount: Int = 0,
        successCount: Int = 10,
        lastFailureTime: OffsetDateTime? = null,
        lastSuccessTime: OffsetDateTime? = OffsetDateTime.now(),
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): CircuitBreakerState {
        return CircuitBreakerState(
            id = id,
            circuitBreakerName = circuitBreakerName,
            state = state,
            failureCount = failureCount,
            successCount = successCount,
            lastFailureTime = lastFailureTime,
            lastSuccessTime = lastSuccessTime,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createRateLimiterMetrics(
        id: UUID? = UUID.randomUUID(),
        rateLimiterName: String = "test-rate-limiter",
        permittedCalls: Int = 90,
        rejectedCalls: Int = 10,
        windowStart: OffsetDateTime = OffsetDateTime.now().minusMinutes(5),
        windowEnd: OffsetDateTime = OffsetDateTime.now(),
        createdAt: OffsetDateTime = OffsetDateTime.now()
    ): RateLimiterMetrics {
        return RateLimiterMetrics(
            id = id,
            rateLimiterName = rateLimiterName,
            permittedCalls = permittedCalls,
            rejectedCalls = rejectedCalls,
            windowStart = windowStart,
            windowEnd = windowEnd,
            createdAt = createdAt
        )
    }

    fun createCategory(
        id: UUID? = UUID.randomUUID(),
        name: String = "Test Category",
        description: String? = "Test category description",
        parentCategoryId: UUID? = null,
        isActive: Boolean = true,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): Category {
        return Category(
            id = id,
            name = name,
            description = description,
            parentCategoryId = parentCategoryId,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createProduct(
        id: UUID? = UUID.randomUUID(),
        sku: String = "TEST-SKU-001",
        name: String = "Test Product",
        description: String? = "Test product description",
        categoryId: UUID = UUID.randomUUID(),
        price: BigDecimal = BigDecimal("99.99"),
        stockQuantity: Int = 50,
        isActive: Boolean = true,
        metadata: String? = """{"brand": "TestBrand"}""",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): Product {
        return Product(
            id = id,
            sku = sku,
            name = name,
            description = description,
            categoryId = categoryId,
            price = price,
            stockQuantity = stockQuantity,
            isActive = isActive,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createShoppingCart(
        id: Long? = 1L,
        cartUuid: UUID = UUID.randomUUID(),
        userId: String? = null,
        sessionId: String = "test-session-123",
        status: CartStatus = CartStatus.ACTIVE,
        currencyCode: String = "USD",
        subtotalCents: Long = 0,
        taxAmountCents: Long = 0,
        discountAmountCents: Long = 0,
        totalAmountCents: Long = 0,
        itemCount: Int = 0,
        metadata: String? = null,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
        expiresAt: OffsetDateTime? = null,
        convertedAt: OffsetDateTime? = null,
        items: List<CartItem> = emptyList()
    ): ShoppingCart {
        return ShoppingCart(
            id = id,
            cartUuid = cartUuid,
            userId = userId,
            sessionId = sessionId,
            status = status,
            currencyCode = currencyCode,
            subtotalCents = subtotalCents,
            taxAmountCents = taxAmountCents,
            discountAmountCents = discountAmountCents,
            totalAmountCents = totalAmountCents,
            itemCount = itemCount,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = updatedAt,
            expiresAt = expiresAt,
            convertedAt = convertedAt,
            items = items
        )
    }

    fun createCartItem(
        id: Long? = 1L,
        cartId: Long = 1L,
        productId: UUID = UUID.randomUUID(),
        sku: String = "TEST-SKU-001",
        productName: String = "Test Product",
        quantity: Int = 1,
        unitPriceCents: Long = 9999, // $99.99
        lineTotalCents: Long = 9999,
        discountAmountCents: Long = 0,
        metadata: String? = null,
        addedAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
        product: Product? = null
    ): CartItem {
        return CartItem(
            id = id,
            cartId = cartId,
            productId = productId,
            sku = sku,
            productName = productName,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            lineTotalCents = lineTotalCents,
            discountAmountCents = discountAmountCents,
            metadata = metadata,
            addedAt = addedAt,
            updatedAt = updatedAt,
            product = product
        )
    }

    fun createCartStateHistory(
        id: Long? = 1L,
        cartId: Long = 1L,
        eventType: CartEventType = CartEventType.CREATED,
        previousStatus: String? = null,
        newStatus: String? = null,
        eventData: String? = null,
        createdAt: OffsetDateTime = OffsetDateTime.now()
    ): CartStateHistory {
        return CartStateHistory(
            id = id,
            cartId = cartId,
            eventType = eventType,
            previousStatus = previousStatus,
            newStatus = newStatus,
            eventData = eventData,
            createdAt = createdAt
        )
    }
}
