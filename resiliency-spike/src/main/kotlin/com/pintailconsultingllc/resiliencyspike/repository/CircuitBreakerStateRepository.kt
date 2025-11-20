package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CircuitBreakerState
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Reactive repository for CircuitBreakerState entities
 */
@Repository
interface CircuitBreakerStateRepository : ReactiveCrudRepository<CircuitBreakerState, UUID> {

    /**
     * Find circuit breaker state by name
     */
    fun findByCircuitBreakerName(circuitBreakerName: String): Mono<CircuitBreakerState>

    /**
     * Find all circuit breakers by state (CLOSED, OPEN, HALF_OPEN)
     */
    fun findByState(state: String): Flux<CircuitBreakerState>

    /**
     * Find all circuit breakers ordered by failure count descending
     */
    @Query("SELECT * FROM circuit_breaker_state ORDER BY failure_count DESC")
    fun findAllOrderByFailureCountDesc(): Flux<CircuitBreakerState>

    /**
     * Find circuit breakers with failures above a threshold
     */
    @Query("SELECT * FROM circuit_breaker_state WHERE failure_count > :threshold")
    fun findWithFailureCountAbove(threshold: Int): Flux<CircuitBreakerState>

    /**
     * Check if a circuit breaker exists by name
     */
    fun existsByCircuitBreakerName(circuitBreakerName: String): Mono<Boolean>
}
