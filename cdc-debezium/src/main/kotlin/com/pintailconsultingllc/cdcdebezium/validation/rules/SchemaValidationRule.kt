package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.validation.ValidationResult
import com.pintailconsultingllc.cdcdebezium.validation.ValidationRule
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class SchemaValidationRule : ValidationRule<CustomerCdcEvent> {

    override val ruleId = "SCHEMA_001"
    override val description = "Validates event conforms to expected schema"

    override fun validate(event: CustomerCdcEvent): ValidationResult {
        val errors = mutableListOf<String>()

        if (!event.isDelete()) {
            if (event.email.isNullOrBlank()) {
                errors.add("email is required for non-delete events")
            } else if (!isValidEmail(event.email)) {
                errors.add("email format is invalid: ${event.email}")
            }
            // status is optional - schema evolution allows missing fields
        }

        return if (errors.isEmpty()) {
            ValidationResult.success(ruleId, "Schema validation passed")
        } else {
            ValidationResult.failure(
                ruleId = ruleId,
                message = "Schema validation failed",
                details = mapOf("errors" to errors)
            )
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
}
