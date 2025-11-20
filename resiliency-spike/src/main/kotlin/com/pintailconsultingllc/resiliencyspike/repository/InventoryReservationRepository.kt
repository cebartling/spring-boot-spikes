package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.InventoryReservation
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.*

/**
 * Reactive repository for InventoryReservation entities
 */
@Repository
interface InventoryReservationRepository : ReactiveCrudRepository<InventoryReservation, UUID> {

    /**
     * Find reservations by product ID
     */
    fun findByProductId(productId: UUID): Flux<InventoryReservation>

    /**
     * Find reservations by location ID
     */
    fun findByLocationId(locationId: UUID): Flux<InventoryReservation>

    /**
     * Find reservations by status
     */
    fun findByStatus(status: String): Flux<InventoryReservation>

    /**
     * Find reservations by reference number
     */
    fun findByReferenceNumber(referenceNumber: String): Flux<InventoryReservation>

    /**
     * Find active reservations for a product and location
     */
    @Query("SELECT * FROM inventory_reservations WHERE product_id = :productId AND location_id = :locationId AND status = 'ACTIVE'")
    fun findActiveReservationsByProductAndLocation(productId: UUID, locationId: UUID): Flux<InventoryReservation>

    /**
     * Find expired reservations (past expires_at and still active)
     */
    @Query("SELECT * FROM inventory_reservations WHERE status = 'ACTIVE' AND expires_at < :now")
    fun findExpiredReservations(now: OffsetDateTime): Flux<InventoryReservation>

    /**
     * Find reservations expiring soon
     */
    @Query("SELECT * FROM inventory_reservations WHERE status = 'ACTIVE' AND expires_at BETWEEN :now AND :threshold ORDER BY expires_at ASC")
    fun findReservationsExpiringSoon(now: OffsetDateTime, threshold: OffsetDateTime): Flux<InventoryReservation>

    /**
     * Find reservations by type
     */
    fun findByReservationType(reservationType: String): Flux<InventoryReservation>

    /**
     * Find reservations by product, location, and status
     */
    fun findByProductIdAndLocationIdAndStatus(productId: UUID, locationId: UUID, status: String): Flux<InventoryReservation>

    /**
     * Get total reserved quantity for a product at a location
     */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations WHERE product_id = :productId AND location_id = :locationId AND status = 'ACTIVE'")
    fun getTotalReservedQuantity(productId: UUID, locationId: UUID): Mono<Long>

    /**
     * Count active reservations
     */
    fun countByStatus(status: String): Mono<Long>

    /**
     * Count reservations for a product
     */
    fun countByProductId(productId: UUID): Mono<Long>
}
