package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.InventoryStock
import com.pintailconsultingllc.resiliencyspike.repository.InventoryStockRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service for managing inventory stock levels
 * Demonstrates reactive inventory management operations
 */
@Service
class InventoryStockService(
    private val inventoryStockRepository: InventoryStockRepository
) {

    /**
     * Get stock by ID
     */
    fun findStockById(id: UUID): Mono<InventoryStock> {
        return inventoryStockRepository.findById(id)
    }

    /**
     * Get stock for a product at a specific location
     */
    fun findStockByProductAndLocation(productId: UUID, locationId: UUID): Mono<InventoryStock> {
        return inventoryStockRepository.findByProductIdAndLocationId(productId, locationId)
    }

    /**
     * Get all stock records for a product across all locations
     */
    fun findStockByProduct(productId: UUID): Flux<InventoryStock> {
        return inventoryStockRepository.findByProductId(productId)
    }

    /**
     * Get all stock records at a location
     */
    fun findStockByLocation(locationId: UUID): Flux<InventoryStock> {
        return inventoryStockRepository.findByLocationId(locationId)
    }

    /**
     * Find all low stock items (below reorder point)
     */
    fun findLowStockItems(): Flux<InventoryStock> {
        return inventoryStockRepository.findLowStockItems()
    }

    /**
     * Find low stock items at a specific location
     */
    fun findLowStockItemsByLocation(locationId: UUID): Flux<InventoryStock> {
        return inventoryStockRepository.findLowStockItemsByLocation(locationId)
    }

    /**
     * Find out of stock items
     */
    fun findOutOfStockItems(): Flux<InventoryStock> {
        return inventoryStockRepository.findOutOfStockItems()
    }

    /**
     * Find items below a specific threshold
     */
    fun findItemsBelowThreshold(threshold: Int): Flux<InventoryStock> {
        return inventoryStockRepository.findItemsBelowThreshold(threshold)
    }

    /**
     * Get total on-hand quantity for a product across all locations
     */
    fun getTotalOnHandQuantity(productId: UUID): Mono<Long> {
        return inventoryStockRepository.getTotalOnHandQuantityForProduct(productId)
            .defaultIfEmpty(0L)
    }

    /**
     * Get total available quantity for a product across all locations
     */
    fun getTotalAvailableQuantity(productId: UUID): Mono<Long> {
        return inventoryStockRepository.getTotalAvailableQuantityForProduct(productId)
            .defaultIfEmpty(0L)
    }

    /**
     * Create or update stock record
     */
    fun saveStock(stock: InventoryStock): Mono<InventoryStock> {
        return inventoryStockRepository.save(stock)
    }

    /**
     * Adjust stock quantity (increase or decrease)
     */
    fun adjustStockQuantity(productId: UUID, locationId: UUID, quantityChange: Int): Mono<InventoryStock> {
        return findStockByProductAndLocation(productId, locationId)
            .flatMap { stock ->
                val newQuantity = stock.quantityOnHand + quantityChange
                inventoryStockRepository.save(stock.copy(quantityOnHand = newQuantity))
            }
    }

    /**
     * Reserve stock quantity
     */
    fun reserveStock(productId: UUID, locationId: UUID, quantity: Int): Mono<InventoryStock> {
        return findStockByProductAndLocation(productId, locationId)
            .flatMap { stock ->
                val newReserved = stock.quantityReserved + quantity
                inventoryStockRepository.save(stock.copy(quantityReserved = newReserved))
            }
    }

    /**
     * Release reserved stock
     */
    fun releaseReservedStock(productId: UUID, locationId: UUID, quantity: Int): Mono<InventoryStock> {
        return findStockByProductAndLocation(productId, locationId)
            .flatMap { stock ->
                val newReserved = maxOf(0, stock.quantityReserved - quantity)
                inventoryStockRepository.save(stock.copy(quantityReserved = newReserved))
            }
    }

    /**
     * Check if sufficient stock is available
     */
    fun isStockAvailable(productId: UUID, locationId: UUID, requiredQuantity: Int): Mono<Boolean> {
        return findStockByProductAndLocation(productId, locationId)
            .map { stock -> stock.quantityAvailable >= requiredQuantity }
            .defaultIfEmpty(false)
    }

    /**
     * Get items with reservations
     */
    fun findItemsWithReservations(): Flux<InventoryStock> {
        return inventoryStockRepository.findItemsWithReservations()
    }

    /**
     * Count stock records at a location
     */
    fun countStockRecordsByLocation(locationId: UUID): Mono<Long> {
        return inventoryStockRepository.countByLocationId(locationId)
    }

    /**
     * Delete stock record
     */
    fun deleteStock(id: UUID): Mono<Void> {
        return inventoryStockRepository.deleteById(id)
    }
}
