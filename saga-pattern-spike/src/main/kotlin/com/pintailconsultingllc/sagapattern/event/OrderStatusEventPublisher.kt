package com.pintailconsultingllc.sagapattern.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Publisher for order status events using reactive streams.
 *
 * Maintains per-order sinks for targeted event delivery to SSE subscribers.
 * Events are multicast to all subscribers of a specific order.
 */
@Component
class OrderStatusEventPublisher {
    private val logger = LoggerFactory.getLogger(OrderStatusEventPublisher::class.java)

    /**
     * Per-order sinks for event distribution.
     * Uses multicast with replay of the last event for late subscribers.
     */
    private val orderSinks = ConcurrentHashMap<UUID, Sinks.Many<OrderStatusEvent>>()

    /**
     * Publish a status event for an order.
     *
     * @param event The event to publish
     */
    fun publish(event: OrderStatusEvent) {
        logger.debug("Publishing status event for order {}: {}", event.orderId, event.eventType)

        val sink = orderSinks.computeIfAbsent(event.orderId) {
            createSink()
        }

        val result = sink.tryEmitNext(event)
        if (result.isFailure) {
            logger.warn("Failed to emit event for order {}: {}", event.orderId, result)
        }

        // Clean up completed sagas
        if (event.eventType == StatusEventType.SAGA_COMPLETED ||
            event.eventType == StatusEventType.SAGA_FAILED
        ) {
            // Allow a brief delay for final event delivery before completing the stream
            // This ensures slow subscribers or those with network latency receive the final event
            Mono.delay(Duration.ofMillis(100))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe {
                    val completeResult = sink.tryEmitComplete()
                    if (completeResult.isFailure) {
                        logger.warn("Failed to complete sink for order {}: {}", event.orderId, completeResult)
                    }
                }
        }
    }

    /**
     * Subscribe to status events for a specific order.
     *
     * @param orderId The order to subscribe to
     * @return Flux of status events for the order
     */
    fun subscribe(orderId: UUID): Flux<OrderStatusEvent> {
        logger.debug("New subscriber for order {}", orderId)

        val sink = orderSinks.computeIfAbsent(orderId) {
            createSink()
        }

        return sink.asFlux()
            .doOnCancel {
                logger.debug("Subscriber cancelled for order {}", orderId)
                cleanupIfNoSubscribers(orderId)
            }
            .doOnTerminate {
                logger.debug("Subscriber terminated for order {}", orderId)
                cleanupIfNoSubscribers(orderId)
            }
    }

    /**
     * Check if there are active subscribers for an order.
     *
     * @param orderId The order to check
     * @return true if there are active subscribers
     */
    fun hasSubscribers(orderId: UUID): Boolean {
        val sink = orderSinks[orderId] ?: return false
        return sink.currentSubscriberCount() > 0
    }

    /**
     * Get the number of active subscribers for an order.
     *
     * @param orderId The order to check
     * @return Number of subscribers
     */
    fun subscriberCount(orderId: UUID): Int {
        return orderSinks[orderId]?.currentSubscriberCount() ?: 0
    }

    /**
     * Clean up the sink for an order if no subscribers remain.
     */
    private fun cleanupIfNoSubscribers(orderId: UUID) {
        val sink = orderSinks[orderId]
        if (sink != null && sink.currentSubscriberCount() == 0) {
            // Atomically remove only if the sink is still present and has zero subscribers
            val removed = orderSinks.remove(orderId, sink)
            if (removed) {
                logger.debug("Cleaned up sink for order {} (no subscribers)", orderId)
            }
        }
    }

    /**
     * Create a new sink for order events.
     *
     * Uses multicast with replay of the last event so late subscribers
     * can get the most recent status.
     */
    private fun createSink(): Sinks.Many<OrderStatusEvent> {
        return Sinks.many().replay().limit(1)
    }
}
