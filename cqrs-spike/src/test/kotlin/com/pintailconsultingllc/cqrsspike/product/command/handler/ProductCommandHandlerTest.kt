package com.pintailconsultingllc.cqrsspike.product.command.handler

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.exception.InvalidStateTransitionException
import com.pintailconsultingllc.cqrsspike.product.command.exception.PriceChangeThresholdExceededException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductDeletedException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductNotFoundException
import com.pintailconsultingllc.cqrsspike.testutil.builders.ProductAggregateBuilder
import com.pintailconsultingllc.cqrsspike.testutil.builders.ProductCommandBuilders
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ProductAggregateRepository
import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandAlreadyProcessed
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandSuccess
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.service.IdempotencyService
import com.pintailconsultingllc.cqrsspike.product.command.validation.ActivateProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.BusinessRulesConfig
import com.pintailconsultingllc.cqrsspike.product.command.validation.ChangePriceCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.CommandValidationException
import com.pintailconsultingllc.cqrsspike.product.command.validation.CreateProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.DeleteProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.DiscontinueProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.UpdateProductCommandValidator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("ProductCommandHandler")
class ProductCommandHandlerTest {

    @Mock
    private lateinit var aggregateRepository: ProductAggregateRepository

    @Mock
    private lateinit var idempotencyService: IdempotencyService

    private lateinit var handler: ProductCommandHandler

    companion object {
        const val VALID_SKU = "TEST-001"
        const val VALID_NAME = "Test Product"
        const val VALID_DESCRIPTION = "Test description"
        const val VALID_PRICE = 1999
    }

    @BeforeEach
    fun setUp() {
        val businessRulesConfig = BusinessRulesConfig()
        handler = ProductCommandHandler(
            aggregateRepository = aggregateRepository,
            idempotencyService = idempotencyService,
            createValidator = CreateProductCommandValidator(businessRulesConfig),
            updateValidator = UpdateProductCommandValidator(businessRulesConfig),
            changePriceValidator = ChangePriceCommandValidator(),
            activateValidator = ActivateProductCommandValidator(),
            discontinueValidator = DiscontinueProductCommandValidator(businessRulesConfig),
            deleteValidator = DeleteProductCommandValidator(businessRulesConfig)
        )
    }

    @Nested
    @DisplayName("CreateProductCommand")
    inner class CreateProductCommandTests {

        @Test
        @DisplayName("should create product successfully")
        fun shouldCreateProductSuccessfully() {
            val command = CreateProductCommand(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = VALID_DESCRIPTION,
                priceCents = VALID_PRICE
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.save(any()))
                .thenAnswer { invocation ->
                    val aggregate = invocation.getArgument<ProductAggregate>(0)
                    Mono.just(aggregate)
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess &&
                    result.version == 1L
                }
                .verifyComplete()

            verify(aggregateRepository).save(any())
        }

        @Test
        @DisplayName("should fail validation for empty SKU")
        fun shouldFailValidationForEmptySku() {
            val command = CreateProductCommand(
                sku = "",
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should fail validation for non-positive price")
        fun shouldFailValidationForNonPositivePrice() {
            val command = CreateProductCommand(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = 0
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should return cached result for idempotent request")
        fun shouldReturnCachedResultForIdempotentRequest() {
            val idempotencyKey = UUID.randomUUID().toString()
            val productId = UUID.randomUUID()

            val command = CreateProductCommand(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE,
                idempotencyKey = idempotencyKey
            )

            val cachedResult = CommandSuccess(
                productId = productId,
                version = 1L,
                timestamp = OffsetDateTime.now()
            )

            whenever(idempotencyService.checkIdempotency(idempotencyKey))
                .thenReturn(Mono.just(cachedResult))

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandAlreadyProcessed &&
                    result.productId == productId &&
                    result.idempotencyKey == idempotencyKey
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("UpdateProductCommand")
    inner class UpdateProductCommandTests {

        @Test
        @DisplayName("should update product successfully")
        fun shouldUpdateProductSuccessfully() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = VALID_DESCRIPTION,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = UpdateProductCommand(
                productId = productId,
                expectedVersion = 1L,
                name = "Updated Name",
                description = "Updated description"
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess &&
                    result.version == 2L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should fail when product not found")
        fun shouldFailWhenProductNotFound() {
            val productId = UUID.randomUUID()
            val command = UpdateProductCommand(
                productId = productId,
                expectedVersion = 1L,
                name = "Updated Name",
                description = null
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.error(ProductNotFoundException(productId)))

            StepVerifier.create(handler.handle(command))
                .expectError(ProductNotFoundException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should fail validation for empty name")
        fun shouldFailValidationForEmptyName() {
            val command = UpdateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                name = "",
                description = null
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("ChangePriceCommand")
    inner class ChangePriceCommandTests {

        @Test
        @DisplayName("should change price successfully")
        fun shouldChangePriceSuccessfully() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = ChangePriceCommand(
                productId = productId,
                expectedVersion = 1L,
                newPriceCents = 2999
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should fail validation for non-positive price")
        fun shouldFailValidationForNonPositivePrice() {
            val command = ChangePriceCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 1L,
                newPriceCents = 0
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("ActivateProductCommand")
    inner class ActivateProductCommandTests {

        @Test
        @DisplayName("should activate product successfully")
        fun shouldActivateProductSuccessfully() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = ActivateProductCommand(
                productId = productId,
                expectedVersion = 1L
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess &&
                    result.version == 2L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should fail validation for non-positive version")
        fun shouldFailValidationForNonPositiveVersion() {
            val command = ActivateProductCommand(
                productId = UUID.randomUUID(),
                expectedVersion = 0L
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectError(CommandValidationException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("DiscontinueProductCommand")
    inner class DiscontinueProductCommandTests {

        @Test
        @DisplayName("should discontinue product successfully")
        fun shouldDiscontinueProductSuccessfully() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = DiscontinueProductCommand(
                productId = productId,
                expectedVersion = 1L,
                reason = "No longer manufactured"
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should discontinue product without reason")
        fun shouldDiscontinueProductWithoutReason() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = DiscontinueProductCommand(
                productId = productId,
                expectedVersion = 1L,
                reason = null
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("DeleteProductCommand")
    inner class DeleteProductCommandTests {

        @Test
        @DisplayName("should delete product successfully")
        fun shouldDeleteProductSuccessfully() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = DeleteProductCommand(
                productId = productId,
                expectedVersion = 1L,
                deletedBy = "admin@example.com"
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should delete product without deletedBy")
        fun shouldDeleteProductWithoutDeletedBy() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )
            val productId = aggregate.id

            val command = DeleteProductCommand(
                productId = productId,
                expectedVersion = 1L,
                deletedBy = null
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }

            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should fail when product not found")
        fun shouldFailWhenProductNotFound() {
            val productId = UUID.randomUUID()
            val command = DeleteProductCommand(
                productId = productId,
                expectedVersion = 1L,
                deletedBy = null
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())

            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.error(ProductNotFoundException(productId)))

            StepVerifier.create(handler.handle(command))
                .expectError(ProductNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("AC12: Edge Cases - Concurrent Modification Handling")
    inner class ConcurrentModificationHandling {

        @Test
        @DisplayName("should propagate concurrent modification exception from repository")
        fun shouldPropagateConcurrentModificationException() {
            // Use draft product (version 1) so expectedVersion matches
            val aggregate = ProductAggregateBuilder.aDraftProduct().buildWithClearedEvents()
            val productId = aggregate.id

            val command = ProductCommandBuilders.updateProductCommand(
                productId = productId,
                expectedVersion = 1L
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))
            whenever(aggregateRepository.update(any()))
                .thenReturn(Mono.error(ConcurrentModificationException(productId, 1L, 3L)))

            StepVerifier.create(handler.handle(command))
                .expectError(ConcurrentModificationException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("AC12: Edge Cases - Business Rule Violations")
    inner class BusinessRuleViolations {

        @Test
        @DisplayName("should reject activation of already active product")
        fun shouldRejectActivationOfAlreadyActiveProduct() {
            val aggregate = ProductAggregateBuilder.anActiveProduct().buildWithClearedEvents()
            val productId = aggregate.id

            val command = ProductCommandBuilders.activateProductCommand(
                productId = productId,
                expectedVersion = aggregate.version
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            StepVerifier.create(handler.handle(command))
                .expectError(InvalidStateTransitionException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should reject operations on deleted product")
        fun shouldRejectOperationsOnDeletedProduct() {
            val aggregate = ProductAggregateBuilder.aDraftProduct()
                .buildWithClearedEvents()
                .delete(expectedVersion = 1L, deletedBy = "test")
            val productId = aggregate.id

            val command = ProductCommandBuilders.updateProductCommand(
                productId = productId,
                expectedVersion = aggregate.version
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            StepVerifier.create(handler.handle(command))
                .expectError(ProductDeletedException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should reject large price change without confirmation for active product")
        fun shouldRejectLargePriceChangeWithoutConfirmation() {
            val aggregate = ProductAggregateBuilder.anActiveProduct()
                .withPrice(1000)
                .buildWithClearedEvents()
            val productId = aggregate.id

            val command = ProductCommandBuilders.changePriceCommand(
                productId = productId,
                expectedVersion = aggregate.version,
                newPriceCents = 3000, // 200% increase - exceeds 20% threshold
                confirmLargeChange = false
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            StepVerifier.create(handler.handle(command))
                .expectError(PriceChangeThresholdExceededException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should allow large price change with confirmation for active product")
        fun shouldAllowLargePriceChangeWithConfirmation() {
            val aggregate = ProductAggregateBuilder.anActiveProduct()
                .withPrice(1000)
                .buildWithClearedEvents()
            val productId = aggregate.id

            val command = ProductCommandBuilders.changePriceCommand(
                productId = productId,
                expectedVersion = aggregate.version,
                newPriceCents = 3000, // 200% increase - exceeds 20% threshold
                confirmLargeChange = true // Confirmed
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))
            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }
            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject reactivation of discontinued product")
        fun shouldRejectReactivationOfDiscontinuedProduct() {
            val aggregate = ProductAggregateBuilder.aDiscontinuedProduct().buildWithClearedEvents()
            val productId = aggregate.id

            val command = ProductCommandBuilders.activateProductCommand(
                productId = productId,
                expectedVersion = aggregate.version
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))

            StepVerifier.create(handler.handle(command))
                .expectError(InvalidStateTransitionException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("AC12: Edge Cases - Using Test Builders")
    inner class TestBuilderUsageExamples {

        @Test
        @DisplayName("should create product using builder")
        fun shouldCreateProductUsingBuilder() {
            val command = ProductCommandBuilders.createProductCommand(
                sku = "BUILDER-TEST-001",
                name = "Builder Test Product",
                priceCents = 4999
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.save(any()))
                .thenAnswer { invocation ->
                    val aggregate = invocation.getArgument<ProductAggregate>(0)
                    Mono.just(aggregate)
                }
            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess &&
                    result.version == 1L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should update product using aggregate builder")
        fun shouldUpdateProductUsingAggregateBuilder() {
            val aggregate = ProductAggregateBuilder.aValidProduct()
                .withSku("UPDATE-TEST-001")
                .buildWithClearedEvents()
            val productId = aggregate.id

            val command = ProductCommandBuilders.updateProductCommand(
                productId = productId,
                expectedVersion = aggregate.version,
                name = "New Name From Builder",
                description = "New Description From Builder"
            )

            whenever(idempotencyService.checkIdempotency(anyOrNull()))
                .thenReturn(Mono.empty())
            whenever(aggregateRepository.findById(productId))
                .thenReturn(Mono.just(aggregate))
            whenever(aggregateRepository.update(any()))
                .thenAnswer { invocation ->
                    Mono.just(invocation.getArgument<ProductAggregate>(0))
                }
            whenever(idempotencyService.recordProcessedCommand(anyOrNull(), any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(handler.handle(command))
                .expectNextMatches { result ->
                    result is CommandSuccess
                }
                .verifyComplete()
        }
    }
}
