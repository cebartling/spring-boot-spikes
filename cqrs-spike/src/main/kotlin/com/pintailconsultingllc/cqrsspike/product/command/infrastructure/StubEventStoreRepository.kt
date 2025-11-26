package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.EventMetadata
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Stub implementation of EventStoreRepository for AC1 testing.
 * Will be replaced with real implementation in AC2.
 *
 * Uses in-memory storage for development/testing purposes only.
 */
@Repository
class StubEventStoreRepository : EventStoreRepository {

    private val logger = LoggerFactory.getLogger(StubEventStoreRepository::class.java)
    private val eventStore = ConcurrentHashMap<UUID, MutableList<ProductEvent>>()

    override fun saveEvents(events: List<ProductEvent>): Mono<Void> {
        return saveEvents(events, null)
    }

    override fun saveEvents(events: List<ProductEvent>, metadata: EventMetadata?): Mono<Void> {
        return Mono.fromRunnable {
            events.forEach { event ->
                eventStore.computeIfAbsent(event.productId) { mutableListOf() }.add(event)
                logger.debug("Saved event: type=${event::class.simpleName}, productId=${event.productId}, version=${event.version}")
            }
            if (metadata != null) {
                logger.debug("Event metadata: correlationId=${metadata.correlationId}, causationId=${metadata.causationId}, userId=${metadata.userId}")
            }
            logger.info("Saved ${events.size} events to stub event store")
        }
    }

    override fun findEventsByAggregateId(aggregateId: UUID): Flux<ProductEvent> {
        return Flux.defer {
            val events = eventStore[aggregateId] ?: emptyList()
            logger.debug("Found ${events.size} events for aggregate $aggregateId")
            Flux.fromIterable(events.sortedBy { it.version })
        }
    }

    /**
     * Clears all events (for testing purposes).
     */
    fun clear() {
        eventStore.clear()
        logger.debug("Cleared stub event store")
    }

    /**
     * Returns total event count (for testing purposes).
     */
    fun eventCount(): Int = eventStore.values.sumOf { it.size }
}
