package com.pintailconsultingllc.cqrsspike.product.command.exception

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.command.validation.ValidationError
import com.pintailconsultingllc.cqrsspike.product.command.validation.ValidationResult
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
 *
 * AC9: "Products in ACTIVE status require confirmation for price changes over 20%"
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

/**
 * Thrown when business rule validation fails in the domain service.
 * Contains structured validation errors for client consumption.
 *
 * This exception is used by ValidationDomainService when complex
 * validation rules (requiring repository access) fail.
 */
class BusinessRuleViolationException(
    val errors: List<ValidationError>,
    message: String = "Business rule validation failed"
) : ProductDomainException(message) {

    companion object {
        /**
         * Creates a BusinessRuleViolationException from a ValidationResult.Invalid.
         *
         * @param result The invalid validation result
         * @return BusinessRuleViolationException with all validation errors
         */
        fun fromValidationResult(result: ValidationResult.Invalid): BusinessRuleViolationException {
            val message = result.errors.joinToString("; ") { "${it.field}: ${it.message}" }
            return BusinessRuleViolationException(result.errors, message)
        }
    }
}

/**
 * Thrown when attempting to reactivate a DISCONTINUED product.
 *
 * AC9: "Products in DISCONTINUED status cannot be reactivated"
 */
class ProductReactivationException(
    val productId: UUID
) : ProductDomainException(
    "Product $productId is in DISCONTINUED status and cannot be reactivated"
)
