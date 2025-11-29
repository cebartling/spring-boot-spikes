package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.infrastructure.error.BaseExceptionHandler
import com.pintailconsultingllc.cqrsspike.infrastructure.error.EnhancedErrorResponse
import com.pintailconsultingllc.cqrsspike.infrastructure.error.ServiceUnavailableResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ApiErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ValidationError
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryRateLimitException
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryServiceUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono

/**
 * Global exception handler for Product Query endpoints.
 *
 * Provides consistent error responses for validation errors,
 * invalid requests, and unexpected errors.
 *
 * Implements AC10: "All errors are logged with correlation IDs"
 */
@RestControllerAdvice(basePackages = ["com.pintailconsultingllc.cqrsspike.product.api"])
@Order(2)
class QueryExceptionHandler : BaseExceptionHandler() {

    private val logger = LoggerFactory.getLogger(QueryExceptionHandler::class.java)

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
     * Handle constraint violations from @Validated annotations.
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiErrorResponse>> {
        logger.warn("Constraint violation: {}", ex.message)

        val errors = ex.constraintViolations.map { violation ->
            val propertyPath = violation.propertyPath.toString()
            val paramName = propertyPath.substringAfterLast('.')

            ValidationError(
                field = paramName,
                message = violation.message,
                rejectedValue = violation.invalidValue
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
     * Handle invalid input errors (e.g., invalid UUID format).
     */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleInputError(
        ex: ServerWebInputException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiErrorResponse>> {
        logger.warn("Input error: {}", ex.message)

        val response = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Invalid request: ${ex.reason ?: "Invalid input"}",
            path = exchange.request.path.value()
        )

        return Mono.just(ResponseEntity.badRequest().body(response))
    }

    /**
     * Handle IllegalArgumentException (e.g., invalid enum values).
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiErrorResponse>> {
        logger.warn("Illegal argument: {}", ex.message)

        val response = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "Invalid argument",
            path = exchange.request.path.value()
        )

        return Mono.just(ResponseEntity.badRequest().body(response))
    }

    /**
     * Handle all other unexpected errors.
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericError(
        ex: Exception,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<EnhancedErrorResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.error("[correlationId={}] Unexpected error in query endpoint", correlationId, ex)

        val response = EnhancedErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "An unexpected error occurred",
            path = getPath(exchange),
            correlationId = correlationId,
            code = "INTERNAL_ERROR"
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        )
    }

    // ============ Resiliency Exception Handlers (AC10) ============

    /**
     * Handle rate limit exceeded from resilience4j.
     *
     * Implements AC10: "Rate limiting prevents abuse of query endpoints"
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
            retryAfter = 2,
            retryGuidance = "Wait for the specified interval before retrying the request."
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "2")
                .body(response)
        )
    }

    /**
     * Handle query rate limit exception.
     *
     * Implements AC10: "Rate limiting prevents abuse of query endpoints"
     */
    @ExceptionHandler(QueryRateLimitException::class)
    fun handleQueryRateLimit(
        ex: QueryRateLimitException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<EnhancedErrorResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.warn("[correlationId={}] Query rate limit: {}", correlationId, ex.message)

        val response = EnhancedErrorResponse(
            status = HttpStatus.TOO_MANY_REQUESTS.value(),
            error = "Too Many Requests",
            message = ex.message ?: "Rate limit exceeded. Please retry later.",
            path = getPath(exchange),
            correlationId = correlationId,
            code = "RATE_LIMIT_EXCEEDED",
            retryAfter = 2,
            retryGuidance = "Wait for the specified interval before retrying the request."
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "2")
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
            retryAfter = 15,
            circuitBreakerState = "OPEN"
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "15")
                .body(response)
        )
    }

    /**
     * Handle query service unavailable exception.
     *
     * Implements AC10: "Fallback methods provide graceful degradation"
     */
    @ExceptionHandler(QueryServiceUnavailableException::class)
    fun handleQueryServiceUnavailable(
        ex: QueryServiceUnavailableException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ServiceUnavailableResponse>> {
        val correlationId = getCorrelationId(exchange)
        logger.error("[correlationId={}] Query service unavailable: {}", correlationId, ex.message)

        val response = ServiceUnavailableResponse(
            message = ex.message ?: "Query service temporarily unavailable. Please retry later.",
            path = getPath(exchange),
            correlationId = correlationId,
            retryAfter = 15
        )

        return Mono.just(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "15")
                .body(response)
        )
    }
}
