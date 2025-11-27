package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("Command Validators")
class CommandValidatorsTest {

    @Nested
    @DisplayName("CreateProductCommandValidator")
    inner class CreateProductCommandValidatorTests {

        private val validator = CreateProductCommandValidator()

        @Test
        @DisplayName("should pass for valid command")
        fun shouldPassForValidCommand() {
            val command = CreateProductCommand(
                sku = "PROD-001",
                name = "Test Product",
                description = "A test product",
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should pass for command with null description")
        fun shouldPassForCommandWithNullDescription() {
            val command = CreateProductCommand(
                sku = "PROD-001",
                name = "Test Product",
                description = null,
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should fail for empty SKU")
        fun shouldFailForEmptySku() {
            val command = CreateProductCommand(
                sku = "",
                name = "Test Product",
                description = null,
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "sku" && it.code == "REQUIRED" })
        }

        @Test
        @DisplayName("should fail for blank SKU")
        fun shouldFailForBlankSku() {
            val command = CreateProductCommand(
                sku = "   ",
                name = "Test Product",
                description = null,
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "sku" })
        }

        @Test
        @DisplayName("should fail for SKU that is too short")
        fun shouldFailForSkuTooShort() {
            val command = CreateProductCommand(
                sku = "AB",
                name = "Test Product",
                description = null,
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "sku" && it.code == "MIN_LENGTH" })
        }

        @Test
        @DisplayName("should fail for SKU with invalid characters")
        fun shouldFailForSkuWithInvalidCharacters() {
            val command = CreateProductCommand(
                sku = "PROD@001!",
                name = "Test Product",
                description = null,
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "sku" && it.code == "INVALID_FORMAT" })
        }

        @Test
        @DisplayName("should fail for empty name")
        fun shouldFailForEmptyName() {
            val command = CreateProductCommand(
                sku = "PROD-001",
                name = "",
                description = null,
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "name" && it.code == "REQUIRED" })
        }

        @Test
        @DisplayName("should fail for non-positive price")
        fun shouldFailForNonPositivePrice() {
            val command = CreateProductCommand(
                sku = "PROD-001",
                name = "Test Product",
                description = null,
                priceCents = 0
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "priceCents" && it.code == "POSITIVE_REQUIRED" })
        }

        @Test
        @DisplayName("should fail for negative price")
        fun shouldFailForNegativePrice() {
            val command = CreateProductCommand(
                sku = "PROD-001",
                name = "Test Product",
                description = null,
                priceCents = -100
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "priceCents" })
        }

        @Test
        @DisplayName("should collect multiple validation errors")
        fun shouldCollectMultipleValidationErrors() {
            val command = CreateProductCommand(
                sku = "",
                name = "",
                description = null,
                priceCents = -100
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.size >= 3)
        }

        @Test
        @DisplayName("should fail for description exceeding max length")
        fun shouldFailForDescriptionExceedingMaxLength() {
            val command = CreateProductCommand(
                sku = "PROD-001",
                name = "Test Product",
                description = "a".repeat(5001),
                priceCents = 1999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "description" && it.code == "MAX_LENGTH" })
        }
    }

    @Nested
    @DisplayName("UpdateProductCommandValidator")
    inner class UpdateProductCommandValidatorTests {

        private val validator = UpdateProductCommandValidator()

        @Test
        @DisplayName("should pass for valid command")
        fun shouldPassForValidCommand() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                name = "Updated Name",
                description = "Updated description"
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should pass for command with null description")
        fun shouldPassForCommandWithNullDescription() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                name = "Updated Name",
                description = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should fail for empty name")
        fun shouldFailForEmptyName() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                name = "",
                description = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "name" })
        }

        @Test
        @DisplayName("should fail for non-positive version")
        fun shouldFailForNonPositiveVersion() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 0L,
                name = "Updated Name",
                description = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "expectedVersion" })
        }

        @Test
        @DisplayName("should fail for negative version")
        fun shouldFailForNegativeVersion() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = -1L,
                name = "Updated Name",
                description = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "expectedVersion" })
        }
    }

    @Nested
    @DisplayName("ChangePriceCommandValidator")
    inner class ChangePriceCommandValidatorTests {

        private val validator = ChangePriceCommandValidator()

        @Test
        @DisplayName("should pass for valid command")
        fun shouldPassForValidCommand() {
            val command = ChangePriceCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                newPriceCents = 2999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should fail for zero price")
        fun shouldFailForZeroPrice() {
            val command = ChangePriceCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                newPriceCents = 0
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "newPriceCents" })
        }

        @Test
        @DisplayName("should fail for negative price")
        fun shouldFailForNegativePrice() {
            val command = ChangePriceCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                newPriceCents = -100
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "newPriceCents" })
        }

        @Test
        @DisplayName("should fail for non-positive version")
        fun shouldFailForNonPositiveVersion() {
            val command = ChangePriceCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 0L,
                newPriceCents = 2999
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "expectedVersion" })
        }
    }

    @Nested
    @DisplayName("ActivateProductCommandValidator")
    inner class ActivateProductCommandValidatorTests {

        private val validator = ActivateProductCommandValidator()

        @Test
        @DisplayName("should pass for valid command")
        fun shouldPassForValidCommand() {
            val command = ActivateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should fail for non-positive version")
        fun shouldFailForNonPositiveVersion() {
            val command = ActivateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 0L
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "expectedVersion" })
        }
    }

    @Nested
    @DisplayName("DiscontinueProductCommandValidator")
    inner class DiscontinueProductCommandValidatorTests {

        private val validator = DiscontinueProductCommandValidator()

        @Test
        @DisplayName("should pass for valid command")
        fun shouldPassForValidCommand() {
            val command = DiscontinueProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                reason = "No longer manufactured"
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should pass for command with null reason")
        fun shouldPassForCommandWithNullReason() {
            val command = DiscontinueProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                reason = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should fail for reason exceeding max length")
        fun shouldFailForReasonExceedingMaxLength() {
            val command = DiscontinueProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                reason = "a".repeat(501)
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "reason" && it.code == "MAX_LENGTH" })
        }

        @Test
        @DisplayName("should fail for non-positive version")
        fun shouldFailForNonPositiveVersion() {
            val command = DiscontinueProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 0L,
                reason = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "expectedVersion" })
        }
    }

    @Nested
    @DisplayName("DeleteProductCommandValidator")
    inner class DeleteProductCommandValidatorTests {

        private val validator = DeleteProductCommandValidator()

        @Test
        @DisplayName("should pass for valid command")
        fun shouldPassForValidCommand() {
            val command = DeleteProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                deletedBy = "admin@example.com"
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should pass for command with null deletedBy")
        fun shouldPassForCommandWithNullDeletedBy() {
            val command = DeleteProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                deletedBy = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("should fail for deletedBy exceeding max length")
        fun shouldFailForDeletedByExceedingMaxLength() {
            val command = DeleteProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                deletedBy = "a".repeat(256)
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "deletedBy" && it.code == "MAX_LENGTH" })
        }

        @Test
        @DisplayName("should fail for non-positive version")
        fun shouldFailForNonPositiveVersion() {
            val command = DeleteProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 0L,
                deletedBy = null
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.field == "expectedVersion" })
        }
    }
}
