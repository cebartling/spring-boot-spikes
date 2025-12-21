package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.validation.ValidationResult
import com.pintailconsultingllc.cdcdebezium.validation.ValidationRule
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
@Order(3)
class TemporalConsistencyRule : ValidationRule<CustomerCdcEvent> {

    override val ruleId = "TEMPORAL_001"
    override val description = "Validates timestamp consistency"
    override val continueOnFailure = true

    companion object {
        val MAX_CLOCK_DRIFT: Duration = Duration.ofMinutes(5)
        val MAX_EVENT_AGE: Duration = Duration.ofHours(24)
    }

    override fun validate(event: CustomerCdcEvent): ValidationResult {
        val now = Instant.now()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        event.sourceTimestamp?.let { ts ->
            val eventTime = Instant.ofEpochMilli(ts)

            if (eventTime.isAfter(now.plus(MAX_CLOCK_DRIFT))) {
                errors.add("Source timestamp is in the future: $eventTime (now: $now)")
            }

            if (eventTime.isBefore(now.minus(MAX_EVENT_AGE))) {
                warnings.add("Event is older than ${MAX_EVENT_AGE.toHours()} hours: $eventTime")
            }
        } ?: run {
            warnings.add("Source timestamp is missing, using current time")
        }

        event.updatedAt?.let { updatedAt ->
            if (updatedAt.isAfter(now.plus(MAX_CLOCK_DRIFT))) {
                errors.add("updatedAt timestamp is in the future: $updatedAt")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success(
                ruleId = ruleId,
                message = if (warnings.isEmpty()) {
                    "Temporal validation passed"
                } else {
                    "Temporal validation passed with warnings"
                }
            )
        } else {
            ValidationResult.failure(
                ruleId = ruleId,
                message = "Temporal validation failed",
                details = mapOf("errors" to errors, "warnings" to warnings)
            )
        }
    }
}
