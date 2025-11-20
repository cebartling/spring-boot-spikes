package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.InventoryLocation
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Reactive repository for InventoryLocation entities
 */
@Repository
interface InventoryLocationRepository : ReactiveCrudRepository<InventoryLocation, UUID> {

    /**
     * Find a location by its code
     */
    fun findByCode(code: String): Mono<InventoryLocation>

    /**
     * Find all locations by type
     */
    fun findByType(type: String): Flux<InventoryLocation>

    /**
     * Find all active locations
     */
    fun findByIsActive(isActive: Boolean): Flux<InventoryLocation>

    /**
     * Find active locations by type
     */
    fun findByTypeAndIsActive(type: String, isActive: Boolean): Flux<InventoryLocation>

    /**
     * Search locations by name (case-insensitive partial match)
     */
    @Query("SELECT * FROM inventory_locations WHERE LOWER(name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    fun searchByName(searchTerm: String): Flux<InventoryLocation>

    /**
     * Find locations by city
     */
    fun findByCity(city: String): Flux<InventoryLocation>

    /**
     * Find locations by state
     */
    fun findByState(state: String): Flux<InventoryLocation>

    /**
     * Find locations by country
     */
    fun findByCountry(country: String): Flux<InventoryLocation>

    /**
     * Count locations by type
     */
    fun countByType(type: String): Mono<Long>

    /**
     * Count active locations
     */
    fun countByIsActive(isActive: Boolean): Mono<Long>
}
