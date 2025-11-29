package com.pintailconsultingllc.cqrsspike.product.command.validation

/**
 * Comprehensive error codes for validation failures.
 *
 * These codes are machine-readable and can be used by clients
 * for internationalization or custom error handling.
 *
 * AC9 Requirements:
 * - Clear, machine-readable error responses
 * - Structured validation errors
 */
enum class ValidationErrorCode(val httpStatus: Int) {
    // Field-level validation errors (400)
    /** Field is required but was not provided */
    REQUIRED(400),

    /** Field value is below minimum length */
    MIN_LENGTH(400),

    /** Field value exceeds maximum length */
    MAX_LENGTH(400),

    /** Field value does not match expected format */
    INVALID_FORMAT(400),

    /** Field value must be a positive number */
    POSITIVE_REQUIRED(400),

    // Business rule violations (400/409/422)
    /** Value already exists (e.g., duplicate SKU) */
    DUPLICATE(409),

    /** Invalid state transition attempted */
    INVALID_STATE_TRANSITION(422),

    /** Price change exceeds threshold without confirmation */
    PRICE_THRESHOLD_EXCEEDED(422),

    /** Attempted to reactivate a DISCONTINUED product */
    REACTIVATION_NOT_ALLOWED(422),

    /** Operation attempted on deleted product */
    PRODUCT_DELETED(410),

    /** Concurrent modification detected */
    CONCURRENT_MODIFICATION(409),

    // Domain invariant violations (400)
    /** Generic domain invariant violation */
    INVARIANT_VIOLATION(400)
}

/**
 * Enhanced validation error with additional context.
 *
 * Provides detailed information about validation failures including
 * the current value and the constraint that was violated.
 */
data class EnhancedValidationError(
    /** The field that failed validation */
    val field: String,
    /** Human-readable error message */
    val message: String,
    /** Machine-readable error code */
    val code: ValidationErrorCode,
    /** The value that failed validation (optional) */
    val currentValue: Any? = null,
    /** The constraint that was violated (optional) */
    val constraint: Any? = null
) {
    companion object {
        /**
         * Create a REQUIRED error for a missing field.
         */
        fun required(field: String) = EnhancedValidationError(
            field = field,
            message = "${field.replaceFirstChar { it.uppercase() }} is required",
            code = ValidationErrorCode.REQUIRED
        )

        /**
         * Create a MIN_LENGTH error.
         */
        fun minLength(field: String, minLength: Int, actualLength: Int) = EnhancedValidationError(
            field = field,
            message = "${field.replaceFirstChar { it.uppercase() }} must be at least $minLength characters",
            code = ValidationErrorCode.MIN_LENGTH,
            currentValue = actualLength,
            constraint = minLength
        )

        /**
         * Create a MAX_LENGTH error.
         */
        fun maxLength(field: String, maxLength: Int, actualLength: Int) = EnhancedValidationError(
            field = field,
            message = "${field.replaceFirstChar { it.uppercase() }} must not exceed $maxLength characters",
            code = ValidationErrorCode.MAX_LENGTH,
            currentValue = actualLength,
            constraint = maxLength
        )

        /**
         * Create an INVALID_FORMAT error.
         */
        fun invalidFormat(field: String, expectedFormat: String) = EnhancedValidationError(
            field = field,
            message = "${field.replaceFirstChar { it.uppercase() }} format is invalid. Expected: $expectedFormat",
            code = ValidationErrorCode.INVALID_FORMAT,
            constraint = expectedFormat
        )

        /**
         * Create a POSITIVE_REQUIRED error.
         */
        fun positiveRequired(field: String, actualValue: Any) = EnhancedValidationError(
            field = field,
            message = "${field.replaceFirstChar { it.uppercase() }} must be a positive integer",
            code = ValidationErrorCode.POSITIVE_REQUIRED,
            currentValue = actualValue
        )

        /**
         * Create a DUPLICATE error.
         */
        fun duplicate(field: String, value: Any) = EnhancedValidationError(
            field = field,
            message = "${field.replaceFirstChar { it.uppercase() }} '$value' already exists",
            code = ValidationErrorCode.DUPLICATE,
            currentValue = value
        )

        /**
         * Create an INVALID_STATE_TRANSITION error.
         */
        fun invalidStateTransition(
            currentStatus: String,
            targetStatus: String
        ) = EnhancedValidationError(
            field = "status",
            message = "Cannot transition from $currentStatus to $targetStatus",
            code = ValidationErrorCode.INVALID_STATE_TRANSITION,
            currentValue = currentStatus,
            constraint = targetStatus
        )

        /**
         * Create a REACTIVATION_NOT_ALLOWED error.
         * AC9: "Products in DISCONTINUED status cannot be reactivated"
         */
        fun reactivationNotAllowed() = EnhancedValidationError(
            field = "status",
            message = "Products in DISCONTINUED status cannot be reactivated",
            code = ValidationErrorCode.REACTIVATION_NOT_ALLOWED,
            currentValue = "DISCONTINUED",
            constraint = "ACTIVE"
        )

        /**
         * Create a PRICE_THRESHOLD_EXCEEDED error.
         * AC9: "Products in ACTIVE status require confirmation for price changes over 20%"
         */
        fun priceThresholdExceeded(
            changePercentage: Double,
            threshold: Double,
            currentPrice: Int,
            newPrice: Int
        ) = EnhancedValidationError(
            field = "newPriceCents",
            message = "Price change of ${String.format("%.2f", changePercentage)}% exceeds ${threshold}% threshold. Set confirmLargeChange=true to confirm.",
            code = ValidationErrorCode.PRICE_THRESHOLD_EXCEEDED,
            currentValue = mapOf("currentPrice" to currentPrice, "newPrice" to newPrice, "changePercentage" to changePercentage),
            constraint = threshold
        )

        /**
         * Create a PRODUCT_DELETED error.
         */
        fun productDeleted(productId: Any) = EnhancedValidationError(
            field = "id",
            message = "Product has already been deleted",
            code = ValidationErrorCode.PRODUCT_DELETED,
            currentValue = productId
        )
    }
}
