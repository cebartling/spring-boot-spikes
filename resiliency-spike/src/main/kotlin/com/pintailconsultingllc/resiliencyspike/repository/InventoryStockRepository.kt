package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.InventoryStock
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Reactive repository for InventoryStock entities
 */
@Repository
interface InventoryStockRepository : ReactiveCrudRepository<InventoryStock, UUID> {

    /**
     * Find stock by product ID
     */
    fun findByProductId(productId: UUID): Flux<InventoryStock>

    /**
     * Find stock by location ID
     */
    fun findByLocationId(locationId: UUID): Flux<InventoryStock>

    /**
     * Find stock by product and location
     */
    fun findByProductIdAndLocationId(productId: UUID, locationId: UUID): Mono<InventoryStock>

    /**
     * Find low stock items (available quantity below reorder point)
     */
    @Query("SELECT * FROM inventory_stock WHERE quantity_available <= reorder_point ORDER BY quantity_available ASC")
    fun findLowStockItems(): Flux<InventoryStock>

    /**
     * Find low stock items at a specific location
     */
    @Query("SELECT * FROM inventory_stock WHERE location_id = :locationId AND quantity_available <= reorder_point ORDER BY quantity_available ASC")
    fun findLowStockItemsByLocation(locationId: UUID): Flux<InventoryStock>

    /**
     * Find products with zero stock
     */
    @Query("SELECT * FROM inventory_stock WHERE quantity_on_hand = 0")
    fun findOutOfStockItems(): Flux<InventoryStock>

    /**
     * Find products with available quantity below threshold
     */
    @Query("SELECT * FROM inventory_stock WHERE quantity_available < :threshold ORDER BY quantity_available ASC")
    fun findItemsBelowThreshold(threshold: Int): Flux<InventoryStock>

    /**
     * Get total on-hand quantity for a product across all locations
     */
    @Query("SELECT SUM(quantity_on_hand) FROM inventory_stock WHERE product_id = :productId")
    fun getTotalOnHandQuantityForProduct(productId: UUID): Mono<Long>

    /**
     * Get total available quantity for a product across all locations
     */
    @Query("SELECT SUM(quantity_available) FROM inventory_stock WHERE product_id = :productId")
    fun getTotalAvailableQuantityForProduct(productId: UUID): Mono<Long>

    /**
     * Find all stock records with reserved quantities
     */
    @Query("SELECT * FROM inventory_stock WHERE quantity_reserved > 0")
    fun findItemsWithReservations(): Flux<InventoryStock>

    /**
     * Count stock records by location
     */
    fun countByLocationId(locationId: UUID): Mono<Long>
}
