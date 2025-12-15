package com.pintailconsultingllc.sagapattern.api.dto

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.history.OrderEvent
import com.pintailconsultingllc.sagapattern.history.OrderEventType
import com.pintailconsultingllc.sagapattern.history.OrderHistory
import com.pintailconsultingllc.sagapattern.history.OrderTimeline
import com.pintailconsultingllc.sagapattern.history.SagaExecutionSummary
import com.pintailconsultingllc.sagapattern.history.SagaOutcome
import com.pintailconsultingllc.sagapattern.history.TimelineEntry
import com.pintailconsultingllc.sagapattern.history.TimelineStatus
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for a timeline entry.
 */
data class TimelineEntryResponse(
    val timestamp: Instant,
    val title: String,
    val description: String,
    val status: String,
    val stepName: String?,
    val details: Map<String, Any>?,
    val error: ErrorInfoResponse?
) {
    companion object {
        fun from(entry: TimelineEntry): TimelineEntryResponse = TimelineEntryResponse(
            timestamp = entry.timestamp,
            title = entry.title,
            description = entry.description,
            status = entry.status.name,
            stepName = entry.stepName,
            details = entry.details,
            error = entry.error?.let { ErrorInfoResponse.from(it) }
        )
    }
}

/**
 * Response DTO for error information.
 */
data class ErrorInfoResponse(
    val code: String,
    val message: String,
    val suggestedAction: String?
) {
    companion object {
        fun from(errorInfo: ErrorInfo): ErrorInfoResponse = ErrorInfoResponse(
            code = errorInfo.code,
            message = errorInfo.message,
            suggestedAction = errorInfo.suggestedAction
        )
    }
}

/**
 * Response DTO for order timeline.
 */
data class OrderTimelineResponse(
    val orderId: UUID,
    val orderNumber: String,
    val createdAt: Instant,
    val currentStatus: String,
    val timeline: List<TimelineEntryResponse>,
    val executionCount: Int
) {
    companion object {
        fun from(timeline: OrderTimeline): OrderTimelineResponse = OrderTimelineResponse(
            orderId = timeline.orderId,
            orderNumber = timeline.orderNumber,
            createdAt = timeline.createdAt,
            currentStatus = timeline.currentStatus.name,
            timeline = timeline.entries.map { TimelineEntryResponse.from(it) },
            executionCount = timeline.executionCount
        )
    }
}

/**
 * Response DTO for saga execution summary.
 */
data class SagaExecutionSummaryResponse(
    val executionId: UUID,
    val attemptNumber: Int,
    val startedAt: Instant,
    val completedAt: Instant?,
    val outcome: String,
    val failedStep: String?,
    val stepsCompleted: Int,
    val isRetry: Boolean,
    val durationMillis: Long?,
    val traceId: String?
) {
    companion object {
        fun from(summary: SagaExecutionSummary): SagaExecutionSummaryResponse =
            SagaExecutionSummaryResponse(
                executionId = summary.executionId,
                attemptNumber = summary.attemptNumber,
                startedAt = summary.startedAt,
                completedAt = summary.completedAt,
                outcome = summary.outcome.name,
                failedStep = summary.failedStep,
                stepsCompleted = summary.stepsCompleted,
                isRetry = summary.isRetry,
                durationMillis = summary.durationMillis,
                traceId = summary.traceId
            )
    }
}

/**
 * Response DTO for complete order history.
 */
data class OrderHistoryResponse(
    val orderId: UUID,
    val orderNumber: String,
    val createdAt: Instant,
    val completedAt: Instant?,
    val finalStatus: String,
    val timeline: List<TimelineEntryResponse>,
    val executions: List<SagaExecutionSummaryResponse>,
    val totalAttempts: Int,
    val retryCount: Int,
    val wasSuccessful: Boolean,
    val hadCompensations: Boolean,
    val traceId: String?,
    val executionCount: Int
) {
    companion object {
        fun from(history: OrderHistory): OrderHistoryResponse = OrderHistoryResponse(
            orderId = history.orderId,
            orderNumber = history.orderNumber,
            createdAt = history.createdAt,
            completedAt = history.completedAt,
            finalStatus = history.finalStatus.name,
            timeline = history.timeline.entries.map { TimelineEntryResponse.from(it) },
            executions = history.executions.map { SagaExecutionSummaryResponse.from(it) },
            totalAttempts = history.totalAttempts,
            retryCount = history.retryCount,
            wasSuccessful = history.wasSuccessful,
            hadCompensations = history.hadCompensations,
            // Use the trace ID from the most recent execution
            traceId = history.executions.lastOrNull()?.traceId,
            executionCount = history.timeline.executionCount
        )
    }
}

/**
 * Response DTO for raw order events.
 */
data class OrderEventResponse(
    val id: UUID,
    val orderId: UUID,
    val sagaExecutionId: UUID?,
    val eventType: String,
    val timestamp: Instant,
    val stepName: String?,
    val outcome: String?,
    val details: Map<String, Any>?
) {
    companion object {
        fun from(event: OrderEvent, details: Map<String, Any>? = null): OrderEventResponse =
            OrderEventResponse(
                id = event.id,
                orderId = event.orderId,
                sagaExecutionId = event.sagaExecutionId,
                eventType = event.eventType.name,
                timestamp = event.timestamp,
                stepName = event.stepName,
                outcome = event.outcome?.name,
                details = details
            )
    }
}

/**
 * Response DTO for list of order events.
 */
data class OrderEventsResponse(
    val orderId: UUID,
    val events: List<OrderEventResponse>
) {
    companion object {
        fun from(orderId: UUID, events: List<OrderEvent>): OrderEventsResponse =
            OrderEventsResponse(
                orderId = orderId,
                events = events.map { OrderEventResponse.from(it) }
            )
    }
}
