package com.pintailconsultingllc.sagapattern.event

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Publisher for domain events.
 *
 * Wraps Spring's ApplicationEventPublisher to provide a domain-specific interface
 * for publishing saga-related events. Events can be consumed by:
 * - Internal listeners for logging, metrics, or side effects
 * - External systems via webhooks or message queues (future extension)
 */
@Component
class DomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(DomainEventPublisher::class.java)

    /**
     * Publish a saga event.
     *
     * @param event The event to publish
     */
    fun publish(event: SagaEvent) {
        logger.info("Publishing event: {} for order {}", event::class.simpleName, event.orderId)
        applicationEventPublisher.publishEvent(event)
    }

    /**
     * Publish an order saga completed event.
     */
    fun publishSagaCompleted(event: OrderSagaCompleted) {
        publish(event)
    }

    /**
     * Publish an order saga failed event.
     */
    fun publishSagaFailed(event: OrderSagaFailed) {
        publish(event)
    }

    /**
     * Publish a compensation started event.
     */
    fun publishCompensationStarted(event: SagaCompensationStarted) {
        publish(event)
    }

    /**
     * Publish a compensation completed event.
     */
    fun publishCompensationCompleted(event: SagaCompensationCompleted) {
        publish(event)
    }
}
