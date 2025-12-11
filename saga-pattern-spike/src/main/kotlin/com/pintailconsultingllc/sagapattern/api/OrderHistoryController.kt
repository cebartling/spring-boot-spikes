package com.pintailconsultingllc.sagapattern.api

import com.pintailconsultingllc.sagapattern.api.dto.OrderEventsResponse
import com.pintailconsultingllc.sagapattern.api.dto.OrderHistoryResponse
import com.pintailconsultingllc.sagapattern.api.dto.OrderTimelineResponse
import com.pintailconsultingllc.sagapattern.api.dto.SagaExecutionSummaryResponse
import com.pintailconsultingllc.sagapattern.history.OrderHistoryService
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * REST controller for order history and timeline endpoints.
 */
@RestController
@RequestMapping("/api/orders")
class OrderHistoryController(
    private val orderHistoryService: OrderHistoryService
) {
    private val logger = LoggerFactory.getLogger(OrderHistoryController::class.java)

    /**
     * Get complete order history including all executions and timeline.
     *
     * @param orderId The order ID to get history for
     * @return Order history with timeline and execution summaries
     */
    @GetMapping("/{orderId}/history")
    @Observed(name = "http.order.history", contextualName = "get-order-history")
    fun getOrderHistory(@PathVariable orderId: UUID): Mono<ResponseEntity<OrderHistoryResponse>> {
        logger.info("Retrieving history for order: {}", orderId)

        return mono {
            val history = orderHistoryService.getOrderHistory(orderId)
            if (history != null) {
                ResponseEntity.ok(OrderHistoryResponse.from(history))
            } else {
                ResponseEntity.notFound().build()
            }
        }
    }

    /**
     * Get order timeline with human-readable entries.
     *
     * This is a simplified view of the order history focused on
     * customer-facing timeline entries.
     *
     * @param orderId The order ID to get timeline for
     * @return Order timeline with human-readable entries
     */
    @GetMapping("/{orderId}/timeline")
    @Observed(name = "http.order.timeline", contextualName = "get-order-timeline")
    fun getOrderTimeline(@PathVariable orderId: UUID): Mono<ResponseEntity<OrderTimelineResponse>> {
        logger.info("Retrieving timeline for order: {}", orderId)

        return mono {
            val timeline = orderHistoryService.getOrderTimeline(orderId)
            if (timeline != null) {
                ResponseEntity.ok(OrderTimelineResponse.from(timeline))
            } else {
                ResponseEntity.notFound().build()
            }
        }
    }

    /**
     * Get raw order events for technical debugging.
     *
     * Returns the raw event log which can be used for debugging
     * or detailed technical analysis.
     *
     * @param orderId The order ID to get events for
     * @return List of raw order events
     */
    @GetMapping("/{orderId}/events")
    @Observed(name = "http.order.events", contextualName = "get-order-events")
    fun getOrderEvents(@PathVariable orderId: UUID): Mono<ResponseEntity<OrderEventsResponse>> {
        logger.info("Retrieving events for order: {}", orderId)

        return mono {
            val events = orderHistoryService.getOrderEvents(orderId)
            if (events.isEmpty()) {
                // Check if order exists by trying to get timeline
                val timeline = orderHistoryService.getOrderTimeline(orderId)
                if (timeline == null) {
                    ResponseEntity.notFound().build()
                } else {
                    ResponseEntity.ok(OrderEventsResponse.from(orderId, events))
                }
            } else {
                ResponseEntity.ok(OrderEventsResponse.from(orderId, events))
            }
        }
    }

    /**
     * Get execution summaries for an order.
     *
     * Shows all saga execution attempts including the original
     * and any retries.
     *
     * @param orderId The order ID to get executions for
     * @return List of saga execution summaries
     */
    @GetMapping("/{orderId}/executions")
    @Observed(name = "http.order.executions", contextualName = "get-order-executions")
    fun getOrderExecutions(
        @PathVariable orderId: UUID
    ): Mono<ResponseEntity<List<SagaExecutionSummaryResponse>>> {
        logger.info("Retrieving executions for order: {}", orderId)

        return mono {
            val executions = orderHistoryService.getExecutionSummaries(orderId)
            ResponseEntity.ok(executions.map { SagaExecutionSummaryResponse.from(it) })
        }
    }
}
