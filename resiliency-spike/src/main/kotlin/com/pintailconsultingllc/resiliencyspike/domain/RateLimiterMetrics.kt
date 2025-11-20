package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing rate limiter metrics for a specific time window
 */
@Table("rate_limiter_metrics")
data class RateLimiterMetrics(
    @Id
    val id: UUID? = null,

    @Column("rate_limiter_name")
    val rateLimiterName: String,

    @Column("permitted_calls")
    val permittedCalls: Int = 0,

    @Column("rejected_calls")
    val rejectedCalls: Int = 0,

    @Column("window_start")
    val windowStart: OffsetDateTime,

    @Column("window_end")
    val windowEnd: OffsetDateTime,

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
