package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.dto.AddressCdcEvent
import com.pintailconsultingllc.cdcdebezium.validation.ValidationResult
import com.pintailconsultingllc.cdcdebezium.validation.ValidationRule
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class AddressValidationRule : ValidationRule<AddressCdcEvent> {

    override val ruleId = "ADDRESS_001"
    override val description = "Validates address event schema and business constraints"

    companion object {
        val VALID_TYPES = setOf("billing", "shipping", "home", "work")
        val US_POSTAL_CODE_REGEX = "^\\d{5}(-\\d{4})?$".toRegex()
        val CANADA_POSTAL_CODE_REGEX = "^[A-Za-z]\\d[A-Za-z][ -]?\\d[A-Za-z]\\d$".toRegex()
    }

    override fun validate(event: AddressCdcEvent): ValidationResult {
        if (event.isDelete()) {
            return ValidationResult.success(ruleId, "Delete events bypass address validation")
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (event.street.isNullOrBlank()) {
            errors.add("street is required for non-delete events")
        }

        if (event.city.isNullOrBlank()) {
            errors.add("city is required for non-delete events")
        }

        if (event.postalCode.isNullOrBlank()) {
            errors.add("postalCode is required for non-delete events")
        } else {
            validatePostalCode(event.postalCode, event.country, errors, warnings)
        }

        event.type?.let { type ->
            if (type !in VALID_TYPES) {
                errors.add("Invalid address type: $type. Must be one of: $VALID_TYPES")
            }
        } ?: errors.add("type is required for non-delete events")

        return if (errors.isEmpty()) {
            val message = if (warnings.isEmpty()) {
                "Address validation passed"
            } else {
                "Address validation passed with warnings: ${warnings.joinToString()}"
            }
            ValidationResult.success(ruleId, message)
        } else {
            ValidationResult.failure(
                ruleId = ruleId,
                message = "Address validation failed",
                details = mapOf("errors" to errors, "warnings" to warnings)
            )
        }
    }

    private fun validatePostalCode(
        postalCode: String,
        country: String?,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        when (country?.uppercase()) {
            "USA", "US", null -> {
                if (!postalCode.matches(US_POSTAL_CODE_REGEX)) {
                    warnings.add("Postal code '$postalCode' may not be a valid US format (expected: 12345 or 12345-6789)")
                }
            }
            "CANADA", "CA" -> {
                if (!postalCode.matches(CANADA_POSTAL_CODE_REGEX)) {
                    warnings.add("Postal code '$postalCode' may not be a valid Canadian format (expected: A1A 1A1)")
                }
            }
        }
    }
}
