package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.InventoryTransaction
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.*

/**
 * Reactive repository for InventoryTransaction entities
 */
@Repository
interface InventoryTransactionRepository : ReactiveCrudRepository<InventoryTransaction, UUID> {

    /**
     * Find transactions by product ID
     */
    fun findByProductId(productId: UUID): Flux<InventoryTransaction>

    /**
     * Find transactions by location ID
     */
    fun findByLocationId(locationId: UUID): Flux<InventoryTransaction>

    /**
     * Find transactions by type
     */
    fun findByTransactionType(transactionType: String): Flux<InventoryTransaction>

    /**
     * Find transactions by product and location
     */
    fun findByProductIdAndLocationId(productId: UUID, locationId: UUID): Flux<InventoryTransaction>

    /**
     * Find transactions by reference number
     */
    fun findByReferenceNumber(referenceNumber: String): Flux<InventoryTransaction>

    /**
     * Find recent transactions ordered by date descending
     */
    @Query("SELECT * FROM inventory_transactions ORDER BY transaction_date DESC LIMIT :limit")
    fun findRecentTransactions(limit: Int): Flux<InventoryTransaction>

    /**
     * Find transactions by date range
     */
    @Query("SELECT * FROM inventory_transactions WHERE transaction_date BETWEEN :startDate AND :endDate ORDER BY transaction_date DESC")
    fun findByTransactionDateBetween(startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<InventoryTransaction>

    /**
     * Find transactions by product and date range
     */
    @Query("SELECT * FROM inventory_transactions WHERE product_id = :productId AND transaction_date BETWEEN :startDate AND :endDate ORDER BY transaction_date DESC")
    fun findByProductIdAndDateRange(productId: UUID, startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<InventoryTransaction>

    /**
     * Find transactions by location and date range
     */
    @Query("SELECT * FROM inventory_transactions WHERE location_id = :locationId AND transaction_date BETWEEN :startDate AND :endDate ORDER BY transaction_date DESC")
    fun findByLocationIdAndDateRange(locationId: UUID, startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<InventoryTransaction>

    /**
     * Find transactions by type and date range
     */
    @Query("SELECT * FROM inventory_transactions WHERE transaction_type = :transactionType AND transaction_date BETWEEN :startDate AND :endDate ORDER BY transaction_date DESC")
    fun findByTypeAndDateRange(transactionType: String, startDate: OffsetDateTime, endDate: OffsetDateTime): Flux<InventoryTransaction>

    /**
     * Count transactions by type
     */
    fun countByTransactionType(transactionType: String): Mono<Long>

    /**
     * Count transactions for a product
     */
    fun countByProductId(productId: UUID): Mono<Long>
}
