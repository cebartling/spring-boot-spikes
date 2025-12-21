package com.pintailconsultingllc.cdcdebezium.validation

/**
 * Interface for CDC event validation rules.
 * Rules are executed in order and can short-circuit on failure.
 */
interface ValidationRule<T> {
    /**
     * Unique identifier for this rule.
     */
    val ruleId: String

    /**
     * Human-readable description of what this rule validates.
     */
    val description: String

    /**
     * Validate the event and return a result.
     * Should not throw exceptions; instead return ValidationResult.failure()
     */
    fun validate(event: T): ValidationResult

    /**
     * Whether validation should continue if this rule fails.
     * Default is false (stop on first failure).
     */
    val continueOnFailure: Boolean get() = false
}
