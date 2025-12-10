package com.pintailconsultingllc.sagapattern.api

import com.pintailconsultingllc.sagapattern.event.OrderStatusEvent
import com.pintailconsultingllc.sagapattern.event.OrderStatusEventPublisher
import com.pintailconsultingllc.sagapattern.event.StatusEventType
import com.pintailconsultingllc.sagapattern.progress.OrderProgressService
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

/**
 * REST controller for streaming order status updates via Server-Sent Events (SSE).
 *
 * Provides real-time visibility into order processing progress without polling.
 */
@RestController
@RequestMapping("/api/orders")
class OrderStatusStreamController(
    private val orderProgressService: OrderProgressService,
    private val orderStatusEventPublisher: OrderStatusEventPublisher
) {
    private val logger = LoggerFactory.getLogger(OrderStatusStreamController::class.java)

    /**
     * Stream order status updates via Server-Sent Events.
     *
     * Sends an initial status event followed by real-time updates as the
     * saga progresses. The stream completes when the saga finishes
     * (either successfully or with compensation).
     *
     * @param orderId The order ID to stream status for
     * @return Flux of SSE events containing order status updates
     */
    @GetMapping("/{orderId}/status/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Observed(name = "http.order.status.stream", contextualName = "stream-order-status")
    fun streamOrderStatus(@PathVariable orderId: UUID): Flux<ServerSentEvent<OrderStatusEvent>> {
        logger.info("Starting SSE stream for order: {}", orderId)

        // Verify order exists first
        return mono { orderProgressService.orderExists(orderId) }
            .flatMapMany { exists ->
                if (!exists) {
                    Flux.error(ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: $orderId"))
                } else {
                    createStatusStream(orderId)
                }
            }
    }

    /**
     * Create the status event stream for an order.
     *
     * Combines:
     * 1. Initial status event from current progress
     * 2. Real-time updates from the event publisher
     * 3. Periodic heartbeat to keep connection alive
     *
     * The heartbeat automatically stops when the status stream completes.
     */
    private fun createStatusStream(orderId: UUID): Flux<ServerSentEvent<OrderStatusEvent>> {
        // Get initial status
        val initialStatus = mono { orderProgressService.getProgress(orderId) }
            .mapNotNull { progress ->
                progress?.let {
                    OrderStatusEvent(
                        orderId = orderId,
                        eventType = mapProgressToEventType(it.overallStatus.name),
                        stepName = it.currentStep,
                        newStatus = it.overallStatus.name
                    )
                }
            }
            .flux()

        // Subscribe to real-time updates
        val updates = orderStatusEventPublisher.subscribe(orderId)

        // Combine initial status with updates, wrap in SSE
        val statusEvents = Flux.concat(initialStatus, updates)
            .map { event ->
                ServerSentEvent.builder(event)
                    .id(event.timestamp.toEpochMilli().toString())
                    .event(event.eventType.name.lowercase())
                    .build()
            }
            .doOnNext { event ->
                logger.debug("Sending SSE event for order {}: {}", orderId, event.event())
            }
            .doOnComplete {
                logger.info("SSE stream completed for order {}", orderId)
            }
            .doOnCancel {
                logger.info("SSE stream cancelled for order {}", orderId)
            }

        // Heartbeat to keep connection alive (every 30 seconds)
        // takeUntilOther ensures heartbeat stops when statusEvents completes
        val heartbeat = Flux.interval(Duration.ofSeconds(30))
            .map {
                ServerSentEvent.builder<OrderStatusEvent>()
                    .comment("heartbeat")
                    .build()
            }
            .takeUntilOther(statusEvents.ignoreElements())

        // Merge status events with heartbeat
        return Flux.merge(statusEvents, heartbeat)
    }

    /**
     * Map progress status name to appropriate event type.
     */
    private fun mapProgressToEventType(statusName: String): StatusEventType {
        return when (statusName) {
            "QUEUED" -> StatusEventType.SAGA_STARTED
            "IN_PROGRESS" -> StatusEventType.STEP_STARTED
            "COMPLETED" -> StatusEventType.SAGA_COMPLETED
            "FAILED" -> StatusEventType.SAGA_FAILED
            "ROLLING_BACK" -> StatusEventType.COMPENSATION_STARTED
            "ROLLED_BACK" -> StatusEventType.SAGA_FAILED
            else -> StatusEventType.STEP_STARTED
        }
    }
}
