package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import org.springframework.stereotype.Component

/**
 * Validates CreateProductCommand.
 * Implements AC9 field-level validation rules using externalized configuration.
 *
 * AC9 Requirements:
 * - Product name is required and between 1-255 characters
 * - Product SKU is required, unique, and follows defined format (alphanumeric, 3-50 chars)
 * - Product price must be a positive integer (cents)
 * - Product description is optional but limited to 5000 characters
 */
@Component
class CreateProductCommandValidator(
    private val businessRulesConfig: BusinessRulesConfig
) {
    private val skuPattern by lazy { Regex(businessRulesConfig.skuPattern) }

    fun validate(command: CreateProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // SKU validation (AC9: required, unique, alphanumeric, 3-50 chars)
        val trimmedSku = command.sku.trim()
        when {
            trimmedSku.isBlank() -> errors.add(
                ValidationError("sku", "SKU is required", ValidationErrorCode.REQUIRED.name)
            )
            trimmedSku.length < businessRulesConfig.minSkuLength -> errors.add(
                ValidationError(
                    "sku",
                    "SKU must be at least ${businessRulesConfig.minSkuLength} characters",
                    ValidationErrorCode.MIN_LENGTH.name
                )
            )
            trimmedSku.length > businessRulesConfig.maxSkuLength -> errors.add(
                ValidationError(
                    "sku",
                    "SKU must not exceed ${businessRulesConfig.maxSkuLength} characters",
                    ValidationErrorCode.MAX_LENGTH.name
                )
            )
            !skuPattern.matches(trimmedSku) -> errors.add(
                ValidationError(
                    "sku",
                    "SKU must contain only alphanumeric characters and hyphens",
                    ValidationErrorCode.INVALID_FORMAT.name
                )
            )
        }

        // Name validation (AC9: required, 1-255 chars)
        val trimmedName = command.name.trim()
        when {
            trimmedName.isBlank() -> errors.add(
                ValidationError("name", "Name is required", ValidationErrorCode.REQUIRED.name)
            )
            trimmedName.length > businessRulesConfig.maxNameLength -> errors.add(
                ValidationError(
                    "name",
                    "Name must not exceed ${businessRulesConfig.maxNameLength} characters",
                    ValidationErrorCode.MAX_LENGTH.name
                )
            )
        }

        // Description validation (AC9: optional, max 5000 chars)
        command.description?.let { desc ->
            if (desc.length > businessRulesConfig.maxDescriptionLength) {
                errors.add(
                    ValidationError(
                        "description",
                        "Description must not exceed ${businessRulesConfig.maxDescriptionLength} characters",
                        ValidationErrorCode.MAX_LENGTH.name
                    )
                )
            }
        }

        // Price validation (AC9: positive integer)
        if (command.priceCents <= 0) {
            errors.add(
                ValidationError(
                    "priceCents",
                    "Price must be a positive integer (cents)",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates UpdateProductCommand.
 * Implements AC9 field-level validation rules.
 */
@Component
class UpdateProductCommandValidator(
    private val businessRulesConfig: BusinessRulesConfig
) {
    fun validate(command: UpdateProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Name validation (AC9: required, 1-255 chars)
        val trimmedName = command.name.trim()
        when {
            trimmedName.isBlank() -> errors.add(
                ValidationError("name", "Name is required", ValidationErrorCode.REQUIRED.name)
            )
            trimmedName.length > businessRulesConfig.maxNameLength -> errors.add(
                ValidationError(
                    "name",
                    "Name must not exceed ${businessRulesConfig.maxNameLength} characters",
                    ValidationErrorCode.MAX_LENGTH.name
                )
            )
        }

        // Description validation (AC9: optional, max 5000 chars)
        command.description?.let { desc ->
            if (desc.length > businessRulesConfig.maxDescriptionLength) {
                errors.add(
                    ValidationError(
                        "description",
                        "Description must not exceed ${businessRulesConfig.maxDescriptionLength} characters",
                        ValidationErrorCode.MAX_LENGTH.name
                    )
                )
            }
        }

        // Version validation
        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError(
                    "expectedVersion",
                    "Expected version must be positive",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates ChangePriceCommand.
 * Implements AC9 price validation rules.
 */
@Component
class ChangePriceCommandValidator {
    fun validate(command: ChangePriceCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Price validation (AC9: positive integer)
        if (command.newPriceCents <= 0) {
            errors.add(
                ValidationError(
                    "newPriceCents",
                    "Price must be a positive integer (cents)",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
            )
        }

        // Version validation
        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError(
                    "expectedVersion",
                    "Expected version must be positive",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
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
    fun validate(command: ActivateProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Version validation
        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError(
                    "expectedVersion",
                    "Expected version must be positive",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates DiscontinueProductCommand.
 */
@Component
class DiscontinueProductCommandValidator(
    private val businessRulesConfig: BusinessRulesConfig
) {
    fun validate(command: DiscontinueProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Reason validation
        command.reason?.let { reason ->
            if (reason.length > businessRulesConfig.maxReasonLength) {
                errors.add(
                    ValidationError(
                        "reason",
                        "Reason must not exceed ${businessRulesConfig.maxReasonLength} characters",
                        ValidationErrorCode.MAX_LENGTH.name
                    )
                )
            }
        }

        // Version validation
        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError(
                    "expectedVersion",
                    "Expected version must be positive",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Validates DeleteProductCommand.
 */
@Component
class DeleteProductCommandValidator(
    private val businessRulesConfig: BusinessRulesConfig
) {
    fun validate(command: DeleteProductCommand): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // DeletedBy validation
        command.deletedBy?.let { deletedBy ->
            if (deletedBy.length > businessRulesConfig.maxDeletedByLength) {
                errors.add(
                    ValidationError(
                        "deletedBy",
                        "DeletedBy must not exceed ${businessRulesConfig.maxDeletedByLength} characters",
                        ValidationErrorCode.MAX_LENGTH.name
                    )
                )
            }
        }

        // Version validation
        if (command.expectedVersion < 1) {
            errors.add(
                ValidationError(
                    "expectedVersion",
                    "Expected version must be positive",
                    ValidationErrorCode.POSITIVE_REQUIRED.name
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
