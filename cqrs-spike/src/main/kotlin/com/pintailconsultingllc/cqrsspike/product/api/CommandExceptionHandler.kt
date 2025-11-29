package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.infrastructure.error.BaseExceptionHandler
import com.pintailconsultingllc.cqrsspike.infrastructure.error.EnhancedErrorResponse
import com.pintailconsultingllc.cqrsspike.infrastructure.error.ServiceUnavailableResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.CommandErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ConflictErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.PriceChangeErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ApiErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ValidationError
import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.exception.DuplicateSkuException
import com.pintailconsultingllc.cqrsspike.product.command.exception.InvalidStateTransitionException
import com.pintailconsultingllc.cqrsspike.product.command.exception.PriceChangeThresholdExceededException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductDeletedException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductInvariantViolationException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductNotFoundException
import com.pintailconsultingllc.cqrsspike.product.command.handler.CommandRateLimitException
import com.pintailconsultingllc.cqrsspike.product.command.handler.CommandServiceUnavailableException
import com.pintailconsultingllc.cqrsspike.product.command.validation.CommandValidationException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Exception handler for Product Command endpoints.
 *
 * Translates domain exceptions to appropriate HTTP responses
 * with structured error payloads.
 *
 * Implements AC10: "All errors are logged with correlation IDs"
 */
@RestControllerAdvice(basePackageClasses = [ProductCommandController::class])
@Order(1)
class CommandExceptionHandler : BaseExceptionHandler() {

    private val logger = LoggerFactory.getLogger(CommandExceptionHandler::class.java)

    /**
     * Handle validation errors from @Valid annotations.
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationError(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiErrorResponse>> {
        logger.warn("Validation error: {}", ex.message)

        val errors = ex.bindingResult.fieldErrors.map { fieldError ->
            ValidationError(
                field = fieldError.field,
                message = fieldError.defaultMessage ?: "Invalid value",
                rejectedValue = fieldError.rejectedValue
            )
        }

        val response = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Validation failed",
            path = exchange.request.path.value(),
            details = errors
        )

        return Mono.just(ResponseEntity.badRequest().body(response))
    }

    /**
     * Handle command validation exceptions.
     */
    @ExceptionHandler(CommandValidationException::class)
    fun handleCommandValidation(
        ex: CommandValidationException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<CommandErrorResponse>> {
        logger.warn("Command validation failed: {}", ex.message)

        val response = CommandErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Command validation failed",
            path = exchange.request.path.value(),
            code = "VALIDATION_FAILED",
            details = ex.errors.map { mapOf("field" to it.field, "message" to it.message) }
        )

        return Mono.just(ResponseEntity.badRequest().body(response))
    }

    /**
     * Handle product not found.
     */
    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(
        ex: ProductNotFoundException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<CommandErrorResponse>> {
        logger.warn("Product not found: {}", ex.productId)

        val response = CommandErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = ex.message ?: "Product not found",
            path = exchange.request.path.value(),
            code = "PRODUCT_NOT_FOUND"
        )

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response))
    }

    /**
     * Handle duplicate SKU.
     */
    @ExceptionHandler(DuplicateSkuException::class)
    fun handleDuplicateSku(
        ex: DuplicateSkuException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<CommandErrorResponse>> {
        logger.warn("Duplicate SKU: {}", ex.sku)

        val response = CommandErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = HttpStatus.CONFLICT.reasonPhrase,
            message = ex.message ?: "SKU already exists",
            path = exchange.request.path.value(),
            code = "DUPLICATE_SKU",
            details = mapOf("sku" to ex.sku)
        )

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(response))
    }

    /**
     * Handle concurrent modification.
     *
     * Implements AC10: "Concurrent modification conflicts return HTTP 409 with retry guidance"
     */
    @ExceptionHandler(ConcurrentModificationException::class)
    fun handleConcurrentModification(
        ex: ConcurrentModificationException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ConflictErrorResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.warn(
            "[correlationId={}] Concurrent modification for product {}: expected {}, actual {}",
            correlationId, ex.productId, ex.expectedVersion, ex.actualVersion
        )

        val response = ConflictErrorResponse(
            message = ex.message ?: "Concurrent modification detected",
            path = getPath(exchange),
            correlationId = correlationId,
            currentVersion = ex.actualVersion,
            expectedVersion = ex.expectedVersion,
            recommendedAction = "GET /api/products/${ex.productId} to retrieve current version, then retry with expectedVersion=${ex.actualVersion}"
        )

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(response))
    }

    /**
     * Handle invalid state transition.
     */
    @ExceptionHandler(InvalidStateTransitionException::class)
    fun handleInvalidStateTransition(
        ex: InvalidStateTransitionException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<CommandErrorResponse>> {
        logger.warn(
            "Invalid state transition for product {}: {} -> {}",
            ex.productId, ex.currentStatus, ex.targetStatus
        )

        val response = CommandErrorResponse(
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            error = "Unprocessable Entity",
            message = ex.message ?: "Invalid state transition",
            path = exchange.request.path.value(),
            code = "INVALID_STATE_TRANSITION",
            details = mapOf(
                "currentStatus" to ex.currentStatus.name,
                "targetStatus" to ex.targetStatus.name
            )
        )

        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response))
    }

    /**
     * Handle product deleted.
     */
    @ExceptionHandler(ProductDeletedException::class)
    fun handleProductDeleted(
        ex: ProductDeletedException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<CommandErrorResponse>> {
        logger.warn("Operation attempted on deleted product: {}", ex.productId)

        val response = CommandErrorResponse(
            status = HttpStatus.GONE.value(),
            error = "Gone",
            message = ex.message ?: "Product has been deleted",
            path = exchange.request.path.value(),
            code = "PRODUCT_DELETED"
        )

        return Mono.just(ResponseEntity.status(HttpStatus.GONE).body(response))
    }

    /**
     * Handle price change threshold exceeded.
     */
    @ExceptionHandler(PriceChangeThresholdExceededException::class)
    fun handlePriceThresholdExceeded(
        ex: PriceChangeThresholdExceededException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<PriceChangeErrorResponse>> {
        logger.warn(
            "Price change threshold exceeded for product {}: {}% change",
            ex.productId, ex.changePercentage
        )

        val response = PriceChangeErrorResponse(
            message = ex.message ?: "Price change exceeds threshold",
            path = exchange.request.path.value(),
            currentPrice = ex.currentPriceCents,
            requestedPrice = ex.newPriceCents,
            changePercentage = ex.changePercentage,
            threshold = ex.thresholdPercentage
        )

        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response))
    }

    /**
     * Handle product invariant violations.
     */
    @ExceptionHandler(ProductInvariantViolationException::class)
    fun handleInvariantViolation(
        ex: ProductInvariantViolationException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<CommandErrorResponse>> {
        logger.warn("Invariant violation for product {}: {}", ex.productId, ex.invariant)

        val response = CommandErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "Business rule violation",
            path = exchange.request.path.value(),
            code = "INVARIANT_VIOLATION",
            details = mapOf(
                "invariant" to ex.invariant,
                "details" to ex.details
            )
        )

        return Mono.just(ResponseEntity.badRequest().body(response))
    }

    /**
     * Handle rate limit exceeded from resilience4j.
     *
     * Implements AC10: "Rate limiting prevents abuse of command endpoints"
     */
    @ExceptionHandler(RequestNotPermitted::class)
    fun handleRateLimitExceeded(
        ex: RequestNotPermitted,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<EnhancedErrorResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.warn("[correlationId={}] Rate limit exceeded: {}", correlationId, ex.message)

        val response = EnhancedErrorResponse(
            status = HttpStatus.TOO_MANY_REQUESTS.value(),
            error = "Too Many Requests",
            message = "Rate limit exceeded. Please retry later.",
            path = getPath(exchange),
            correlationId = correlationId,
            code = "RATE_LIMIT_EXCEEDED",
            retryAfter = 5,
            retryGuidance = "Wait for the specified interval before retrying the request."
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "5")
                .body(response)
        )
    }

    /**
     * Handle rate limit exception from command handler.
     *
     * Implements AC10: "Rate limiting prevents abuse of command endpoints"
     */
    @ExceptionHandler(CommandRateLimitException::class)
    fun handleCommandRateLimit(
        ex: CommandRateLimitException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<EnhancedErrorResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.warn("[correlationId={}] Command rate limit: {}", correlationId, ex.message)

        val response = EnhancedErrorResponse(
            status = HttpStatus.TOO_MANY_REQUESTS.value(),
            error = "Too Many Requests",
            message = ex.message ?: "Rate limit exceeded. Please retry later.",
            path = getPath(exchange),
            correlationId = correlationId,
            code = "RATE_LIMIT_EXCEEDED",
            retryAfter = 5,
            retryGuidance = "Wait for the specified interval before retrying the request."
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "5")
                .body(response)
        )
    }

    /**
     * Handle circuit breaker open from resilience4j.
     *
     * Implements AC10: "Circuit breaker pattern protects database operations"
     */
    @ExceptionHandler(CallNotPermittedException::class)
    fun handleCircuitBreakerOpen(
        ex: CallNotPermittedException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ServiceUnavailableResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.error("[correlationId={}] Circuit breaker open: {}", correlationId, ex.message)

        val response = ServiceUnavailableResponse(
            message = "Service temporarily unavailable. Please retry later.",
            path = getPath(exchange),
            correlationId = correlationId,
            retryAfter = 30,
            circuitBreakerState = "OPEN"
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(response)
        )
    }

    /**
     * Handle service unavailable from command handler.
     *
     * Implements AC10: "Fallback methods provide graceful degradation"
     */
    @ExceptionHandler(CommandServiceUnavailableException::class)
    fun handleServiceUnavailable(
        ex: CommandServiceUnavailableException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ServiceUnavailableResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.error("[correlationId={}] Service unavailable: {}", correlationId, ex.message)

        val response = ServiceUnavailableResponse(
            message = ex.message ?: "Service temporarily unavailable. Please retry later.",
            path = getPath(exchange),
            correlationId = correlationId,
            retryAfter = 30
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(response)
        )
    }
}
