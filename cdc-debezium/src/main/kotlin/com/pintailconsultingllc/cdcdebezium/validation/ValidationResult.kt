package com.pintailconsultingllc.cdcdebezium.validation

import java.time.Instant

data class ValidationResult(
    val valid: Boolean,
    val ruleId: String,
    val message: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val timestamp: Instant = Instant.now()
) {
    companion object {
        fun success(ruleId: String, message: String? = null): ValidationResult =
            ValidationResult(valid = true, ruleId = ruleId, message = message)

        fun failure(
            ruleId: String,
            message: String,
            details: Map<String, Any> = emptyMap()
        ): ValidationResult =
            ValidationResult(
                valid = false,
                ruleId = ruleId,
                message = message,
                details = details
            )
    }
}

data class AggregatedValidationResult(
    val valid: Boolean,
    val results: List<ValidationResult>,
    val eventId: String,
    val entityType: String,
    val timestamp: Instant = Instant.now()
) {
    val failures: List<ValidationResult>
        get() = results.filter { !it.valid }

    val failureCount: Int
        get() = failures.size

    companion object {
        fun fromResults(
            results: List<ValidationResult>,
            eventId: String,
            entityType: String
        ): AggregatedValidationResult =
            AggregatedValidationResult(
                valid = results.all { it.valid },
                results = results,
                eventId = eventId,
                entityType = entityType
            )
    }
}
