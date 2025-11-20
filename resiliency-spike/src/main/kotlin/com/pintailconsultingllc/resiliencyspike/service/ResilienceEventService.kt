package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.ResilienceEvent
import com.pintailconsultingllc.resiliencyspike.repository.ResilienceEventRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service for managing resilience events
 * Demonstrates reactive database operations with R2DBC
 */
@Service
class ResilienceEventService(
    private val resilienceEventRepository: ResilienceEventRepository
) {

    /**
     * Save a new resilience event
     */
    fun saveEvent(event: ResilienceEvent): Mono<ResilienceEvent> {
        return resilienceEventRepository.save(event)
    }

    /**
     * Find all events by type
     */
    fun findEventsByType(eventType: String): Flux<ResilienceEvent> {
        return resilienceEventRepository.findByEventType(eventType)
    }

    /**
     * Find recent events
     */
    fun findRecentEvents(limit: Int = 50): Flux<ResilienceEvent> {
        return resilienceEventRepository.findRecentEvents(limit)
    }

    /**
     * Find event by ID
     */
    fun findEventById(id: UUID): Mono<ResilienceEvent> {
        return resilienceEventRepository.findById(id)
    }

    /**
     * Find events by status
     */
    fun findEventsByStatus(status: String): Flux<ResilienceEvent> {
        return resilienceEventRepository.findByStatus(status)
    }

    /**
     * Find events by type and status
     */
    fun findEventsByTypeAndStatus(eventType: String, status: String): Flux<ResilienceEvent> {
        return resilienceEventRepository.findByEventTypeAndStatus(eventType, status)
    }

    /**
     * Delete all events (use with caution!)
     */
    fun deleteAllEvents(): Mono<Void> {
        return resilienceEventRepository.deleteAll()
    }
}
