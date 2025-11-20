package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.RateLimiterMetrics
import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("RateLimiterMetricsRepository Unit Tests")
class RateLimiterMetricsRepositoryTest {

    @Mock
    private lateinit var rateLimiterMetricsRepository: RateLimiterMetricsRepository

    @Test
    @DisplayName("Should save rate limiter metrics and return saved entity")
    fun shouldSaveRateLimiterMetricsAndReturnSavedEntity() {
        // Given
        val metrics = TestFixtures.createRateLimiterMetrics(id = null)
        val savedMetrics = metrics.copy(id = UUID.randomUUID())

        whenever(rateLimiterMetricsRepository.save(metrics))
            .thenReturn(Mono.just(savedMetrics))

        // When
        val result = rateLimiterMetricsRepository.save(metrics)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id != null }
            .verifyComplete()

        verify(rateLimiterMetricsRepository).save(metrics)
    }

    @Test
    @DisplayName("Should find all metrics for a specific rate limiter")
    fun shouldFindAllMetricsForSpecificRateLimiter() {
        // Given
        val rateLimiterName = "api-rate-limiter"
        val metrics = listOf(
            TestFixtures.createRateLimiterMetrics(rateLimiterName = rateLimiterName, permittedCalls = 100),
            TestFixtures.createRateLimiterMetrics(rateLimiterName = rateLimiterName, permittedCalls = 95)
        )

        whenever(rateLimiterMetricsRepository.findByRateLimiterName(rateLimiterName))
            .thenReturn(Flux.fromIterable(metrics))

        // When
        val result = rateLimiterMetricsRepository.findByRateLimiterName(rateLimiterName)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findByRateLimiterName(rateLimiterName)
    }

    @Test
    @DisplayName("Should find metrics for rate limiter within time range")
    fun shouldFindMetricsForRateLimiterWithinTimeRange() {
        // Given
        val rateLimiterName = "api-rate-limiter"
        val start = OffsetDateTime.now().minusHours(1)
        val end = OffsetDateTime.now()
        val metrics = listOf(
            TestFixtures.createRateLimiterMetrics(
                rateLimiterName = rateLimiterName,
                windowStart = start,
                windowEnd = end
            )
        )

        whenever(rateLimiterMetricsRepository.findByRateLimiterNameAndTimeRange(rateLimiterName, start, end))
            .thenReturn(Flux.fromIterable(metrics))

        // When
        val result = rateLimiterMetricsRepository.findByRateLimiterNameAndTimeRange(rateLimiterName, start, end)

        // Then
        StepVerifier.create(result)
            .expectNext(metrics[0])
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findByRateLimiterNameAndTimeRange(rateLimiterName, start, end)
    }

    @Test
    @DisplayName("Should find recent metrics for rate limiter with limit")
    fun shouldFindRecentMetricsForRateLimiterWithLimit() {
        // Given
        val rateLimiterName = "api-rate-limiter"
        val limit = 10
        val metrics = listOf(
            TestFixtures.createRateLimiterMetrics(rateLimiterName = rateLimiterName)
        )

        whenever(rateLimiterMetricsRepository.findRecentByRateLimiterName(rateLimiterName, limit))
            .thenReturn(Flux.fromIterable(metrics))

        // When
        val result = rateLimiterMetricsRepository.findRecentByRateLimiterName(rateLimiterName, limit)

        // Then
        StepVerifier.create(result)
            .expectNext(metrics[0])
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findRecentByRateLimiterName(rateLimiterName, limit)
    }

    @Test
    @DisplayName("Should find all metrics within time range")
    fun shouldFindAllMetricsWithinTimeRange() {
        // Given
        val start = OffsetDateTime.now().minusHours(2)
        val end = OffsetDateTime.now()
        val metrics = listOf(
            TestFixtures.createRateLimiterMetrics(
                rateLimiterName = "limiter-1",
                windowStart = start,
                windowEnd = end
            ),
            TestFixtures.createRateLimiterMetrics(
                rateLimiterName = "limiter-2",
                windowStart = start,
                windowEnd = end
            )
        )

        whenever(rateLimiterMetricsRepository.findByTimeRange(start, end))
            .thenReturn(Flux.fromIterable(metrics))

        // When
        val result = rateLimiterMetricsRepository.findByTimeRange(start, end)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findByTimeRange(start, end)
    }

    @Test
    @DisplayName("Should find metrics with rejection rate above threshold")
    fun shouldFindMetricsWithRejectionRateAboveThreshold() {
        // Given
        val threshold = 0.1 // 10% rejection rate
        val metrics = listOf(
            TestFixtures.createRateLimiterMetrics(
                rateLimiterName = "limiter-1",
                permittedCalls = 80,
                rejectedCalls = 20 // 20% rejection rate
            ),
            TestFixtures.createRateLimiterMetrics(
                rateLimiterName = "limiter-2",
                permittedCalls = 85,
                rejectedCalls = 15 // 15% rejection rate
            )
        )

        whenever(rateLimiterMetricsRepository.findWithRejectionRateAbove(threshold))
            .thenReturn(Flux.fromIterable(metrics))

        // When
        val result = rateLimiterMetricsRepository.findWithRejectionRateAbove(threshold)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findWithRejectionRateAbove(threshold)
    }

    @Test
    @DisplayName("Should find metrics by ID")
    fun shouldFindMetricsById() {
        // Given
        val id = UUID.randomUUID()
        val metrics = TestFixtures.createRateLimiterMetrics(id = id)

        whenever(rateLimiterMetricsRepository.findById(id))
            .thenReturn(Mono.just(metrics))

        // When
        val result = rateLimiterMetricsRepository.findById(id)

        // Then
        StepVerifier.create(result)
            .expectNext(metrics)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findById(id)
    }

    @Test
    @DisplayName("Should delete metrics by ID")
    fun shouldDeleteMetricsById() {
        // Given
        val id = UUID.randomUUID()

        whenever(rateLimiterMetricsRepository.deleteById(id))
            .thenReturn(Mono.empty())

        // When
        val result = rateLimiterMetricsRepository.deleteById(id)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).deleteById(id)
    }

    @Test
    @DisplayName("Should return empty Flux when no metrics found for rate limiter")
    fun shouldReturnEmptyFluxWhenNoMetricsFoundForRateLimiter() {
        // Given
        val rateLimiterName = "nonexistent-limiter"

        whenever(rateLimiterMetricsRepository.findByRateLimiterName(rateLimiterName))
            .thenReturn(Flux.empty())

        // When
        val result = rateLimiterMetricsRepository.findByRateLimiterName(rateLimiterName)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findByRateLimiterName(rateLimiterName)
    }

    @Test
    @DisplayName("Should return empty Flux when no metrics in time range")
    fun shouldReturnEmptyFluxWhenNoMetricsInTimeRange() {
        // Given
        val start = OffsetDateTime.now().minusDays(7)
        val end = OffsetDateTime.now().minusDays(6)

        whenever(rateLimiterMetricsRepository.findByTimeRange(start, end))
            .thenReturn(Flux.empty())

        // When
        val result = rateLimiterMetricsRepository.findByTimeRange(start, end)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findByTimeRange(start, end)
    }

    @Test
    @DisplayName("Should handle metrics with zero rejected calls")
    fun shouldHandleMetricsWithZeroRejectedCalls() {
        // Given
        val rateLimiterName = "well-performing-limiter"
        val metrics = listOf(
            TestFixtures.createRateLimiterMetrics(
                rateLimiterName = rateLimiterName,
                permittedCalls = 100,
                rejectedCalls = 0
            )
        )

        whenever(rateLimiterMetricsRepository.findByRateLimiterName(rateLimiterName))
            .thenReturn(Flux.fromIterable(metrics))

        // When
        val result = rateLimiterMetricsRepository.findByRateLimiterName(rateLimiterName)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.rejectedCalls == 0 }
            .verifyComplete()

        verify(rateLimiterMetricsRepository).findByRateLimiterName(rateLimiterName)
    }
}
