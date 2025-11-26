package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.exception.DuplicateSkuException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductNotFoundException
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Repository service for Product aggregate persistence.
 *
 * Handles:
 * - Aggregate state persistence
 * - Event publication (to event store)
 * - Optimistic locking
 * - SKU uniqueness validation
 */
@Service
class ProductAggregateRepository(
    private val repository: ProductCommandRepository,
    private val eventStoreRepository: EventStoreRepository
) {
    private val logger = LoggerFactory.getLogger(ProductAggregateRepository::class.java)

    /**
     * Saves a new Product aggregate.
     *
     * @param aggregate The aggregate to save
     * @return Mono<ProductAggregate> with events cleared
     * @throws DuplicateSkuException if SKU already exists
     */
    @Transactional
    fun save(aggregate: ProductAggregate): Mono<ProductAggregate> {
        val events = aggregate.getUncommittedEvents()

        return validateSkuUniqueness(aggregate.sku, aggregate.id)
            .then(persistEntity(aggregate))
            .flatMap { savedEntity ->
                publishEvents(events)
                    .thenReturn(aggregate)
            }
            .doOnSuccess {
                logger.info("Saved product aggregate: id=${aggregate.id}, sku=${aggregate.sku}")
            }
            .doOnError { error ->
                logger.error("Failed to save product aggregate: id=${aggregate.id}", error)
            }
    }

    /**
     * Updates an existing Product aggregate.
     *
     * @param aggregate The aggregate to update
     * @return Mono<ProductAggregate> with events cleared
     * @throws ConcurrentModificationException if version mismatch
     */
    @Transactional
    fun update(aggregate: ProductAggregate): Mono<ProductAggregate> {
        val events = aggregate.getUncommittedEvents()

        return persistEntity(aggregate)
            .flatMap { savedEntity ->
                publishEvents(events)
                    .thenReturn(aggregate)
            }
            .onErrorMap(OptimisticLockingFailureException::class.java) { error ->
                ConcurrentModificationException(
                    productId = aggregate.id,
                    expectedVersion = aggregate.version - 1,
                    actualVersion = aggregate.version
                )
            }
            .doOnSuccess {
                logger.info("Updated product aggregate: id=${aggregate.id}, version=${aggregate.version}")
            }
    }

    /**
     * Finds a Product aggregate by ID.
     *
     * Note: In a full event-sourced system, this would reconstitute from events.
     * This implementation uses snapshot + events for efficiency.
     *
     * @param id The product ID
     * @return Mono<ProductAggregate>
     * @throws ProductNotFoundException if not found
     */
    fun findById(id: UUID): Mono<ProductAggregate> {
        return repository.findByIdNotDeleted(id)
            .switchIfEmpty(Mono.error(ProductNotFoundException(id)))
            .flatMap { entity ->
                // In full event sourcing, reconstitute from event store
                reconstitute(entity)
            }
    }

    /**
     * Finds a Product aggregate by SKU.
     */
    fun findBySku(sku: String): Mono<ProductAggregate> {
        return repository.findBySku(sku.uppercase())
            .switchIfEmpty(Mono.defer {
                Mono.error(IllegalArgumentException("Product not found for SKU: $sku"))
            })
            .flatMap { entity -> reconstitute(entity) }
    }

    /**
     * Checks if a product exists by ID (not deleted).
     */
    fun exists(id: UUID): Mono<Boolean> {
        return repository.findByIdNotDeleted(id)
            .hasElement()
    }

    // Private helper methods

    private fun validateSkuUniqueness(sku: String, excludeId: UUID): Mono<Void> {
        return repository.findBySku(sku.uppercase())
            .filter { it.id != excludeId }
            .flatMap<Void> {
                Mono.error(DuplicateSkuException(sku))
            }
            .then()
    }

    private fun persistEntity(aggregate: ProductAggregate): Mono<ProductEntity> {
        val entity = ProductAggregateMapper.toEntity(aggregate)
        return repository.save(entity)
    }

    private fun publishEvents(events: List<ProductEvent>): Mono<Void> {
        if (events.isEmpty()) {
            return Mono.empty()
        }

        return eventStoreRepository.saveEvents(events)
            .doOnSuccess {
                logger.debug("Published ${events.size} events")
            }
    }

    private fun reconstitute(entity: ProductEntity): Mono<ProductAggregate> {
        // Load events and reconstitute
        return eventStoreRepository.findEventsByAggregateId(entity.id)
            .collectList()
            .map { events ->
                if (events.isNotEmpty()) {
                    ProductAggregate.reconstitute(events)
                } else {
                    // Fallback: No events found, this shouldn't happen in normal flow
                    // but can occur during initial development or data migration
                    throw IllegalStateException("No events found for product ${entity.id}")
                }
            }
    }
}
