package com.pintailconsultingllc.resiliencyspike.fixtures

import com.pintailconsultingllc.resiliencyspike.domain.Category
import com.pintailconsultingllc.resiliencyspike.domain.CircuitBreakerState
import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.domain.RateLimiterMetrics
import com.pintailconsultingllc.resiliencyspike.domain.ResilienceEvent
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
}
