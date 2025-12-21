package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.validation.ValidationResult
import com.pintailconsultingllc.cdcdebezium.validation.ValidationRule
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(2)
class BusinessValidationRule : ValidationRule<CustomerCdcEvent> {

    override val ruleId = "BUSINESS_001"
    override val description = "Validates business constraints"
    override val continueOnFailure = true

    companion object {
        val VALID_STATUSES = setOf("active", "inactive", "pending", "suspended", "DELETED")
    }

    override fun validate(event: CustomerCdcEvent): ValidationResult {
        if (event.isDelete()) {
            return ValidationResult.success(ruleId, "Delete events bypass business rules")
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        event.status?.let { status ->
            if (status !in VALID_STATUSES) {
                errors.add("Invalid status: $status. Must be one of: $VALID_STATUSES")
            }
        }

        event.email?.let { email ->
            if (email.endsWith("@test.com") || email.endsWith("@example.com")) {
                warnings.add("Email appears to be a test address: $email")
            }
        }

        return if (errors.isEmpty()) {
            val message = if (warnings.isEmpty()) {
                "Business validation passed"
            } else {
                "Business validation passed with warnings: ${warnings.joinToString()}"
            }
            ValidationResult.success(ruleId, message)
        } else {
            ValidationResult.failure(
                ruleId = ruleId,
                message = "Business validation failed",
                details = mapOf("errors" to errors, "warnings" to warnings)
            )
        }
    }
}
