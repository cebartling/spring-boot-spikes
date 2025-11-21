package com.pintailconsultingllc.resiliencyspike.dto

import com.pintailconsultingllc.resiliencyspike.domain.CartEventType
import java.time.OffsetDateTime

/**
 * DTO for cart state history responses
 */
data class CartStateHistoryResponse(
    val id: Long,
    val cartId: Long,
    val eventType: CartEventType,
    val previousStatus: String?,
    val newStatus: String?,
    val eventData: String?,
    val createdAt: OffsetDateTime
)

/**
 * Response for conversion rate analytics
 */
data class ConversionRateResponse(
    val startDate: OffsetDateTime,
    val endDate: OffsetDateTime,
    val conversionRate: Double,
    val totalCreated: Long,
    val totalConverted: Long
)

/**
 * Response for abandonment rate analytics
 */
data class AbandonmentRateResponse(
    val startDate: OffsetDateTime,
    val endDate: OffsetDateTime,
    val abandonmentRate: Double,
    val totalCreated: Long,
    val totalAbandoned: Long
)

/**
 * Response for cart activity summary
 */
data class CartActivitySummaryResponse(
    val cartId: Long,
    val eventCounts: Map<CartEventType, Long>,
    val totalEvents: Long
)
