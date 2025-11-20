package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.ResilienceEvent
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.*

/**
 * Reactive repository for ResilienceEvent entities
 */
@Repository
interface ResilienceEventRepository : ReactiveCrudRepository<ResilienceEvent, UUID> {

    /**
     * Find all events by event type
     */
    fun findByEventType(eventType: String): Flux<ResilienceEvent>

    /**
     * Find all events by event name
     */
    fun findByEventName(eventName: String): Flux<ResilienceEvent>

    /**
     * Find all events by status
     */
    fun findByStatus(status: String): Flux<ResilienceEvent>

    /**
     * Find events by event type and status
     */
    fun findByEventTypeAndStatus(eventType: String, status: String): Flux<ResilienceEvent>

    /**
     * Find recent events ordered by created_at descending
     */
    @Query("SELECT * FROM resilience_events ORDER BY created_at DESC LIMIT :limit")
    fun findRecentEvents(limit: Int): Flux<ResilienceEvent>

    /**
     * Find events by event type ordered by created_at descending
     */
    @Query("SELECT * FROM resilience_events WHERE event_type = :eventType ORDER BY created_at DESC")
    fun findByEventTypeOrderByCreatedAtDesc(eventType: String): Flux<ResilienceEvent>
}
