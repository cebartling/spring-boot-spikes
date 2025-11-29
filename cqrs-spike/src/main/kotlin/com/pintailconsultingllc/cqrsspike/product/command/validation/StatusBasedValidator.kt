package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Validates business rules based on product status.
 *
 * Implements AC9 requirements:
 * - DRAFT: can be freely edited
 * - ACTIVE: price changes >20% require confirmation
 * - DISCONTINUED: cannot be reactivated
 */
@Component
class StatusBasedValidator(
    private val businessRulesConfig: BusinessRulesConfig
) {

    /**
     * Checks if a product in the given status can be freely edited.
     * Per AC9: "Products in DRAFT status can be freely edited"
     *
     * @param status Current product status
     * @return true if the product can be edited without restrictions
     */
    fun canEditFreely(status: ProductStatus): Boolean = when (status) {
        ProductStatus.DRAFT -> true
        ProductStatus.ACTIVE -> false  // ACTIVE products have price change restrictions (>20% requires confirmation)
        ProductStatus.DISCONTINUED -> false  // Terminal state - no editing
    }

    /**
     * Checks if editing is allowed for the given status.
     *
     * @param status Current product status
     * @return true if editing is allowed (even with restrictions)
     */
    fun canEdit(status: ProductStatus): Boolean = when (status) {
        ProductStatus.DRAFT -> true
        ProductStatus.ACTIVE -> true
        ProductStatus.DISCONTINUED -> false
    }

    /**
     * Checks if price change requires confirmation based on status and change percentage.
     * Per AC9: "Products in ACTIVE status require confirmation for price changes over 20%"
     *
     * @param status Current product status
     * @param currentPriceCents Current price in cents
     * @param newPriceCents New price in cents
     * @return true if confirmation is required
     */
    fun requiresPriceConfirmation(
        status: ProductStatus,
        currentPriceCents: Int,
        newPriceCents: Int
    ): Boolean {
        if (status != ProductStatus.ACTIVE) return false
        if (currentPriceCents == 0) return false

        val changePercent = abs(calculatePriceChangePercent(currentPriceCents, newPriceCents))
        return changePercent > businessRulesConfig.priceChangeThresholdPercent
    }

    /**
     * Calculates the price change percentage.
     *
     * @param currentPriceCents Current price in cents
     * @param newPriceCents New price in cents
     * @return Percentage change (positive for increase, negative for decrease)
     */
    fun calculatePriceChangePercent(currentPriceCents: Int, newPriceCents: Int): Double {
        if (currentPriceCents == 0) return 100.0
        return ((newPriceCents - currentPriceCents).toDouble() / currentPriceCents) * 100.0
    }

    /**
     * Checks if a product can transition to the target status.
     * Per AC9: "Products in DISCONTINUED status cannot be reactivated"
     *
     * @param currentStatus Current product status
     * @param targetStatus Desired target status
     * @return true if transition is allowed
     */
    fun canTransitionTo(currentStatus: ProductStatus, targetStatus: ProductStatus): Boolean =
        currentStatus.canTransitionTo(targetStatus)

    /**
     * Validates a status transition and returns detailed error if invalid.
     *
     * @param currentStatus Current product status
     * @param targetStatus Desired target status
     * @return ValidationResult.Valid if allowed, ValidationResult.Invalid with error details otherwise
     */
    fun validateTransition(
        currentStatus: ProductStatus,
        targetStatus: ProductStatus
    ): ValidationResult {
        return if (canTransitionTo(currentStatus, targetStatus)) {
            ValidationResult.Valid
        } else {
            val errorCode = when {
                currentStatus == ProductStatus.DISCONTINUED && targetStatus == ProductStatus.ACTIVE ->
                    ValidationErrorCode.REACTIVATION_NOT_ALLOWED
                else -> ValidationErrorCode.INVALID_STATE_TRANSITION
            }

            val message = when (errorCode) {
                ValidationErrorCode.REACTIVATION_NOT_ALLOWED ->
                    "Products in DISCONTINUED status cannot be reactivated"
                else ->
                    "Cannot transition from $currentStatus to $targetStatus. Valid transitions: ${currentStatus.validTransitions()}"
            }

            ValidationResult.Invalid(listOf(
                ValidationError(
                    field = "status",
                    message = message,
                    code = errorCode.name
                )
            ))
        }
    }

    /**
     * Validates a price change and returns detailed error if confirmation is required but not provided.
     *
     * @param status Current product status
     * @param currentPriceCents Current price in cents
     * @param newPriceCents New price in cents
     * @param confirmLargeChange Whether confirmation was provided
     * @return ValidationResult.Valid if allowed, ValidationResult.Invalid with error details otherwise
     */
    fun validatePriceChange(
        status: ProductStatus,
        currentPriceCents: Int,
        newPriceCents: Int,
        confirmLargeChange: Boolean
    ): ValidationResult {
        if (!requiresPriceConfirmation(status, currentPriceCents, newPriceCents)) {
            return ValidationResult.Valid
        }

        if (confirmLargeChange) {
            return ValidationResult.Valid
        }

        val changePercent = calculatePriceChangePercent(currentPriceCents, newPriceCents)

        return ValidationResult.Invalid(listOf(
            ValidationError(
                field = "newPriceCents",
                message = "Price change of ${String.format("%.2f", changePercent)}% exceeds " +
                        "${businessRulesConfig.priceChangeThresholdPercent}% threshold. " +
                        "Set confirmLargeChange=true to confirm.",
                code = ValidationErrorCode.PRICE_THRESHOLD_EXCEEDED.name
            )
        ))
    }

    /**
     * Gets all editing restrictions for a given status.
     *
     * @param status Current product status
     * @return List of restriction descriptions
     */
    fun getEditingRestrictions(status: ProductStatus): List<String> = when (status) {
        ProductStatus.DRAFT -> emptyList()
        ProductStatus.ACTIVE -> listOf(
            "Price changes exceeding ${businessRulesConfig.priceChangeThresholdPercent}% require confirmation"
        )
        ProductStatus.DISCONTINUED -> listOf(
            "Product cannot be edited in DISCONTINUED status",
            "Product cannot be reactivated"
        )
    }

    /**
     * Gets the price change threshold percentage from configuration.
     *
     * @return Threshold percentage
     */
    fun getPriceChangeThreshold(): Double = businessRulesConfig.priceChangeThresholdPercent
}
