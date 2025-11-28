package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.DomainEventEntity
import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.DomainEventR2dbcRepository
import com.pintailconsultingllc.cqrsspike.infrastructure.eventstore.EventDeserializer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Service for querying events from the event store for projection processing.
 *
 * Provides ordered event retrieval with support for incremental processing
 * and full replay scenarios.
 */
@Service
class EventQueryService(
    private val domainEventRepository: DomainEventR2dbcRepository,
    private val eventDeserializer: EventDeserializer
) {
    private val logger = LoggerFactory.getLogger(EventQueryService::class.java)

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
    }

    /**
     * Find events after a specific event ID for incremental processing.
     *
     * @param afterEventId The event ID to start after (exclusive)
     * @param limit Maximum number of events to return
     * @return Flux of stored events in order
     */
    fun findEventsAfterEventId(
        afterEventId: UUID?,
        limit: Int = DEFAULT_BATCH_SIZE
    ): Flux<StoredEvent> {
        return if (afterEventId == null) {
            findAllEventsOrdered(limit, 0)
        } else {
            domainEventRepository.findEventsAfterEventId(afterEventId, limit)
                .map { entity -> toStoredEvent(entity) }
                .doOnSubscribe {
                    logger.debug("Querying events after eventId={}, limit={}", afterEventId, limit)
                }
        }
    }

    /**
     * Find all events ordered by occurrence for full rebuild.
     *
     * @param limit Maximum events per batch
     * @param offset Starting offset
     * @return Flux of stored events
     */
    fun findAllEventsOrdered(
        limit: Int = DEFAULT_BATCH_SIZE,
        offset: Long = 0
    ): Flux<StoredEvent> {
        return domainEventRepository.findAllEventsOrdered(limit, offset)
            .map { entity -> toStoredEvent(entity) }
            .doOnSubscribe {
                logger.debug("Querying all events: limit={}, offset={}", limit, offset)
            }
    }

    /**
     * Get the latest event in the store.
     */
    fun getLatestEvent(): Mono<StoredEvent> {
        return domainEventRepository.findLatestEvent()
            .map { entity -> toStoredEvent(entity) }
    }

    /**
     * Count events after a specific event ID for lag calculation.
     */
    fun countEventsAfterEventId(afterEventId: UUID?): Mono<Long> {
        return if (afterEventId == null) {
            domainEventRepository.countAllEvents()
        } else {
            domainEventRepository.countEventsAfterEventId(afterEventId)
        }
    }

    /**
     * Count total events in the event store.
     */
    fun countAllEvents(): Mono<Long> {
        return domainEventRepository.countAllEvents()
    }

    private fun toStoredEvent(entity: DomainEventEntity): StoredEvent {
        val event = eventDeserializer.deserialize(
            eventType = entity.eventType,
            eventVersion = entity.eventVersion,
            json = entity.eventData
        )

        return StoredEvent(
            eventId = entity.eventId,
            streamId = entity.streamId,
            eventType = entity.eventType,
            aggregateVersion = entity.aggregateVersion.toLong(),
            globalSequence = 0, // Position tracking uses eventId instead
            occurredAt = entity.occurredAt,
            event = event
        )
    }
}
