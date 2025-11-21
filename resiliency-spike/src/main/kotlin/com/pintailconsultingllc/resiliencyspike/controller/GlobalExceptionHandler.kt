package com.pintailconsultingllc.resiliencyspike.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * Error response DTO
 */
data class ErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)

/**
 * Global exception handler for REST controllers
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handle NoSuchElementException (typically thrown when an entity is not found)
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFoundException(
        ex: NoSuchElementException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.warn("Resource not found: {}", ex.message)

        val errorResponse = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = ex.message ?: "Resource not found",
            path = exchange.request.path.value()
        )

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse))
    }

    /**
     * Handle IllegalArgumentException (typically thrown for validation errors)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(
        ex: IllegalArgumentException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.warn("Bad request: {}", ex.message)

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "Invalid request",
            path = exchange.request.path.value()
        )

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse))
    }

    /**
     * Handle IllegalStateException (typically thrown for invalid state transitions)
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(
        ex: IllegalStateException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.warn("Conflict: {}", ex.message)

        val errorResponse = ErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = HttpStatus.CONFLICT.reasonPhrase,
            message = ex.message ?: "Conflict in current state",
            path = exchange.request.path.value()
        )

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse))
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.error("Internal server error", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "An unexpected error occurred",
            path = exchange.request.path.value()
        )

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse))
    }
}
