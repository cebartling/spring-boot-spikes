package com.pintailconsultingllc.cqrsspike.product.command.validation

/**
 * Represents a validation error with field and message.
 */
data class ValidationError(
    val field: String,
    val message: String,
    val code: String? = null
)

/**
 * Result of command validation.
 */
sealed class ValidationResult {
    /**
     * Validation passed successfully.
     */
    data object Valid : ValidationResult()

    /**
     * Validation failed with one or more errors.
     */
    data class Invalid(val errors: List<ValidationError>) : ValidationResult() {
        constructor(vararg errors: ValidationError) : this(errors.toList())

        fun toException(): CommandValidationException = CommandValidationException(errors)
    }

    val isValid: Boolean get() = this is Valid
}

/**
 * Exception thrown when command validation fails.
 */
class CommandValidationException(
    val errors: List<ValidationError>
) : RuntimeException("Command validation failed: ${errors.joinToString { "${it.field}: ${it.message}" }}")
