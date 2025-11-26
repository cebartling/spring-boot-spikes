package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Interface for Event Store Repository.
 * Full implementation will be provided in AC2.
 */
interface EventStoreRepository {
    /**
     * Saves events to the event store.
     */
    fun saveEvents(events: List<ProductEvent>): Mono<Void>

    /**
     * Finds all events for a given aggregate ID.
     */
    fun findEventsByAggregateId(aggregateId: UUID): Flux<ProductEvent>
}
