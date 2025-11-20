package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing circuit breaker state and metrics
 */
@Table("circuit_breaker_state")
data class CircuitBreakerState(
    @Id
    val id: UUID? = null,

    @Column("circuit_breaker_name")
    val circuitBreakerName: String,

    @Column("state")
    val state: String, // CLOSED, OPEN, HALF_OPEN

    @Column("failure_count")
    val failureCount: Int = 0,

    @Column("success_count")
    val successCount: Int = 0,

    @Column("last_failure_time")
    val lastFailureTime: OffsetDateTime? = null,

    @Column("last_success_time")
    val lastSuccessTime: OffsetDateTime? = null,

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
