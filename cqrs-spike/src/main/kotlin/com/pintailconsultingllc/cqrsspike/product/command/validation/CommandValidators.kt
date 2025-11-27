package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import org.springframework.stereotype.Component

/**
 * Validates CreateProductCommand.
 */
@Component
class CreateProductCommandValidator {

    companion object {
        private val SKU_PATTERN = Regex("^[A-Za-z0-9\\-]{3,50}$")
    }

    fun validate(command: CreateProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // SKU validation
        val trimmedSku = command.sku.trim()
        when {
            trimmedSku.isBlank() -> errors.add(
                ValidationError("sku", "SKU is required", "REQUIRED")
            )
            trimmedSku.length < ProductAggregate.MIN_SKU_LENGTH -> errors.add(
                ValidationError("sku", "SKU must be at least ${ProductAggregate.MIN_SKU_LENGTH} characters", "MIN_LENGTH")
            )
            trimmedSku.length > ProductAggregate.MAX_SKU_LENGTH -> errors.add(
                ValidationError("sku", "SKU must not exceed ${ProductAggregate.MAX_SKU_LENGTH} characters", "MAX_LENGTH")
            )
            !SKU_PATTERN.matches(trimmedSku) -> errors.add(
                ValidationError("sku", "SKU must contain only alphanumeric characters and hyphens", "INVALID_FORMAT")
            )
        }

        // Name validation
        val trimmedName = command.name.trim()
        when {
            trimmedName.isBlank() -> errors.add(
                ValidationError("name", "Name is required", "REQUIRED")
            )
            trimmedName.length > ProductAggregate.MAX_NAME_LENGTH -> errors.add(
                ValidationError("name", "Name must not exceed ${ProductAggregate.MAX_NAME_LENGTH} characters", "MAX_LENGTH")
            )
        }

        // Description validation
        command.description?.let { desc ->
            if (desc.length > ProductAggregate.MAX_DESCRIPTION_LENGTH) {
                errors.add(
                    ValidationError("description", "Description must not exceed ${ProductAggregate.MAX_DESCRIPTION_LENGTH} characters", "MAX_LENGTH")
                )
            }
        }

        // Price validation
        if (command.priceCents <= 0) {
            errors.add(
                ValidationError("priceCents", "Price must be positive", "POSITIVE_REQUIRED")
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates UpdateProductCommand.
 */
@Component
class UpdateProductCommandValidator {

    fun validate(command: UpdateProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Name validation
        val trimmedName = command.name.trim()
        when {
            trimmedName.isBlank() -> errors.add(
                ValidationError("name", "Name is required", "REQUIRED")
            )
            trimmedName.length > ProductAggregate.MAX_NAME_LENGTH -> errors.add(
                ValidationError("name", "Name must not exceed ${ProductAggregate.MAX_NAME_LENGTH} characters", "MAX_LENGTH")
            )
        }

        // Description validation
        command.description?.let { desc ->
            if (desc.length > ProductAggregate.MAX_DESCRIPTION_LENGTH) {
                errors.add(
                    ValidationError("description", "Description must not exceed ${ProductAggregate.MAX_DESCRIPTION_LENGTH} characters", "MAX_LENGTH")
                )
            }
        }

        // Version validation
        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError("expectedVersion", "Expected version must be positive", "POSITIVE_REQUIRED")
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates ChangePriceCommand.
 */
@Component
class ChangePriceCommandValidator {

    fun validate(command: ChangePriceCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (command.newPriceCents <= 0) {
            errors.add(
                ValidationError("newPriceCents", "Price must be positive", "POSITIVE_REQUIRED")
            )
        }

        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError("expectedVersion", "Expected version must be positive", "POSITIVE_REQUIRED")
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates ActivateProductCommand.
 */
@Component
class ActivateProductCommandValidator {

    fun validate(command: com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError("expectedVersion", "Expected version must be positive", "POSITIVE_REQUIRED")
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates DiscontinueProductCommand.
 */
@Component
class DiscontinueProductCommandValidator {

    companion object {
        const val MAX_REASON_LENGTH = 500
    }

    fun validate(command: DiscontinueProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        command.reason?.let { reason ->
            if (reason.length > MAX_REASON_LENGTH) {
                errors.add(
                    ValidationError("reason", "Reason must not exceed $MAX_REASON_LENGTH characters", "MAX_LENGTH")
                )
            }
        }

        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError("expectedVersion", "Expected version must be positive", "POSITIVE_REQUIRED")
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates DeleteProductCommand.
 */
@Component
class DeleteProductCommandValidator {

    companion object {
        const val MAX_DELETED_BY_LENGTH = 255
    }

    fun validate(command: DeleteProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        command.deletedBy?.let { deletedBy ->
            if (deletedBy.length > MAX_DELETED_BY_LENGTH) {
                errors.add(
                    ValidationError("deletedBy", "DeletedBy must not exceed $MAX_DELETED_BY_LENGTH characters", "MAX_LENGTH")
                )
            }
        }

        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError("expectedVersion", "Expected version must be positive", "POSITIVE_REQUIRED")
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
