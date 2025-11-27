package com.pintailconsultingllc.cqrsspike.product.command.handler

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ProductAggregateRepository
import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandAlreadyProcessed
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandResult
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandSuccess
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ExistingProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.service.IdempotencyService
import com.pintailconsultingllc.cqrsspike.product.command.validation.ActivateProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.ChangePriceCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.CreateProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.DeleteProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.DiscontinueProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.UpdateProductCommandValidator
import com.pintailconsultingllc.cqrsspike.product.command.validation.ValidationResult
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.Optional

/**
 * Command handler for Product aggregate.
 *
 * Orchestrates command processing including:
 * - Validation
 * - Idempotency checking
 * - Aggregate loading/creation
 * - Business logic execution
 * - Persistence
 * - Event publication
 *
 * Implements resiliency patterns (circuit breaker, retry, rate limiting).
 */
@Service
class ProductCommandHandler(
    private val aggregateRepository: ProductAggregateRepository,
    private val idempotencyService: IdempotencyService,
    private val createValidator: CreateProductCommandValidator,
    private val updateValidator: UpdateProductCommandValidator,
    private val changePriceValidator: ChangePriceCommandValidator,
    private val activateValidator: ActivateProductCommandValidator,
    private val discontinueValidator: DiscontinueProductCommandValidator,
    private val deleteValidator: DeleteProductCommandValidator
) {
    private val logger = LoggerFactory.getLogger(ProductCommandHandler::class.java)

    /**
     * Handles CreateProductCommand.
     * Creates a new product with the given details.
     *
     * @param command The create product command
     * @return Mono<CommandResult> with the created product ID and version
     */
    @RateLimiter(name = "productCommands", fallbackMethod = "rateLimitFallbackCreate")
    @Retry(name = "productCommands", fallbackMethod = "retryFallbackCreate")
    @CircuitBreaker(name = "productCommands", fallbackMethod = "circuitBreakerFallbackCreate")
    @Transactional
    fun handle(command: CreateProductCommand): Mono<CommandResult> {
        logger.info("Handling CreateProductCommand: sku=${command.sku}")

        return checkIdempotency(command.idempotencyKey)
            .flatMap { existingResultOpt ->
                if (existingResultOpt.isPresent) {
                    val existingResult = existingResultOpt.get()
                    logger.info("Idempotent request detected: key=${command.idempotencyKey}")
                    Mono.just(CommandAlreadyProcessed(
                        productId = existingResult.productId,
                        idempotencyKey = command.idempotencyKey!!
                    ) as CommandResult)
                } else {
                    validateAndExecuteCreate(command)
                }
            }
    }

    /**
     * Handles UpdateProductCommand.
     * Updates product details (name and description).
     *
     * @param command The update product command
     * @return Mono<CommandResult> with the updated product ID and version
     */
    @RateLimiter(name = "productCommands", fallbackMethod = "rateLimitFallbackUpdate")
    @Retry(name = "productCommands", fallbackMethod = "retryFallbackUpdate")
    @CircuitBreaker(name = "productCommands", fallbackMethod = "circuitBreakerFallbackUpdate")
    @Transactional
    fun handle(command: UpdateProductCommand): Mono<CommandResult> {
        logger.info("Handling UpdateProductCommand: productId=${command.productId}")

        return checkIdempotency(command.idempotencyKey)
            .flatMap { existingResultOpt ->
                if (existingResultOpt.isPresent) {
                    val existingResult = existingResultOpt.get()
                    Mono.just(CommandAlreadyProcessed(
                        productId = existingResult.productId,
                        idempotencyKey = command.idempotencyKey!!
                    ) as CommandResult)
                } else {
                    validateAndExecuteUpdate(command)
                }
            }
    }

    /**
     * Handles ChangePriceCommand.
     * Changes the product price.
     *
     * @param command The change price command
     * @return Mono<CommandResult> with the updated product ID and version
     */
    @RateLimiter(name = "productCommands", fallbackMethod = "rateLimitFallbackChangePrice")
    @Retry(name = "productCommands", fallbackMethod = "retryFallbackChangePrice")
    @CircuitBreaker(name = "productCommands", fallbackMethod = "circuitBreakerFallbackChangePrice")
    @Transactional
    fun handle(command: ChangePriceCommand): Mono<CommandResult> {
        logger.info("Handling ChangePriceCommand: productId=${command.productId}")

        return checkIdempotency(command.idempotencyKey)
            .flatMap { existingResultOpt ->
                if (existingResultOpt.isPresent) {
                    val existingResult = existingResultOpt.get()
                    Mono.just(CommandAlreadyProcessed(
                        productId = existingResult.productId,
                        idempotencyKey = command.idempotencyKey!!
                    ) as CommandResult)
                } else {
                    validateAndExecuteChangePrice(command)
                }
            }
    }

    /**
     * Handles ActivateProductCommand.
     * Transitions a product from DRAFT to ACTIVE status.
     *
     * @param command The activate product command
     * @return Mono<CommandResult> with the updated product ID and version
     */
    @RateLimiter(name = "productCommands", fallbackMethod = "rateLimitFallbackActivate")
    @Retry(name = "productCommands", fallbackMethod = "retryFallbackActivate")
    @CircuitBreaker(name = "productCommands", fallbackMethod = "circuitBreakerFallbackActivate")
    @Transactional
    fun handle(command: ActivateProductCommand): Mono<CommandResult> {
        logger.info("Handling ActivateProductCommand: productId=${command.productId}")

        return checkIdempotency(command.idempotencyKey)
            .flatMap { existingResultOpt ->
                if (existingResultOpt.isPresent) {
                    val existingResult = existingResultOpt.get()
                    Mono.just(CommandAlreadyProcessed(
                        productId = existingResult.productId,
                        idempotencyKey = command.idempotencyKey!!
                    ) as CommandResult)
                } else {
                    validateAndExecuteActivate(command)
                }
            }
    }

    /**
     * Handles DiscontinueProductCommand.
     * Transitions a product to DISCONTINUED status.
     *
     * @param command The discontinue product command
     * @return Mono<CommandResult> with the updated product ID and version
     */
    @RateLimiter(name = "productCommands", fallbackMethod = "rateLimitFallbackDiscontinue")
    @Retry(name = "productCommands", fallbackMethod = "retryFallbackDiscontinue")
    @CircuitBreaker(name = "productCommands", fallbackMethod = "circuitBreakerFallbackDiscontinue")
    @Transactional
    fun handle(command: DiscontinueProductCommand): Mono<CommandResult> {
        logger.info("Handling DiscontinueProductCommand: productId=${command.productId}")

        return checkIdempotency(command.idempotencyKey)
            .flatMap { existingResultOpt ->
                if (existingResultOpt.isPresent) {
                    val existingResult = existingResultOpt.get()
                    Mono.just(CommandAlreadyProcessed(
                        productId = existingResult.productId,
                        idempotencyKey = command.idempotencyKey!!
                    ) as CommandResult)
                } else {
                    validateAndExecuteDiscontinue(command)
                }
            }
    }

    /**
     * Handles DeleteProductCommand.
     * Soft-deletes a product.
     *
     * @param command The delete product command
     * @return Mono<CommandResult> with the deleted product ID and version
     */
    @RateLimiter(name = "productCommands", fallbackMethod = "rateLimitFallbackDelete")
    @Retry(name = "productCommands", fallbackMethod = "retryFallbackDelete")
    @CircuitBreaker(name = "productCommands", fallbackMethod = "circuitBreakerFallbackDelete")
    @Transactional
    fun handle(command: DeleteProductCommand): Mono<CommandResult> {
        logger.info("Handling DeleteProductCommand: productId=${command.productId}")

        return checkIdempotency(command.idempotencyKey)
            .flatMap { existingResultOpt ->
                if (existingResultOpt.isPresent) {
                    val existingResult = existingResultOpt.get()
                    Mono.just(CommandAlreadyProcessed(
                        productId = existingResult.productId,
                        idempotencyKey = command.idempotencyKey!!
                    ) as CommandResult)
                } else {
                    validateAndExecuteDelete(command)
                }
            }
    }

    // Private helper methods

    private fun checkIdempotency(idempotencyKey: String?): Mono<Optional<CommandSuccess>> {
        return idempotencyService.checkIdempotency(idempotencyKey)
    }

    private fun recordIdempotency(
        idempotencyKey: String?,
        commandType: String,
        result: CommandSuccess
    ): Mono<CommandSuccess> {
        return idempotencyService.recordProcessedCommand(idempotencyKey, commandType, result.productId, result)
            .thenReturn(result)
    }

    private fun validateAndExecuteCreate(command: CreateProductCommand): Mono<CommandResult> {
        val validationResult = createValidator.validate(command)
        if (validationResult is ValidationResult.Invalid) {
            return Mono.error(validationResult.toException())
        }

        return Mono.fromCallable {
            ProductAggregate.create(
                sku = command.sku,
                name = command.name,
                description = command.description,
                priceCents = command.priceCents
            )
        }
        .flatMap { aggregate ->
            aggregateRepository.save(aggregate)
                .map { saved ->
                    CommandSuccess(
                        productId = saved.id,
                        version = saved.version
                    )
                }
        }
        .flatMap { result ->
            recordIdempotency(command.idempotencyKey, "CreateProductCommand", result)
        }
        .map { it as CommandResult }
        .doOnSuccess { result ->
            logger.info("Product created: productId=${(result as CommandSuccess).productId}")
        }
    }

    private fun validateAndExecuteUpdate(command: UpdateProductCommand): Mono<CommandResult> {
        val validationResult = updateValidator.validate(command)
        if (validationResult is ValidationResult.Invalid) {
            return Mono.error(validationResult.toException())
        }

        return loadAndExecute(command) { aggregate ->
            aggregate.update(
                newName = command.name,
                newDescription = command.description,
                expectedVersion = command.expectedVersion
            )
        }
        .flatMap { result ->
            recordIdempotency(command.idempotencyKey, "UpdateProductCommand", result)
        }
        .map { it as CommandResult }
    }

    private fun validateAndExecuteChangePrice(command: ChangePriceCommand): Mono<CommandResult> {
        val validationResult = changePriceValidator.validate(command)
        if (validationResult is ValidationResult.Invalid) {
            return Mono.error(validationResult.toException())
        }

        return loadAndExecute(command) { aggregate ->
            aggregate.changePrice(
                newPriceCents = command.newPriceCents,
                expectedVersion = command.expectedVersion,
                confirmLargeChange = command.confirmLargeChange
            )
        }
        .flatMap { result ->
            recordIdempotency(command.idempotencyKey, "ChangePriceCommand", result)
        }
        .map { it as CommandResult }
    }

    private fun validateAndExecuteActivate(command: ActivateProductCommand): Mono<CommandResult> {
        val validationResult = activateValidator.validate(command)
        if (validationResult is ValidationResult.Invalid) {
            return Mono.error(validationResult.toException())
        }

        return loadAndExecute(command) { aggregate ->
            aggregate.activate(expectedVersion = command.expectedVersion)
        }
        .flatMap { result ->
            recordIdempotency(command.idempotencyKey, "ActivateProductCommand", result)
        }
        .map { it as CommandResult }
    }

    private fun validateAndExecuteDiscontinue(command: DiscontinueProductCommand): Mono<CommandResult> {
        val validationResult = discontinueValidator.validate(command)
        if (validationResult is ValidationResult.Invalid) {
            return Mono.error(validationResult.toException())
        }

        return loadAndExecute(command) { aggregate ->
            aggregate.discontinue(
                expectedVersion = command.expectedVersion,
                reason = command.reason
            )
        }
        .flatMap { result ->
            recordIdempotency(command.idempotencyKey, "DiscontinueProductCommand", result)
        }
        .map { it as CommandResult }
    }

    private fun validateAndExecuteDelete(command: DeleteProductCommand): Mono<CommandResult> {
        val validationResult = deleteValidator.validate(command)
        if (validationResult is ValidationResult.Invalid) {
            return Mono.error(validationResult.toException())
        }

        return loadAndExecute(command) { aggregate ->
            aggregate.delete(
                expectedVersion = command.expectedVersion,
                deletedBy = command.deletedBy
            )
        }
        .flatMap { result ->
            recordIdempotency(command.idempotencyKey, "DeleteProductCommand", result)
        }
        .map { it as CommandResult }
    }

    /**
     * Generic method to load an aggregate and execute a command on it.
     */
    private fun loadAndExecute(
        command: ExistingProductCommand,
        action: (ProductAggregate) -> ProductAggregate
    ): Mono<CommandSuccess> {
        return aggregateRepository.findById(command.productId)
            .flatMap { aggregate ->
                Mono.fromCallable { action(aggregate) }
            }
            .flatMap { updatedAggregate ->
                aggregateRepository.update(updatedAggregate)
            }
            .map { saved ->
                CommandSuccess(
                    productId = saved.id,
                    version = saved.version
                )
            }
    }

    // Fallback methods for resiliency

    @Suppress("unused")
    private fun rateLimitFallbackCreate(command: CreateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.warn("Rate limit exceeded for CreateProductCommand", ex)
        return Mono.error(CommandRateLimitException("Too many requests. Please try again later."))
    }

    @Suppress("unused")
    private fun rateLimitFallbackUpdate(command: UpdateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.warn("Rate limit exceeded for UpdateProductCommand", ex)
        return Mono.error(CommandRateLimitException("Too many requests. Please try again later."))
    }

    @Suppress("unused")
    private fun rateLimitFallbackChangePrice(command: ChangePriceCommand, ex: Exception): Mono<CommandResult> {
        logger.warn("Rate limit exceeded for ChangePriceCommand", ex)
        return Mono.error(CommandRateLimitException("Too many requests. Please try again later."))
    }

    @Suppress("unused")
    private fun rateLimitFallbackActivate(command: ActivateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.warn("Rate limit exceeded for ActivateProductCommand", ex)
        return Mono.error(CommandRateLimitException("Too many requests. Please try again later."))
    }

    @Suppress("unused")
    private fun rateLimitFallbackDiscontinue(command: DiscontinueProductCommand, ex: Exception): Mono<CommandResult> {
        logger.warn("Rate limit exceeded for DiscontinueProductCommand", ex)
        return Mono.error(CommandRateLimitException("Too many requests. Please try again later."))
    }

    @Suppress("unused")
    private fun rateLimitFallbackDelete(command: DeleteProductCommand, ex: Exception): Mono<CommandResult> {
        logger.warn("Rate limit exceeded for DeleteProductCommand", ex)
        return Mono.error(CommandRateLimitException("Too many requests. Please try again later."))
    }

    @Suppress("unused")
    private fun retryFallbackCreate(command: CreateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Retry exhausted for CreateProductCommand", ex)
        return Mono.error(ex)
    }

    @Suppress("unused")
    private fun retryFallbackUpdate(command: UpdateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Retry exhausted for UpdateProductCommand", ex)
        return Mono.error(ex)
    }

    @Suppress("unused")
    private fun retryFallbackChangePrice(command: ChangePriceCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Retry exhausted for ChangePriceCommand", ex)
        return Mono.error(ex)
    }

    @Suppress("unused")
    private fun retryFallbackActivate(command: ActivateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Retry exhausted for ActivateProductCommand", ex)
        return Mono.error(ex)
    }

    @Suppress("unused")
    private fun retryFallbackDiscontinue(command: DiscontinueProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Retry exhausted for DiscontinueProductCommand", ex)
        return Mono.error(ex)
    }

    @Suppress("unused")
    private fun retryFallbackDelete(command: DeleteProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Retry exhausted for DeleteProductCommand", ex)
        return Mono.error(ex)
    }

    @Suppress("unused")
    private fun circuitBreakerFallbackCreate(command: CreateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Circuit breaker open for CreateProductCommand", ex)
        return Mono.error(CommandServiceUnavailableException("Service temporarily unavailable. Please try again later."))
    }

    @Suppress("unused")
    private fun circuitBreakerFallbackUpdate(command: UpdateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Circuit breaker open for UpdateProductCommand", ex)
        return Mono.error(CommandServiceUnavailableException("Service temporarily unavailable. Please try again later."))
    }

    @Suppress("unused")
    private fun circuitBreakerFallbackChangePrice(command: ChangePriceCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Circuit breaker open for ChangePriceCommand", ex)
        return Mono.error(CommandServiceUnavailableException("Service temporarily unavailable. Please try again later."))
    }

    @Suppress("unused")
    private fun circuitBreakerFallbackActivate(command: ActivateProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Circuit breaker open for ActivateProductCommand", ex)
        return Mono.error(CommandServiceUnavailableException("Service temporarily unavailable. Please try again later."))
    }

    @Suppress("unused")
    private fun circuitBreakerFallbackDiscontinue(command: DiscontinueProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Circuit breaker open for DiscontinueProductCommand", ex)
        return Mono.error(CommandServiceUnavailableException("Service temporarily unavailable. Please try again later."))
    }

    @Suppress("unused")
    private fun circuitBreakerFallbackDelete(command: DeleteProductCommand, ex: Exception): Mono<CommandResult> {
        logger.error("Circuit breaker open for DeleteProductCommand", ex)
        return Mono.error(CommandServiceUnavailableException("Service temporarily unavailable. Please try again later."))
    }
}

/**
 * Exception thrown when rate limit is exceeded.
 */
class CommandRateLimitException(message: String) : RuntimeException(message)

/**
 * Exception thrown when service is unavailable (circuit breaker open).
 */
class CommandServiceUnavailableException(message: String) : RuntimeException(message)
