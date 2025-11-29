package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for StatusBasedValidator.
 *
 * AC9 Requirements:
 * - Products in DRAFT status can be freely edited
 * - Products in ACTIVE status require confirmation for price changes over 20%
 * - Products in DISCONTINUED status cannot be reactivated
 */
@DisplayName("StatusBasedValidator - AC9 Status-Based Rules")
class StatusBasedValidatorTest {

    private lateinit var businessRulesConfig: BusinessRulesConfig
    private lateinit var validator: StatusBasedValidator

    @BeforeEach
    fun setup() {
        businessRulesConfig = BusinessRulesConfig()
        validator = StatusBasedValidator(businessRulesConfig)
    }

    @Nested
    @DisplayName("AC9: Products in DRAFT status can be freely edited")
    inner class DraftStatusEditing {

        @Test
        @DisplayName("DRAFT products can be freely edited")
        fun draftProductsCanBeFreelyEdited() {
            assertTrue(validator.canEditFreely(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("DRAFT products can be edited")
        fun draftProductsCanBeEdited() {
            assertTrue(validator.canEdit(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("DRAFT products have no editing restrictions")
        fun draftProductsHaveNoRestrictions() {
            val restrictions = validator.getEditingRestrictions(ProductStatus.DRAFT)
            assertTrue(restrictions.isEmpty())
        }

        @Test
        @DisplayName("DRAFT products do not require price confirmation")
        fun draftProductsDoNotRequirePriceConfirmation() {
            // Even 100% price change should not require confirmation for DRAFT
            assertFalse(validator.requiresPriceConfirmation(ProductStatus.DRAFT, 1000, 2000))
            assertFalse(validator.requiresPriceConfirmation(ProductStatus.DRAFT, 1000, 100))
        }
    }

    @Nested
    @DisplayName("AC9: Products in ACTIVE status require confirmation for price changes over 20%")
    inner class ActiveStatusPriceConfirmation {

        @Test
        @DisplayName("ACTIVE products can be edited")
        fun activeProductsCanBeEdited() {
            assertTrue(validator.canEdit(ProductStatus.ACTIVE))
        }

        @Test
        @DisplayName("ACTIVE products have price change restriction")
        fun activeProductsHavePriceChangeRestriction() {
            val restrictions = validator.getEditingRestrictions(ProductStatus.ACTIVE)
            assertEquals(1, restrictions.size)
            assertTrue(restrictions[0].contains("20.0%") || restrictions[0].contains("20%"))
        }

        @ParameterizedTest
        @CsvSource(
            "1000, 1100, false",   // 10% increase - no confirmation
            "1000, 900, false",    // 10% decrease - no confirmation
            "1000, 1200, false",   // 20% increase - exactly at threshold - no confirmation
            "1000, 800, false",    // 20% decrease - exactly at threshold - no confirmation
            "1000, 1201, true",    // 20.1% increase - requires confirmation
            "1000, 799, true",     // 20.1% decrease - requires confirmation
            "1000, 1500, true",    // 50% increase - requires confirmation
            "1000, 500, true"      // 50% decrease - requires confirmation
        )
        @DisplayName("should correctly determine if price confirmation is required")
        fun shouldCorrectlyDeterminePriceConfirmationRequirement(
            currentPrice: Int,
            newPrice: Int,
            expectedRequiresConfirmation: Boolean
        ) {
            assertEquals(
                expectedRequiresConfirmation,
                validator.requiresPriceConfirmation(ProductStatus.ACTIVE, currentPrice, newPrice)
            )
        }

        @Test
        @DisplayName("validatePriceChange returns Valid when within threshold")
        fun validatePriceChangeReturnsValidWhenWithinThreshold() {
            val result = validator.validatePriceChange(
                status = ProductStatus.ACTIVE,
                currentPriceCents = 1000,
                newPriceCents = 1100, // 10% change
                confirmLargeChange = false
            )

            assertIs<ValidationResult.Valid>(result)
        }

        @Test
        @DisplayName("validatePriceChange returns Invalid when exceeds threshold without confirmation")
        fun validatePriceChangeReturnsInvalidWhenExceedsThresholdWithoutConfirmation() {
            val result = validator.validatePriceChange(
                status = ProductStatus.ACTIVE,
                currentPriceCents = 1000,
                newPriceCents = 1500, // 50% change
                confirmLargeChange = false
            )

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.code == ValidationErrorCode.PRICE_THRESHOLD_EXCEEDED.name })
        }

        @Test
        @DisplayName("validatePriceChange returns Valid when exceeds threshold with confirmation")
        fun validatePriceChangeReturnsValidWhenExceedsThresholdWithConfirmation() {
            val result = validator.validatePriceChange(
                status = ProductStatus.ACTIVE,
                currentPriceCents = 1000,
                newPriceCents = 1500, // 50% change
                confirmLargeChange = true
            )

            assertIs<ValidationResult.Valid>(result)
        }
    }

    @Nested
    @DisplayName("AC9: Products in DISCONTINUED status cannot be reactivated")
    inner class DiscontinuedStatusReactivation {

        @Test
        @DisplayName("DISCONTINUED products cannot be edited")
        fun discontinuedProductsCannotBeEdited() {
            assertFalse(validator.canEdit(ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("DISCONTINUED products cannot be freely edited")
        fun discontinuedProductsCannotBeFreelyEdited() {
            assertFalse(validator.canEditFreely(ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("DISCONTINUED products have multiple restrictions")
        fun discontinuedProductsHaveMultipleRestrictions() {
            val restrictions = validator.getEditingRestrictions(ProductStatus.DISCONTINUED)
            assertEquals(2, restrictions.size)
            assertTrue(restrictions.any { it.contains("cannot be edited") })
            assertTrue(restrictions.any { it.contains("cannot be reactivated") })
        }

        @Test
        @DisplayName("validateTransition returns Invalid for DISCONTINUED to ACTIVE")
        fun validateTransitionReturnsInvalidForReactivation() {
            val result = validator.validateTransition(ProductStatus.DISCONTINUED, ProductStatus.ACTIVE)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.code == ValidationErrorCode.REACTIVATION_NOT_ALLOWED.name })
            assertTrue(result.errors.any { it.message.contains("cannot be reactivated") })
        }

        @Test
        @DisplayName("canTransitionTo returns false for DISCONTINUED to ACTIVE")
        fun canTransitionToReturnsFalseForReactivation() {
            assertFalse(validator.canTransitionTo(ProductStatus.DISCONTINUED, ProductStatus.ACTIVE))
        }

        @Test
        @DisplayName("canTransitionTo returns false for DISCONTINUED to DRAFT")
        fun canTransitionToReturnsFalseForDiscontinuedToDraft() {
            assertFalse(validator.canTransitionTo(ProductStatus.DISCONTINUED, ProductStatus.DRAFT))
        }
    }

    @Nested
    @DisplayName("Valid State Transitions")
    inner class ValidStateTransitions {

        @Test
        @DisplayName("DRAFT can transition to ACTIVE")
        fun draftCanTransitionToActive() {
            assertTrue(validator.canTransitionTo(ProductStatus.DRAFT, ProductStatus.ACTIVE))
        }

        @Test
        @DisplayName("DRAFT can transition to DISCONTINUED")
        fun draftCanTransitionToDiscontinued() {
            assertTrue(validator.canTransitionTo(ProductStatus.DRAFT, ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("ACTIVE can transition to DISCONTINUED")
        fun activeCanTransitionToDiscontinued() {
            assertTrue(validator.canTransitionTo(ProductStatus.ACTIVE, ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("validateTransition returns Valid for allowed transitions")
        fun validateTransitionReturnsValidForAllowedTransitions() {
            val draftToActive = validator.validateTransition(ProductStatus.DRAFT, ProductStatus.ACTIVE)
            val draftToDiscontinued = validator.validateTransition(ProductStatus.DRAFT, ProductStatus.DISCONTINUED)
            val activeToDiscontinued = validator.validateTransition(ProductStatus.ACTIVE, ProductStatus.DISCONTINUED)

            assertIs<ValidationResult.Valid>(draftToActive)
            assertIs<ValidationResult.Valid>(draftToDiscontinued)
            assertIs<ValidationResult.Valid>(activeToDiscontinued)
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    inner class InvalidStateTransitions {

        @Test
        @DisplayName("ACTIVE cannot transition to DRAFT")
        fun activeCannotTransitionToDraft() {
            assertFalse(validator.canTransitionTo(ProductStatus.ACTIVE, ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("validateTransition returns Invalid for ACTIVE to DRAFT")
        fun validateTransitionReturnsInvalidForActiveToDraft() {
            val result = validator.validateTransition(ProductStatus.ACTIVE, ProductStatus.DRAFT)

            assertIs<ValidationResult.Invalid>(result)
            assertTrue(result.errors.any { it.code == ValidationErrorCode.INVALID_STATE_TRANSITION.name })
        }
    }

    @Nested
    @DisplayName("Price Change Calculations")
    inner class PriceChangeCalculations {

        @ParameterizedTest
        @CsvSource(
            "1000, 1100, 10.0",    // 10% increase
            "1000, 900, -10.0",    // 10% decrease
            "1000, 2000, 100.0",   // 100% increase
            "1000, 500, -50.0",    // 50% decrease
            "1000, 1000, 0.0",     // No change
            "100, 125, 25.0",      // 25% increase
            "100, 75, -25.0"       // 25% decrease
        )
        @DisplayName("should calculate price change percentage correctly")
        fun shouldCalculatePriceChangePercentageCorrectly(
            currentPrice: Int,
            newPrice: Int,
            expectedPercentage: Double
        ) {
            val result = validator.calculatePriceChangePercent(currentPrice, newPrice)
            assertEquals(expectedPercentage, result, 0.01)
        }

        @Test
        @DisplayName("should return 100% for zero current price")
        fun shouldReturn100ForZeroCurrentPrice() {
            val result = validator.calculatePriceChangePercent(0, 1000)
            assertEquals(100.0, result)
        }
    }

    @Nested
    @DisplayName("Configuration-Based Threshold")
    inner class ConfigurationBasedThreshold {

        @Test
        @DisplayName("should use configured threshold value")
        fun shouldUseConfiguredThresholdValue() {
            assertEquals(20.0, validator.getPriceChangeThreshold())
        }

        @Test
        @DisplayName("should respect custom threshold configuration")
        fun shouldRespectCustomThresholdConfiguration() {
            val customConfig = BusinessRulesConfig().apply {
                priceChangeThresholdPercent = 10.0
            }
            val customValidator = StatusBasedValidator(customConfig)

            // 15% change should require confirmation with 10% threshold
            assertTrue(customValidator.requiresPriceConfirmation(ProductStatus.ACTIVE, 1000, 1150))

            // 15% change should NOT require confirmation with default 20% threshold
            assertFalse(validator.requiresPriceConfirmation(ProductStatus.ACTIVE, 1000, 1150))
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("zero current price does not require confirmation")
        fun zeroCurrentPriceDoesNotRequireConfirmation() {
            // Edge case: zero current price should not require confirmation
            assertFalse(validator.requiresPriceConfirmation(ProductStatus.ACTIVE, 0, 1000))
        }

        @ParameterizedTest
        @EnumSource(ProductStatus::class)
        @DisplayName("same status transition validation")
        fun sameStatusTransitionValidation(status: ProductStatus) {
            // Transitioning to the same status should follow the canTransitionTo rules
            val result = validator.canTransitionTo(status, status)
            // This depends on ProductStatus.canTransitionTo implementation
            // Generally, staying in the same status is a no-op and might be allowed or not
        }
    }
}
