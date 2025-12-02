package com.pintailconsultingllc.cqrsspike.product.command.handler

import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandAlreadyProcessed
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandSuccess
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.validation.CommandValidationException
import com.pintailconsultingllc.cqrsspike.testutil.TestDatabaseCleanup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Integration tests for ProductCommandHandler with PostgreSQL.
 *
 * These tests verify the complete command handling flow including:
 * - Validation
 * - Idempotency
 * - Aggregate operations
 * - Event persistence
 *
 * IMPORTANT: Before running these tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ProductCommandHandler Integration Tests")
class ProductCommandHandlerIntegrationTest {

    @Autowired
    private lateinit var commandHandler: ProductCommandHandler

    @Autowired
    private lateinit var testDatabaseCleanup: TestDatabaseCleanup

    @BeforeEach
    fun setUp() {
        // Clean all test data before each test for isolation
        testDatabaseCleanup.cleanAllTestData().block()
    }

    @Nested
    @DisplayName("CreateProductCommand")
    inner class CreateProductCommandTests {

        @Test
        @DisplayName("should create product successfully")
        fun shouldCreateProductSuccessfully() {
            val command = CreateProductCommand(
                sku = "CREATE-${UUID.randomUUID().toString().take(8)}",
                name = "Integration Test Product",
                description = "Test description",
                priceCents = 1999
            )

            StepVerifier.create(commandHandler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess &&
                    result.version == 1L &&
                    result.success
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should fail validation for invalid command")
        fun shouldFailValidationForInvalidCommand() {
            val command = CreateProductCommand(
                sku = "",
                name = "",
                description = null,
                priceCents = -100
            )

            StepVerifier.create(commandHandler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("Full Command Lifecycle")
    inner class FullCommandLifecycle {

        @Test
        @DisplayName("should execute full product lifecycle")
        fun shouldExecuteFullProductLifecycle() {
            val sku = "LIFECYCLE-${UUID.randomUUID().toString().take(8)}"

            // Step 1: Create product
            val createCommand = CreateProductCommand(
                sku = sku,
                name = "Lifecycle Test Product",
                description = "Testing full lifecycle",
                priceCents = 1999
            )

            val createResult = commandHandler.handle(createCommand).block() as CommandSuccess
            val productId = createResult.productId

            // Step 2: Update product
            val updateCommand = UpdateProductCommand(
                productId = productId,
                expectedVersion = 1L,
                name = "Updated Lifecycle Product",
                description = "Updated description"
            )

            StepVerifier.create(commandHandler.handle(updateCommand))
                .expectNextMatches { result ->
                    result is CommandSuccess && result.version == 2L
                }
                .verifyComplete()

            // Step 3: Change price
            val changePriceCommand = ChangePriceCommand(
                productId = productId,
                expectedVersion = 2L,
                newPriceCents = 2999
            )

            StepVerifier.create(commandHandler.handle(changePriceCommand))
                .expectNextMatches { result ->
                    result is CommandSuccess && result.version == 3L
                }
                .verifyComplete()

            // Step 4: Activate product
            val activateCommand = ActivateProductCommand(
                productId = productId,
                expectedVersion = 3L
            )

            StepVerifier.create(commandHandler.handle(activateCommand))
                .expectNextMatches { result ->
                    result is CommandSuccess && result.version == 4L
                }
                .verifyComplete()

            // Step 5: Delete product
            val deleteCommand = DeleteProductCommand(
                productId = productId,
                expectedVersion = 4L,
                deletedBy = "integration-test"
            )

            StepVerifier.create(commandHandler.handle(deleteCommand))
                .expectNextMatches { result ->
                    result is CommandSuccess && result.version == 5L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should discontinue product from draft")
        fun shouldDiscontinueProductFromDraft() {
            val sku = "DISCONTINUE-${UUID.randomUUID().toString().take(8)}"

            // Create product
            val createCommand = CreateProductCommand(
                sku = sku,
                name = "Discontinue Test Product",
                description = null,
                priceCents = 999
            )

            val createResult = commandHandler.handle(createCommand).block() as CommandSuccess

            // Discontinue product
            val discontinueCommand = DiscontinueProductCommand(
                productId = createResult.productId,
                expectedVersion = 1L,
                reason = "End of life"
            )

            StepVerifier.create(commandHandler.handle(discontinueCommand))
                .expectNextMatches { result ->
                    result is CommandSuccess && result.version == 2L
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class IdempotencyTests {

        @Test
        @DisplayName("should return same result for duplicate idempotent request")
        fun shouldReturnSameResultForDuplicateRequest() {
            val idempotencyKey = UUID.randomUUID().toString()
            val sku = "IDEMP-${UUID.randomUUID().toString().take(8)}"

            val command = CreateProductCommand(
                sku = sku,
                name = "Idempotency Test",
                description = null,
                priceCents = 999,
                idempotencyKey = idempotencyKey
            )

            // First request
            val firstResult = commandHandler.handle(command).block() as CommandSuccess

            // Second request with same idempotency key
            StepVerifier.create(commandHandler.handle(command))
                .expectNextMatches { result ->
                    result is CommandAlreadyProcessed &&
                    result.productId == firstResult.productId &&
                    result.idempotencyKey == idempotencyKey
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should process requests without idempotency key")
        fun shouldProcessRequestsWithoutIdempotencyKey() {
            val command = CreateProductCommand(
                sku = "NOKEY-${UUID.randomUUID().toString().take(8)}",
                name = "No Key Test",
                description = null,
                priceCents = 999,
                idempotencyKey = null
            )

            StepVerifier.create(commandHandler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("should reject invalid create command")
        fun shouldRejectInvalidCreateCommand() {
            val command = CreateProductCommand(
                sku = "",
                name = "",
                description = null,
                priceCents = -100
            )

            StepVerifier.create(commandHandler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should reject update with invalid name")
        fun shouldRejectUpdateWithInvalidName() {
            val sku = "VAL-${UUID.randomUUID().toString().take(8)}"

            // Create product first
            val createCommand = CreateProductCommand(
                sku = sku,
                name = "Validation Test",
                description = null,
                priceCents = 999
            )
            val createResult = commandHandler.handle(createCommand).block() as CommandSuccess

            // Try to update with empty name
            val updateCommand = UpdateProductCommand(
                productId = createResult.productId,
                expectedVersion = 1L,
                name = "",
                description = null
            )

            StepVerifier.create(commandHandler.handle(updateCommand))
                .expectError(CommandValidationException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should reject price change with non-positive price")
        fun shouldRejectPriceChangeWithNonPositivePrice() {
            val sku = "VALP-${UUID.randomUUID().toString().take(8)}"

            // Create product first
            val createCommand = CreateProductCommand(
                sku = sku,
                name = "Price Validation Test",
                description = null,
                priceCents = 999
            )
            val createResult = commandHandler.handle(createCommand).block() as CommandSuccess

            // Try to change price to zero
            val changePriceCommand = ChangePriceCommand(
                productId = createResult.productId,
                expectedVersion = 1L,
                newPriceCents = 0
            )

            StepVerifier.create(commandHandler.handle(changePriceCommand))
                .expectError(CommandValidationException::class.java)
                .verify()
        }
    }
}
