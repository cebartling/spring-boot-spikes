package com.pintailconsultingllc.sagapattern.event

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for OrderStatusEventPublisher.
 */
class OrderStatusEventPublisherTest {

    private lateinit var publisher: OrderStatusEventPublisher

    @BeforeEach
    fun setUp() {
        publisher = OrderStatusEventPublisher()
    }

    @Test
    fun `publish delivers event to subscriber`() {
        val orderId = UUID.randomUUID()
        val event = createEvent(orderId, StatusEventType.SAGA_STARTED)

        // Subscribe first
        val flux = publisher.subscribe(orderId)

        // Then publish
        publisher.publish(event)

        StepVerifier.create(flux.take(1))
            .expectNext(event)
            .verifyComplete()
    }

    @Test
    fun `subscribe receives events published after subscription`() {
        val orderId = UUID.randomUUID()
        val event1 = createEvent(orderId, StatusEventType.SAGA_STARTED)
        val event2 = createEvent(orderId, StatusEventType.STEP_STARTED, "Inventory")

        val flux = publisher.subscribe(orderId)

        publisher.publish(event1)
        publisher.publish(event2)

        // With replay(1), late subscription only gets the last event
        // So we verify we can receive at least the replayed event
        StepVerifier.create(flux.take(1))
            .expectNext(event2)
            .verifyComplete()
    }

    @Test
    fun `multiple subscribers receive same events`() {
        val orderId = UUID.randomUUID()
        val event = createEvent(orderId, StatusEventType.STEP_COMPLETED, "Inventory")

        val flux1 = publisher.subscribe(orderId)
        val flux2 = publisher.subscribe(orderId)

        publisher.publish(event)

        StepVerifier.create(flux1.take(1))
            .expectNext(event)
            .verifyComplete()

        StepVerifier.create(flux2.take(1))
            .expectNext(event)
            .verifyComplete()
    }

    @Test
    fun `events are isolated between orders`() {
        val orderId1 = UUID.randomUUID()
        val orderId2 = UUID.randomUUID()
        val event1 = createEvent(orderId1, StatusEventType.SAGA_STARTED)
        val event2 = createEvent(orderId2, StatusEventType.SAGA_STARTED)

        val flux1 = publisher.subscribe(orderId1)
        val flux2 = publisher.subscribe(orderId2)

        publisher.publish(event1)
        publisher.publish(event2)

        StepVerifier.create(flux1.take(1))
            .expectNext(event1)
            .verifyComplete()

        StepVerifier.create(flux2.take(1))
            .expectNext(event2)
            .verifyComplete()
    }

    @Test
    fun `hasSubscribers returns true when subscribed`() {
        val orderId = UUID.randomUUID()

        assertFalse(publisher.hasSubscribers(orderId))

        // Create subscription (need to actually subscribe by calling subscribe)
        val disposable = publisher.subscribe(orderId).subscribe()

        assertTrue(publisher.hasSubscribers(orderId))

        disposable.dispose()
    }

    @Test
    fun `hasSubscribers returns false for unknown order`() {
        val orderId = UUID.randomUUID()

        assertFalse(publisher.hasSubscribers(orderId))
    }

    @Test
    fun `subscriberCount returns correct count`() {
        val orderId = UUID.randomUUID()

        assertEquals(0, publisher.subscriberCount(orderId))

        val disposable1 = publisher.subscribe(orderId).subscribe()
        assertEquals(1, publisher.subscriberCount(orderId))

        val disposable2 = publisher.subscribe(orderId).subscribe()
        assertEquals(2, publisher.subscriberCount(orderId))

        disposable1.dispose()
        disposable2.dispose()
    }

    @Test
    fun `SAGA_COMPLETED event completes the stream`() {
        val orderId = UUID.randomUUID()
        val completeEvent = createEvent(orderId, StatusEventType.SAGA_COMPLETED)

        val flux = publisher.subscribe(orderId)

        publisher.publish(completeEvent)

        StepVerifier.create(flux)
            .expectNext(completeEvent)
            .verifyComplete()
    }

    @Test
    fun `SAGA_FAILED event completes the stream`() {
        val orderId = UUID.randomUUID()
        val failedEvent = createEvent(orderId, StatusEventType.SAGA_FAILED)

        val flux = publisher.subscribe(orderId)

        publisher.publish(failedEvent)

        StepVerifier.create(flux)
            .expectNext(failedEvent)
            .verifyComplete()
    }

    @Test
    fun `late subscriber receives last event via replay`() {
        val orderId = UUID.randomUUID()
        val event = createEvent(orderId, StatusEventType.STEP_STARTED, "Inventory")

        // Publish before subscription
        val earlyFlux = publisher.subscribe(orderId)
        publisher.publish(event)

        // Late subscriber should receive the last event
        val lateFlux = publisher.subscribe(orderId)

        StepVerifier.create(lateFlux.take(1))
            .expectNext(event)
            .verifyComplete()
    }

    private fun createEvent(
        orderId: UUID,
        eventType: StatusEventType,
        stepName: String? = null
    ): OrderStatusEvent = OrderStatusEvent(
        orderId = orderId,
        eventType = eventType,
        stepName = stepName,
        newStatus = eventType.name
    )
}
