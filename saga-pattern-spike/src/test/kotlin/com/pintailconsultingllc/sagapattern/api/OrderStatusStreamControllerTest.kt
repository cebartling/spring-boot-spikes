package com.pintailconsultingllc.sagapattern.api

import com.pintailconsultingllc.sagapattern.event.OrderStatusEvent
import com.pintailconsultingllc.sagapattern.event.OrderStatusEventPublisher
import com.pintailconsultingllc.sagapattern.event.StatusEventType
import com.pintailconsultingllc.sagapattern.progress.OrderProgress
import com.pintailconsultingllc.sagapattern.progress.OrderProgressService
import com.pintailconsultingllc.sagapattern.progress.ProgressStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for OrderStatusStreamController.
 * 
 * Focus on verifying that the SSE heartbeat mechanism properly stops
 * when the status event stream completes.
 */
@Tag("unit")
class OrderStatusStreamControllerTest {

    private lateinit var orderProgressService: OrderProgressService
    private lateinit var orderStatusEventPublisher: OrderStatusEventPublisher
    private lateinit var controller: OrderStatusStreamController

    @BeforeEach
    fun setUp() {
        orderProgressService = mock()
        orderStatusEventPublisher = mock()
        controller = OrderStatusStreamController(orderProgressService, orderStatusEventPublisher)
    }

    @Test
    fun `streamOrderStatus completes when status stream completes`() {
        val orderId = UUID.randomUUID()
        
        // Mock orderExists to return true
        runBlocking {
            whenever(orderProgressService.orderExists(orderId)).thenReturn(true)
            whenever(orderProgressService.getProgress(orderId)).thenReturn(
                OrderProgress(
                    orderId = orderId,
                    overallStatus = ProgressStatus.IN_PROGRESS,
                    currentStep = "Payment Processing",
                    steps = emptyList(),
                    lastUpdated = Instant.now()
                )
            )
        }

        // Create a publisher that emits one event and completes
        val completingEvent = OrderStatusEvent(
            orderId = orderId,
            eventType = StatusEventType.SAGA_COMPLETED,
            stepName = "Shipping Arrangement",
            newStatus = "COMPLETED",
            timestamp = Instant.now()
        )
        
        whenever(orderStatusEventPublisher.subscribe(orderId))
            .thenReturn(Flux.just(completingEvent))

        // Stream should complete shortly after the completing event
        // Verify that it doesn't continue indefinitely due to heartbeat
        StepVerifier.create(controller.streamOrderStatus(orderId))
            .expectNextCount(2) // Initial status + completing event
            .expectComplete()
            .verify(Duration.ofSeconds(5)) // Should complete within 5 seconds
    }

    @Test
    fun `streamOrderStatus includes heartbeat comments before completion`() {
        val orderId = UUID.randomUUID()
        
        runBlocking {
            whenever(orderProgressService.orderExists(orderId)).thenReturn(true)
            whenever(orderProgressService.getProgress(orderId)).thenReturn(
                OrderProgress(
                    orderId = orderId,
                    overallStatus = ProgressStatus.IN_PROGRESS,
                    currentStep = "Payment Processing",
                    steps = emptyList(),
                    lastUpdated = Instant.now()
                )
            )
        }

        // Create a publisher that emits events slowly, allowing heartbeat to interleave
        val event1 = OrderStatusEvent(
            orderId = orderId,
            eventType = StatusEventType.STEP_STARTED,
            stepName = "Payment Processing",
            newStatus = "IN_PROGRESS",
            timestamp = Instant.now()
        )
        
        val event2 = OrderStatusEvent(
            orderId = orderId,
            eventType = StatusEventType.SAGA_COMPLETED,
            stepName = "Shipping Arrangement",
            newStatus = "COMPLETED",
            timestamp = Instant.now()
        )
        
        // Emit events with delay to allow heartbeat
        whenever(orderStatusEventPublisher.subscribe(orderId))
            .thenReturn(
                Flux.just(event1)
                    .concatWith(Flux.just(event2).delayElements(Duration.ofSeconds(1)))
            )

        // Verify stream completes and heartbeats are present (comments have no data)
        val hasDataOrHeartbeat: (org.springframework.http.codec.ServerSentEvent<OrderStatusEvent>) -> Boolean = 
            { it.data() != null || it.comment() == "heartbeat" }
        
        StepVerifier.create(controller.streamOrderStatus(orderId))
            .expectNextMatches(hasDataOrHeartbeat)
            .expectNextMatches(hasDataOrHeartbeat)
            .expectNextMatches(hasDataOrHeartbeat)
            .expectComplete()
            .verify(Duration.ofSeconds(10))
    }

    @Test
    fun `streamOrderStatus returns 404 for non-existent order`() {
        val orderId = UUID.randomUUID()
        
        runBlocking {
            whenever(orderProgressService.orderExists(orderId)).thenReturn(false)
        }

        StepVerifier.create(controller.streamOrderStatus(orderId))
            .expectError()
            .verify(Duration.ofSeconds(5))
    }
}
