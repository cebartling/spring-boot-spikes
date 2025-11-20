package com.pintailconsultingllc.resiliencyspike.fixtures

import com.pintailconsultingllc.resiliencyspike.domain.CircuitBreakerState
import com.pintailconsultingllc.resiliencyspike.domain.RateLimiterMetrics
import com.pintailconsultingllc.resiliencyspike.domain.ResilienceEvent
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
}
