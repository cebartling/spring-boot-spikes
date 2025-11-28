package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPosition
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPositionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Projection handler for Product read model.
 *
 * Processes domain events and updates the read model accordingly.
 * Supports idempotent event processing for safe replays.
 */
@Component
class ProductProjector(
    private val readModelRepository: ProductReadModelRepository,
    private val positionRepository: ProjectionPositionRepository
) {
    private val logger = LoggerFactory.getLogger(ProductProjector::class.java)

    companion object {
        const val PROJECTION_NAME = "ProductReadModel"
    }

    /**
     * Process a domain event and update the read model.
     *
     * This method is idempotent - processing the same event twice
     * will not corrupt the read model.
     *
     * @param event The domain event to process
     * @param eventId Unique event identifier for tracking
     * @param eventSequence Sequence number for ordering
     * @return Mono<Void> completing when processing is done
     */
    @Transactional
    fun processEvent(
        event: ProductEvent,
        eventId: UUID,
        eventSequence: Long
    ): Mono<Void> {
        logger.debug(
            "Processing event: type={}, productId={}, version={}",
            event::class.simpleName, event.productId, event.version
        )

        return when (event) {
            is ProductCreated -> handleProductCreated(event, eventId)
            is ProductUpdated -> handleProductUpdated(event, eventId)
            is ProductPriceChanged -> handleProductPriceChanged(event, eventId)
            is ProductActivated -> handleProductActivated(event, eventId)
            is ProductDiscontinued -> handleProductDiscontinued(event, eventId)
            is ProductDeleted -> handleProductDeleted(event, eventId)
        }.flatMap { _ ->
            updateProjectionPosition(eventId, eventSequence)
        }.doOnSuccess { _unused: Void? ->
            logger.debug(
                "Successfully processed event: type={}, productId={}",
                event::class.simpleName, event.productId
            )
        }.doOnError { error ->
            logger.error(
                "Failed to process event: type={}, productId={}",
                event::class.simpleName, event.productId, error
            )
        }
    }

    /**
     * Handle ProductCreated event.
     * Creates a new read model entry.
     */
    private fun handleProductCreated(event: ProductCreated, eventId: UUID): Mono<ProductReadModel> {
        val readModel = ProductReadModel(
            id = event.productId,
            sku = event.sku,
            name = event.name,
            description = event.description,
            priceCents = event.priceCents,
            status = event.status.name,
            createdAt = event.occurredAt,
            updatedAt = event.occurredAt,
            aggregateVersion = event.version,
            isDeleted = false,
            priceDisplay = formatPrice(event.priceCents),
            searchText = buildSearchText(event.name, event.description),
            lastEventId = eventId
        )

        return readModelRepository.save(readModel)
            .doOnSuccess {
                logger.info("Created read model for product: id={}, sku={}", event.productId, event.sku)
            }
    }

    /**
     * Handle ProductUpdated event.
     * Updates name and description in the read model.
     */
    private fun handleProductUpdated(event: ProductUpdated, eventId: UUID): Mono<ProductReadModel> {
        return readModelRepository.findById(event.productId)
            .flatMap { existing ->
                // Check for idempotency
                if (existing.aggregateVersion >= event.version) {
                    logger.debug(
                        "Skipping already processed event: productId={}, version={}",
                        event.productId, event.version
                    )
                    return@flatMap Mono.just(existing)
                }

                val updated = existing.copy(
                    name = event.name,
                    description = event.description,
                    updatedAt = event.occurredAt,
                    aggregateVersion = event.version,
                    searchText = buildSearchText(event.name, event.description),
                    lastEventId = eventId
                )
                readModelRepository.save(updated)
            }
            .switchIfEmpty(Mono.defer {
                logger.warn("Product not found for update: productId={}", event.productId)
                Mono.empty()
            })
    }

    /**
     * Handle ProductPriceChanged event.
     * Updates price and price display in the read model.
     */
    private fun handleProductPriceChanged(event: ProductPriceChanged, eventId: UUID): Mono<ProductReadModel> {
        return readModelRepository.findById(event.productId)
            .flatMap { existing ->
                if (existing.aggregateVersion >= event.version) {
                    return@flatMap Mono.just(existing)
                }

                val updated = existing.copy(
                    priceCents = event.newPriceCents,
                    priceDisplay = formatPrice(event.newPriceCents),
                    updatedAt = event.occurredAt,
                    aggregateVersion = event.version,
                    lastEventId = eventId
                )
                readModelRepository.save(updated)
            }
            .switchIfEmpty(Mono.defer {
                logger.warn("Product not found for price change: productId={}", event.productId)
                Mono.empty()
            })
    }

    /**
     * Handle ProductActivated event.
     * Updates status to ACTIVE in the read model.
     */
    private fun handleProductActivated(event: ProductActivated, eventId: UUID): Mono<ProductReadModel> {
        return readModelRepository.findById(event.productId)
            .flatMap { existing ->
                if (existing.aggregateVersion >= event.version) {
                    return@flatMap Mono.just(existing)
                }

                val updated = existing.copy(
                    status = ProductStatusView.ACTIVE.name,
                    updatedAt = event.occurredAt,
                    aggregateVersion = event.version,
                    lastEventId = eventId
                )
                readModelRepository.save(updated)
            }
            .switchIfEmpty(Mono.defer {
                logger.warn("Product not found for activation: productId={}", event.productId)
                Mono.empty()
            })
    }

    /**
     * Handle ProductDiscontinued event.
     * Updates status to DISCONTINUED in the read model.
     */
    private fun handleProductDiscontinued(event: ProductDiscontinued, eventId: UUID): Mono<ProductReadModel> {
        return readModelRepository.findById(event.productId)
            .flatMap { existing ->
                if (existing.aggregateVersion >= event.version) {
                    return@flatMap Mono.just(existing)
                }

                val updated = existing.copy(
                    status = ProductStatusView.DISCONTINUED.name,
                    updatedAt = event.occurredAt,
                    aggregateVersion = event.version,
                    lastEventId = eventId
                )
                readModelRepository.save(updated)
            }
            .switchIfEmpty(Mono.defer {
                logger.warn("Product not found for discontinuation: productId={}", event.productId)
                Mono.empty()
            })
    }

    /**
     * Handle ProductDeleted event.
     * Marks product as deleted in the read model (soft delete).
     */
    private fun handleProductDeleted(event: ProductDeleted, eventId: UUID): Mono<ProductReadModel> {
        return readModelRepository.findById(event.productId)
            .flatMap { existing ->
                if (existing.aggregateVersion >= event.version) {
                    return@flatMap Mono.just(existing)
                }

                val updated = existing.copy(
                    isDeleted = true,
                    updatedAt = event.occurredAt,
                    aggregateVersion = event.version,
                    lastEventId = eventId
                )
                readModelRepository.save(updated)
            }
            .switchIfEmpty(Mono.defer {
                logger.warn("Product not found for deletion: productId={}", event.productId)
                Mono.empty()
            })
    }

    /**
     * Update the projection position after successfully processing an event.
     */
    private fun updateProjectionPosition(eventId: UUID, eventSequence: Long): Mono<Void> {
        return positionRepository.upsertPosition(
            projectionName = PROJECTION_NAME,
            lastEventId = eventId,
            lastEventSequence = eventSequence,
            eventsProcessed = 1
        ).then()
    }

    /**
     * Get the current projection position.
     */
    fun getProjectionPosition(): Mono<ProjectionPosition> {
        return positionRepository.findByProjectionName(PROJECTION_NAME)
            .defaultIfEmpty(
                ProjectionPosition(
                    projectionName = PROJECTION_NAME,
                    lastEventId = null,
                    lastEventSequence = null,
                    eventsProcessed = 0
                )
            )
    }

    // Helper methods

    private fun formatPrice(cents: Int): String {
        val dollars = cents / 100
        val remainingCents = cents % 100
        return "$${dollars}.${remainingCents.toString().padStart(2, '0')}"
    }

    private fun buildSearchText(name: String, description: String?): String {
        return listOfNotNull(name, description).joinToString(" ")
    }
}
