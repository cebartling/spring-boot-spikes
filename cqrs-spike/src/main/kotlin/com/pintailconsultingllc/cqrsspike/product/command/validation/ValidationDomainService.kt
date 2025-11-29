package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ProductAggregateRepository
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Domain service for complex validation rules that require repository access
 * or cross-aggregate validation.
 *
 * This service implements AC9 business rules that cannot be validated
 * by command validators alone (e.g., SKU uniqueness).
 *
 * AC9 Requirements:
 * - Product SKU is required, unique
 * - Products in DRAFT status can be freely edited
 * - Products in ACTIVE status require confirmation for price changes over 20%
 * - Products in DISCONTINUED status cannot be reactivated
 * - Deleted products are soft-deleted and excluded from queries by default
 */
@Service
class ValidationDomainService(
    private val productRepository: ProductAggregateRepository,
    private val businessRulesConfig: BusinessRulesConfig,
    private val statusBasedValidator: StatusBasedValidator
) {
    private val logger = LoggerFactory.getLogger(ValidationDomainService::class.java)

    /**
     * Validates SKU uniqueness for product creation.
     * Per AC9: "Product SKU is required, unique..."
     *
     * @param sku The SKU to validate
     * @return Mono<ValidationResult> with DUPLICATE error if SKU exists
     */
    fun validateSkuUniqueness(sku: String): Mono<ValidationResult> {
        val normalizedSku = sku.uppercase().trim()

        return productRepository.findBySku(normalizedSku)
            .map<ValidationResult> { existingProduct ->
                logger.debug("SKU {} already exists with product ID {}", normalizedSku, existingProduct.id)
                ValidationResult.Invalid(listOf(
                    ValidationError(
                        field = "sku",
                        message = "SKU '$normalizedSku' already exists",
                        code = ValidationErrorCode.DUPLICATE.name
                    )
                ))
            }
            .onErrorResume(IllegalArgumentException::class.java) {
                // findBySku throws IllegalArgumentException when not found
                Mono.just(ValidationResult.Valid)
            }
            .defaultIfEmpty(ValidationResult.Valid)
    }

    /**
     * Validates SKU uniqueness excluding a specific product ID (for updates).
     *
     * @param sku The SKU to validate
     * @param excludeProductId Product ID to exclude from uniqueness check
     * @return Mono<ValidationResult> with DUPLICATE error if SKU exists for different product
     */
    fun validateSkuUniquenessExcluding(sku: String, excludeProductId: UUID): Mono<ValidationResult> {
        val normalizedSku = sku.uppercase().trim()

        return productRepository.findBySku(normalizedSku)
            .filter { it.id != excludeProductId }
            .map<ValidationResult> { existingProduct ->
                logger.debug("SKU {} already exists with product ID {}", normalizedSku, existingProduct.id)
                ValidationResult.Invalid(listOf(
                    ValidationError(
                        field = "sku",
                        message = "SKU '$normalizedSku' already exists",
                        code = ValidationErrorCode.DUPLICATE.name
                    )
                ))
            }
            .onErrorResume(IllegalArgumentException::class.java) {
                Mono.just(ValidationResult.Valid)
            }
            .defaultIfEmpty(ValidationResult.Valid)
    }

    /**
     * Validates that a product can be edited based on its current status.
     * Per AC9:
     * - "Products in DRAFT status can be freely edited"
     * - DISCONTINUED products cannot be edited
     *
     * @param productId The product ID to validate
     * @return Mono<ValidationResult> with error if editing is not allowed
     */
    fun validateCanEdit(productId: UUID): Mono<ValidationResult> {
        return productRepository.findById(productId)
            .map { aggregate ->
                if (statusBasedValidator.canEdit(aggregate.status)) {
                    ValidationResult.Valid
                } else {
                    ValidationResult.Invalid(listOf(
                        ValidationError(
                            field = "status",
                            message = "Product in ${aggregate.status} status cannot be edited",
                            code = ValidationErrorCode.INVALID_STATE_TRANSITION.name
                        )
                    ))
                }
            }
    }

    /**
     * Validates price change for an existing product.
     * Per AC9: "Products in ACTIVE status require confirmation for price changes over 20%"
     *
     * @param productId The product ID
     * @param newPriceCents The new price in cents
     * @param confirmLargeChange Whether the user confirmed the large change
     * @return Mono<ValidationResult> with error if confirmation required but not provided
     */
    fun validatePriceChange(
        productId: UUID,
        newPriceCents: Int,
        confirmLargeChange: Boolean
    ): Mono<ValidationResult> {
        return productRepository.findById(productId)
            .map { aggregate ->
                statusBasedValidator.validatePriceChange(
                    aggregate.status,
                    aggregate.priceCents,
                    newPriceCents,
                    confirmLargeChange
                )
            }
    }

    /**
     * Validates that a product can be activated.
     * Per AC9 state transitions: DRAFT → ACTIVE is valid
     *
     * @param productId The product ID
     * @return Mono<ValidationResult> with error if transition not allowed
     */
    fun validateCanActivate(productId: UUID): Mono<ValidationResult> {
        return productRepository.findById(productId)
            .map { aggregate ->
                statusBasedValidator.validateTransition(
                    aggregate.status,
                    ProductStatus.ACTIVE
                )
            }
    }

    /**
     * Validates that a product can be discontinued.
     * Per AC9 state transitions: DRAFT/ACTIVE → DISCONTINUED is valid
     *
     * @param productId The product ID
     * @return Mono<ValidationResult> with error if transition not allowed
     */
    fun validateCanDiscontinue(productId: UUID): Mono<ValidationResult> {
        return productRepository.findById(productId)
            .map { aggregate ->
                statusBasedValidator.validateTransition(
                    aggregate.status,
                    ProductStatus.DISCONTINUED
                )
            }
    }

    /**
     * Validates that a product can be deleted.
     * Per AC9: "Deleted products are soft-deleted"
     * Already deleted products cannot be deleted again.
     *
     * @param productId The product ID
     * @return Mono<ValidationResult> with error if already deleted
     */
    fun validateCanDelete(productId: UUID): Mono<ValidationResult> {
        return productRepository.findById(productId)
            .map { aggregate ->
                if (aggregate.isDeleted) {
                    ValidationResult.Invalid(listOf(
                        ValidationError(
                            field = "id",
                            message = "Product has already been deleted",
                            code = ValidationErrorCode.PRODUCT_DELETED.name
                        )
                    ))
                } else {
                    ValidationResult.Valid
                }
            }
    }

    /**
     * Validates that a product exists and is not deleted.
     *
     * @param productId The product ID
     * @return Mono<ValidationResult> with error if not found
     */
    fun validateProductExists(productId: UUID): Mono<ValidationResult> {
        return productRepository.exists(productId)
            .map { exists ->
                if (exists) {
                    ValidationResult.Valid
                } else {
                    ValidationResult.Invalid(listOf(
                        ValidationError(
                            field = "productId",
                            message = "Product not found: $productId",
                            code = "PRODUCT_NOT_FOUND"
                        )
                    ))
                }
            }
    }

    /**
     * Combines multiple validation results into one.
     * If all are valid, returns Valid.
     * If any are invalid, returns Invalid with all errors combined.
     *
     * @param results Vararg of validation results
     * @return Combined ValidationResult
     */
    fun combineResults(vararg results: ValidationResult): ValidationResult {
        val allErrors = results.filterIsInstance<ValidationResult.Invalid>()
            .flatMap { it.errors }

        return if (allErrors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(allErrors)
        }
    }

    /**
     * Combines multiple Mono<ValidationResult> into one.
     *
     * @param results List of Mono<ValidationResult>
     * @return Mono<ValidationResult> with combined results
     */
    fun combineResultsAsync(vararg results: Mono<ValidationResult>): Mono<ValidationResult> {
        return Mono.zip(results.toList()) { resultsArray ->
            val validationResults = resultsArray.map { it as ValidationResult }
            combineResults(*validationResults.toTypedArray())
        }
    }
}
