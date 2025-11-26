package com.pintailconsultingllc.cqrsspike.product.command.exception

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import java.util.UUID

/**
 * Base exception for product domain errors.
 */
sealed class ProductDomainException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when a product with the given ID is not found.
 */
class ProductNotFoundException(
    val productId: UUID
) : ProductDomainException("Product not found: $productId")

/**
 * Thrown when attempting to create a product with a duplicate SKU.
 */
class DuplicateSkuException(
    val sku: String
) : ProductDomainException("Product with SKU '$sku' already exists")

/**
 * Thrown when an invalid state transition is attempted.
 */
class InvalidStateTransitionException(
    val productId: UUID,
    val currentStatus: ProductStatus,
    val targetStatus: ProductStatus
) : ProductDomainException(
    "Cannot transition product $productId from $currentStatus to $targetStatus. " +
    "Valid transitions: ${currentStatus.validTransitions()}"
)

/**
 * Thrown when a business invariant is violated.
 */
class ProductInvariantViolationException(
    val productId: UUID?,
    val invariant: String,
    val details: String
) : ProductDomainException("Invariant violation for product ${productId ?: "new"}: $invariant - $details")

/**
 * Thrown when optimistic locking detects a concurrent modification.
 */
class ConcurrentModificationException(
    val productId: UUID,
    val expectedVersion: Long,
    val actualVersion: Long
) : ProductDomainException(
    "Concurrent modification detected for product $productId. " +
    "Expected version: $expectedVersion, actual version: $actualVersion"
)

/**
 * Thrown when attempting to modify a deleted product.
 */
class ProductDeletedException(
    val productId: UUID
) : ProductDomainException("Product $productId has been deleted and cannot be modified")

/**
 * Thrown when price change exceeds allowed threshold without confirmation.
 */
class PriceChangeThresholdExceededException(
    val productId: UUID,
    val currentPriceCents: Int,
    val newPriceCents: Int,
    val changePercentage: Double,
    val thresholdPercentage: Double
) : ProductDomainException(
    "Price change of ${String.format("%.2f", changePercentage)}% exceeds threshold of ${thresholdPercentage}% " +
    "for product $productId. Confirmation required."
)
