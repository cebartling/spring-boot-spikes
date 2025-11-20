package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.RateLimiterMetrics
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.*

/**
 * Reactive repository for RateLimiterMetrics entities
 */
@Repository
interface RateLimiterMetricsRepository : ReactiveCrudRepository<RateLimiterMetrics, UUID> {

    /**
     * Find all metrics for a specific rate limiter
     */
    fun findByRateLimiterName(rateLimiterName: String): Flux<RateLimiterMetrics>

    /**
     * Find metrics for a rate limiter within a time range
     */
    @Query(
        """
        SELECT * FROM rate_limiter_metrics
        WHERE rate_limiter_name = :rateLimiterName
        AND window_start >= :start
        AND window_end <= :end
        ORDER BY window_start DESC
        """
    )
    fun findByRateLimiterNameAndTimeRange(
        rateLimiterName: String,
        start: OffsetDateTime,
        end: OffsetDateTime
    ): Flux<RateLimiterMetrics>

    /**
     * Find recent metrics for a rate limiter ordered by window start descending
     */
    @Query(
        """
        SELECT * FROM rate_limiter_metrics
        WHERE rate_limiter_name = :rateLimiterName
        ORDER BY window_start DESC
        LIMIT :limit
        """
    )
    fun findRecentByRateLimiterName(rateLimiterName: String, limit: Int): Flux<RateLimiterMetrics>

    /**
     * Find all metrics within a time range
     */
    @Query(
        """
        SELECT * FROM rate_limiter_metrics
        WHERE window_start >= :start
        AND window_end <= :end
        ORDER BY window_start DESC
        """
    )
    fun findByTimeRange(start: OffsetDateTime, end: OffsetDateTime): Flux<RateLimiterMetrics>

    /**
     * Find metrics with rejection rate above threshold
     */
    @Query(
        """
        SELECT * FROM rate_limiter_metrics
        WHERE rejected_calls > 0
        AND (CAST(rejected_calls AS FLOAT) / (permitted_calls + rejected_calls)) > :threshold
        ORDER BY window_start DESC
        """
    )
    fun findWithRejectionRateAbove(threshold: Double): Flux<RateLimiterMetrics>
}
