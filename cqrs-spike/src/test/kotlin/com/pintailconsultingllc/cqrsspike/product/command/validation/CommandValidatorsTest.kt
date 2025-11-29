package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for Command Validators.
 *
 * AC9 Requirements tested:
 * - Product name is required and between 1-255 characters
 * - Product SKU is required, unique, and follows defined format (alphanumeric, 3-50 chars)
 * - Product price must be a positive integer (cents)
 * - Product description is optional but limited to 5000 characters
 */
@DisplayName("Command Validators - AC9 Business Rules")
class CommandValidatorsTest {

    private lateinit var businessRulesConfig: BusinessRulesConfig

    @BeforeEach
    fun setup() {
        businessRulesConfig = BusinessRulesConfig()
    }

    @Nested
    @DisplayName("CreateProductCommandValidator")
    inner class CreateProductCommandValidatorTests {

        private lateinit var validator: CreateProductCommandValidator

        @BeforeEach
        fun setup() {
            validator = CreateProductCommandValidator(businessRulesConfig)
        }

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

        @Nested
        @DisplayName("AC9: Product name is required and between 1-255 characters")
        inner class NameValidation {

            @Test
            @DisplayName("should fail for empty name with REQUIRED error")
            fun shouldFailForEmptyName() {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "",
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "name" && it.code == ValidationErrorCode.REQUIRED.name })
            }

            @Test
            @DisplayName("should fail for name exceeding 255 characters with MAX_LENGTH error")
            fun shouldFailForNameTooLong() {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "a".repeat(256),
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "name" && it.code == ValidationErrorCode.MAX_LENGTH.name })
            }

            @Test
            @DisplayName("should pass for name exactly 255 characters")
            fun shouldPassForNameAtLimit() {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "a".repeat(255),
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Valid>(result)
            }
        }

        @Nested
        @DisplayName("AC9: Product SKU is required, alphanumeric, 3-50 chars")
        inner class SkuValidation {

            @Test
            @DisplayName("should fail for empty SKU with REQUIRED error")
            fun shouldFailForEmptySku() {
                val command = CreateProductCommand(
                    sku = "",
                    name = "Test Product",
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "sku" && it.code == ValidationErrorCode.REQUIRED.name })
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

            @ParameterizedTest
            @ValueSource(strings = ["AB", "A"])
            @DisplayName("should fail for SKU less than 3 characters with MIN_LENGTH error")
            fun shouldFailForSkuTooShort(sku: String) {
                val command = CreateProductCommand(
                    sku = sku,
                    name = "Test Product",
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "sku" && it.code == ValidationErrorCode.MIN_LENGTH.name })
            }

            @Test
            @DisplayName("should fail for SKU exceeding 50 characters with MAX_LENGTH error")
            fun shouldFailForSkuTooLong() {
                val command = CreateProductCommand(
                    sku = "A".repeat(51),
                    name = "Test Product",
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "sku" && it.code == ValidationErrorCode.MAX_LENGTH.name })
            }

            @ParameterizedTest
            @ValueSource(strings = ["PROD@001", "PROD 001", "PROD#001", "PROD.001"])
            @DisplayName("should fail for SKU with invalid characters with INVALID_FORMAT error")
            fun shouldFailForSkuWithInvalidCharacters(sku: String) {
                val command = CreateProductCommand(
                    sku = sku,
                    name = "Test Product",
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "sku" && it.code == ValidationErrorCode.INVALID_FORMAT.name })
            }

            @ParameterizedTest
            @ValueSource(strings = ["ABC", "PROD-001", "PRODUCT123", "A-B-C-123"])
            @DisplayName("should pass for valid SKU formats")
            fun shouldPassForValidSkuFormats(sku: String) {
                val command = CreateProductCommand(
                    sku = sku,
                    name = "Test Product",
                    description = null,
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Valid>(result)
            }
        }

        @Nested
        @DisplayName("AC9: Product price must be a positive integer (cents)")
        inner class PriceValidation {

            @ParameterizedTest
            @ValueSource(ints = [0, -1, -100])
            @DisplayName("should fail for non-positive price with POSITIVE_REQUIRED error")
            fun shouldFailForNonPositivePrice(price: Int) {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "Test Product",
                    description = null,
                    priceCents = price
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "priceCents" && it.code == ValidationErrorCode.POSITIVE_REQUIRED.name })
            }

            @ParameterizedTest
            @ValueSource(ints = [1, 100, 9999, 1000000])
            @DisplayName("should pass for positive price")
            fun shouldPassForPositivePrice(price: Int) {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "Test Product",
                    description = null,
                    priceCents = price
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Valid>(result)
            }
        }

        @Nested
        @DisplayName("AC9: Product description is optional but limited to 5000 characters")
        inner class DescriptionValidation {

            @Test
            @DisplayName("should pass for null description")
            fun shouldPassForNullDescription() {
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
            @DisplayName("should pass for empty description")
            fun shouldPassForEmptyDescription() {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "Test Product",
                    description = "",
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Valid>(result)
            }

            @Test
            @DisplayName("should fail for description exceeding 5000 characters with MAX_LENGTH error")
            fun shouldFailForDescriptionTooLong() {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "Test Product",
                    description = "a".repeat(5001),
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Invalid>(result)
                assertTrue(result.errors.any { it.field == "description" && it.code == ValidationErrorCode.MAX_LENGTH.name })
            }

            @Test
            @DisplayName("should pass for description exactly 5000 characters")
            fun shouldPassForDescriptionAtLimit() {
                val command = CreateProductCommand(
                    sku = "PROD-001",
                    name = "Test Product",
                    description = "a".repeat(5000),
                    priceCents = 1999
                )

                val result = validator.validate(command)

                assertIs<ValidationResult.Valid>(result)
            }
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
    }

    @Nested
    @DisplayName("UpdateProductCommandValidator")
    inner class UpdateProductCommandValidatorTests {

        private lateinit var validator: UpdateProductCommandValidator

        @BeforeEach
        fun setup() {
            validator = UpdateProductCommandValidator(businessRulesConfig)
        }

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

        @Test
        @DisplayName("should validate name and description rules together")
        fun shouldValidateNameAndDescriptionRules() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1,
                name = "",
                description = "a".repeat(5001)
            )

            val result = validator.validate(command)

            assertIs<ValidationResult.Invalid>(result)
            assertEquals(2, result.errors.size)
            assertTrue(result.errors.any { it.field == "name" })
            assertTrue(result.errors.any { it.field == "description" })
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

        private lateinit var validator: DiscontinueProductCommandValidator

        @BeforeEach
        fun setup() {
            validator = DiscontinueProductCommandValidator(businessRulesConfig)
        }

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
            assertTrue(result.errors.any { it.field == "reason" && it.code == ValidationErrorCode.MAX_LENGTH.name })
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

        private lateinit var validator: DeleteProductCommandValidator

        @BeforeEach
        fun setup() {
            validator = DeleteProductCommandValidator(businessRulesConfig)
        }

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
            assertTrue(result.errors.any { it.field == "deletedBy" && it.code == ValidationErrorCode.MAX_LENGTH.name })
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
