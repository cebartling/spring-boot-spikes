package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a resilience event (circuit breaker, rate limiter, retry, etc.)
 */
@Table("resilience_events")
data class ResilienceEvent(
    @Id
    val id: UUID? = null,

    @Column("event_type")
    val eventType: String,

    @Column("event_name")
    val eventName: String,

    @Column("status")
    val status: String,

    @Column("error_message")
    val errorMessage: String? = null,

    @Column("metadata")
    val metadata: String? = null, // JSON stored as String, can be parsed with ObjectMapper

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
