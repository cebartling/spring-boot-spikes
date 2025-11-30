package com.pintailconsultingllc.cqrsspike.product.command.validation

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.exception.InvalidStateTransitionException
import com.pintailconsultingllc.cqrsspike.product.command.exception.PriceChangeThresholdExceededException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductInvariantViolationException
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ProductAggregateRepository
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.StubEventStoreRepository
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for AC9 Business Rules.
 *
 * Tests validate the AC9 requirements:
 * - Product name is required and between 1-255 characters
 * - Product SKU is required, unique, and follows defined format (alphanumeric, 3-50 chars)
 * - Product price must be a positive integer (cents)
 * - Product description is optional but limited to 5000 characters
 * - Products in DRAFT status can be freely edited
 * - Products in ACTIVE status require confirmation for price changes over 20%
 * - Products in DISCONTINUED status cannot be reactivated
 * - Deleted products are soft-deleted and excluded from queries by default
 *
 * IMPORTANT: Before running these tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AC9 Business Rules Integration Tests")
class BusinessRulesIntegrationTest {

    @Autowired
    private lateinit var repository: ProductAggregateRepository

    @Autowired
    private lateinit var stubEventStore: StubEventStoreRepository

    @Autowired
    private lateinit var businessRulesConfig: BusinessRulesConfig

    @Autowired
    private lateinit var statusBasedValidator: StatusBasedValidator

    @BeforeEach
    fun setup() {
        stubEventStore.clear()
    }

    @Nested
    @DisplayName("AC9: Product name validation (1-255 characters)")
    inner class ProductNameValidation {

        @Test
        @DisplayName("should accept product with valid name")
        fun shouldAcceptValidName() {
            val aggregate = ProductAggregate.create(
                sku = "NAME-${UUID.randomUUID().toString().take(8)}",
                name = "Valid Product Name",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.name == "Valid Product Name" }
                .verifyComplete()
        }

        @Test
        @DisplayName("should accept product with name at maximum length (255)")
        fun shouldAcceptNameAtMaxLength() {
            val maxLengthName = "a".repeat(255)
            val aggregate = ProductAggregate.create(
                sku = "NMAX-${UUID.randomUUID().toString().take(8)}",
                name = maxLengthName,
                description = null,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.name.length == 255 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject product with name exceeding 255 characters")
        fun shouldRejectNameExceedingMaxLength() {
            val tooLongName = "a".repeat(256)

            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "NLONG",
                    name = tooLongName,
                    description = null,
                    priceCents = 999
                )
            }
        }

        @Test
        @DisplayName("should reject product with empty name")
        fun shouldRejectEmptyName() {
            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "NEMP",
                    name = "",
                    description = null,
                    priceCents = 999
                )
            }
        }
    }

    @Nested
    @DisplayName("AC9: Product SKU validation (alphanumeric, 3-50 chars)")
    inner class ProductSkuValidation {

        @Test
        @DisplayName("should accept product with valid SKU")
        fun shouldAcceptValidSku() {
            val aggregate = ProductAggregate.create(
                sku = "VALID-SKU-123",
                name = "Test Product",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.sku == "VALID-SKU-123" }
                .verifyComplete()
        }

        @Test
        @DisplayName("should accept product with SKU at minimum length (3)")
        fun shouldAcceptSkuAtMinLength() {
            val aggregate = ProductAggregate.create(
                sku = "ABC",
                name = "Min SKU Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.sku.length == 3 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject product with SKU less than 3 characters")
        fun shouldRejectSkuTooShort() {
            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "AB",
                    name = "Test",
                    description = null,
                    priceCents = 999
                )
            }
        }

        @Test
        @DisplayName("should reject product with SKU exceeding 50 characters")
        fun shouldRejectSkuTooLong() {
            val tooLongSku = "A".repeat(51)

            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = tooLongSku,
                    name = "Test",
                    description = null,
                    priceCents = 999
                )
            }
        }

        @Test
        @DisplayName("should reject product with SKU containing invalid characters")
        fun shouldRejectSkuWithInvalidCharacters() {
            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "INVALID@SKU",
                    name = "Test",
                    description = null,
                    priceCents = 999
                )
            }
        }
    }

    @Nested
    @DisplayName("AC9: Product price validation (positive integer)")
    inner class ProductPriceValidation {

        @Test
        @DisplayName("should accept product with positive price")
        fun shouldAcceptPositivePrice() {
            val aggregate = ProductAggregate.create(
                sku = "PRC-${UUID.randomUUID().toString().take(8)}",
                name = "Price Test",
                description = null,
                priceCents = 1999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.priceCents == 1999 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should accept product with price of 1 cent")
        fun shouldAcceptMinimumPrice() {
            val aggregate = ProductAggregate.create(
                sku = "PMIN-${UUID.randomUUID().toString().take(8)}",
                name = "Minimum Price",
                description = null,
                priceCents = 1
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.priceCents == 1 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject product with zero price")
        fun shouldRejectZeroPrice() {
            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "PZERO",
                    name = "Zero Price",
                    description = null,
                    priceCents = 0
                )
            }
        }

        @Test
        @DisplayName("should reject product with negative price")
        fun shouldRejectNegativePrice() {
            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "PNEG",
                    name = "Negative Price",
                    description = null,
                    priceCents = -100
                )
            }
        }
    }

    @Nested
    @DisplayName("AC9: Product description validation (optional, max 5000 chars)")
    inner class ProductDescriptionValidation {

        @Test
        @DisplayName("should accept product without description")
        fun shouldAcceptNullDescription() {
            val aggregate = ProductAggregate.create(
                sku = "DNUL-${UUID.randomUUID().toString().take(8)}",
                name = "No Description",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.description == null }
                .verifyComplete()
        }

        @Test
        @DisplayName("should accept product with description at maximum length")
        fun shouldAcceptDescriptionAtMaxLength() {
            val maxDescription = "a".repeat(5000)
            val aggregate = ProductAggregate.create(
                sku = "DMAX-${UUID.randomUUID().toString().take(8)}",
                name = "Max Description",
                description = maxDescription,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { it.description?.length == 5000 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject product with description exceeding 5000 characters")
        fun shouldRejectDescriptionTooLong() {
            val tooLongDescription = "a".repeat(5001)

            assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "DLONG",
                    name = "Long Description",
                    description = tooLongDescription,
                    priceCents = 999
                )
            }
        }
    }

    @Nested
    @DisplayName("AC9: DRAFT status - can be freely edited")
    inner class DraftStatusEditing {

        @Test
        @DisplayName("should allow name update for DRAFT product")
        fun shouldAllowNameUpdateForDraft() {
            val aggregate = ProductAggregate.create(
                sku = "DRUP-${UUID.randomUUID().toString().take(8)}",
                name = "Original Name",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val updated = saved.update(
                            newName = "Updated Name",
                            newDescription = "New description",
                            expectedVersion = saved.version
                        )
                        repository.update(updated)
                    }
            )
                .expectNextMatches { it.name == "Updated Name" }
                .verifyComplete()
        }

        @Test
        @DisplayName("should allow price change for DRAFT product without confirmation")
        fun shouldAllowPriceChangeForDraftWithoutConfirmation() {
            val aggregate = ProductAggregate.create(
                sku = "DRPC-${UUID.randomUUID().toString().take(8)}",
                name = "Draft Price Change",
                description = null,
                priceCents = 1000
            )

            // 50% price increase - should be allowed for DRAFT without confirmation
            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val updated = saved.changePrice(
                            newPriceCents = 1500, // 50% increase
                            expectedVersion = saved.version,
                            confirmLargeChange = false
                        )
                        repository.update(updated)
                    }
            )
                .expectNextMatches { it.priceCents == 1500 }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC9: ACTIVE status - price changes over 20% require confirmation")
    inner class ActiveStatusPriceConfirmation {

        @Test
        @DisplayName("should allow small price change for ACTIVE product without confirmation")
        fun shouldAllowSmallPriceChangeWithoutConfirmation() {
            val aggregate = ProductAggregate.create(
                sku = "ASPC-${UUID.randomUUID().toString().take(8)}",
                name = "Active Small Change",
                description = null,
                priceCents = 1000
            )

            // 15% increase - should be allowed without confirmation
            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val activated = saved.activate(expectedVersion = saved.version)
                        repository.update(activated)
                    }
                    .flatMap { activated ->
                        val priceChanged = activated.changePrice(
                            newPriceCents = 1150, // 15% increase
                            expectedVersion = activated.version,
                            confirmLargeChange = false
                        )
                        repository.update(priceChanged)
                    }
            )
                .expectNextMatches { it.priceCents == 1150 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject large price change for ACTIVE product without confirmation")
        fun shouldRejectLargePriceChangeWithoutConfirmation() {
            val aggregate = ProductAggregate.create(
                sku = "ALPC-${UUID.randomUUID().toString().take(8)}",
                name = "Active Large Change",
                description = null,
                priceCents = 1000
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val activated = saved.activate(expectedVersion = saved.version)
                        repository.update(activated)
                    }
                    .map { activated ->
                        // 50% increase - should require confirmation
                        activated.changePrice(
                            newPriceCents = 1500,
                            expectedVersion = activated.version,
                            confirmLargeChange = false
                        )
                    }
            )
                .expectError(PriceChangeThresholdExceededException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should allow large price change for ACTIVE product with confirmation")
        fun shouldAllowLargePriceChangeWithConfirmation() {
            val aggregate = ProductAggregate.create(
                sku = "ALCC-${UUID.randomUUID().toString().take(8)}",
                name = "Active Large Confirmed",
                description = null,
                priceCents = 1000
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val activated = saved.activate(expectedVersion = saved.version)
                        repository.update(activated)
                    }
                    .flatMap { activated ->
                        val priceChanged = activated.changePrice(
                            newPriceCents = 1500, // 50% increase
                            expectedVersion = activated.version,
                            confirmLargeChange = true // With confirmation
                        )
                        repository.update(priceChanged)
                    }
            )
                .expectNextMatches { it.priceCents == 1500 }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC9: DISCONTINUED status - cannot be reactivated")
    inner class DiscontinuedStatusReactivation {

        @Test
        @DisplayName("should allow transition from DRAFT to DISCONTINUED")
        fun shouldAllowDraftToDiscontinued() {
            val aggregate = ProductAggregate.create(
                sku = "DISC-${UUID.randomUUID().toString().take(8)}",
                name = "Discontinue Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val discontinued = saved.discontinue(
                            expectedVersion = saved.version,
                            reason = "Test discontinuation"
                        )
                        repository.update(discontinued)
                    }
            )
                .expectNextMatches { it.status == ProductStatus.DISCONTINUED }
                .verifyComplete()
        }

        @Test
        @DisplayName("should allow transition from ACTIVE to DISCONTINUED")
        fun shouldAllowActiveToDiscontinued() {
            val aggregate = ProductAggregate.create(
                sku = "ADIS-${UUID.randomUUID().toString().take(8)}",
                name = "Active Discontinue",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val activated = saved.activate(expectedVersion = saved.version)
                        repository.update(activated)
                    }
                    .flatMap { activated ->
                        val discontinued = activated.discontinue(
                            expectedVersion = activated.version,
                            reason = "End of life"
                        )
                        repository.update(discontinued)
                    }
            )
                .expectNextMatches { it.status == ProductStatus.DISCONTINUED }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject reactivation of DISCONTINUED product")
        fun shouldRejectReactivationOfDiscontinued() {
            val aggregate = ProductAggregate.create(
                sku = "REAC-${UUID.randomUUID().toString().take(8)}",
                name = "Reactivation Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val discontinued = saved.discontinue(
                            expectedVersion = saved.version,
                            reason = "Testing"
                        )
                        repository.update(discontinued)
                    }
                    .map { discontinued ->
                        // Attempt to reactivate - should fail
                        discontinued.activate(expectedVersion = discontinued.version)
                    }
            )
                .expectError(InvalidStateTransitionException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("AC9: Soft delete")
    inner class SoftDeleteBehavior {

        @Test
        @DisplayName("should soft delete product")
        fun shouldSoftDeleteProduct() {
            val aggregate = ProductAggregate.create(
                sku = "SDEL-${UUID.randomUUID().toString().take(8)}",
                name = "Soft Delete Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val deleted = saved.delete(
                            expectedVersion = saved.version,
                            deletedBy = "test@example.com"
                        )
                        repository.update(deleted)
                    }
            )
                .expectNextMatches { product ->
                    product.isDeleted && product.deletedAt != null
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("BusinessRulesConfig and StatusBasedValidator Integration")
    inner class ConfigAndValidatorIntegration {

        @Test
        @DisplayName("BusinessRulesConfig should be properly injected")
        fun businessRulesConfigShouldBeInjected() {
            assertEquals(255, businessRulesConfig.maxNameLength)
            assertEquals(1, businessRulesConfig.minNameLength)
            assertEquals(50, businessRulesConfig.maxSkuLength)
            assertEquals(3, businessRulesConfig.minSkuLength)
            assertEquals(5000, businessRulesConfig.maxDescriptionLength)
            assertEquals(20.0, businessRulesConfig.priceChangeThresholdPercent)
        }

        @Test
        @DisplayName("StatusBasedValidator should use BusinessRulesConfig")
        fun statusBasedValidatorShouldUseConfig() {
            assertEquals(20.0, statusBasedValidator.getPriceChangeThreshold())

            // Test that it correctly determines confirmation requirement
            assertTrue(statusBasedValidator.requiresPriceConfirmation(ProductStatus.ACTIVE, 1000, 1250))
            assertFalse(statusBasedValidator.requiresPriceConfirmation(ProductStatus.ACTIVE, 1000, 1150))
        }
    }
}
