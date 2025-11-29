package com.pintailconsultingllc.cqrsspike.exception

import com.pintailconsultingllc.cqrsspike.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException
import reactor.core.publisher.Mono

/**
 * Global exception handler for REST API error handling.
 *
 * Provides consistent error response formatting across all API endpoints,
 * handling various exception types and returning structured JSON responses.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handles UnsupportedMediaTypeStatusException (HTTP 415).
     *
     * Returns a structured error response when a request is made with an unsupported Content-Type.
     *
     * @param ex The UnsupportedMediaTypeStatusException thrown
     * @param exchange The server web exchange containing request details
     * @return Mono containing ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(UnsupportedMediaTypeStatusException::class)
    fun handleUnsupportedMediaType(
        ex: UnsupportedMediaTypeStatusException,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> {
        val path = exchange.request.path.value()
        val contentType = ex.contentType?.toString() ?: "unknown"
        val supportedTypes = ex.supportedMediaTypes
            .map { it.toString() }
            .joinToString(", ")
            .ifEmpty { MediaType.APPLICATION_JSON_VALUE }

        val message = "Content-Type '$contentType' is not supported. Supported types: $supportedTypes"

        logger.error("Unsupported media type - path: $path, contentType: $contentType, error: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            error = HttpStatus.UNSUPPORTED_MEDIA_TYPE.reasonPhrase,
            message = message,
            path = path,
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse)
        )
    }

    /**
     * Handles SecretNotFoundException (HTTP 500).
     *
     * Returns a structured error response when a Vault secret is not found.
     *
     * @param ex The SecretNotFoundException thrown
     * @param exchange The server web exchange containing request details
     * @return Mono containing ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(SecretNotFoundException::class)
    fun handleSecretNotFound(
        ex: SecretNotFoundException,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> {
        val path = exchange.request.path.value()

        logger.error("Secret not found - path: $path, error: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "Configuration error. Please contact support.",
            path = path,
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse)
        )
    }

    /**
     * Handles VaultConnectionException (HTTP 503).
     *
     * Returns a structured error response when Vault connection fails.
     *
     * @param ex The VaultConnectionException thrown
     * @param exchange The server web exchange containing request details
     * @return Mono containing ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(VaultConnectionException::class)
    fun handleVaultConnectionError(
        ex: VaultConnectionException,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> {
        val path = exchange.request.path.value()

        logger.error("Vault connection failed - path: $path, error: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.SERVICE_UNAVAILABLE.value(),
            error = HttpStatus.SERVICE_UNAVAILABLE.reasonPhrase,
            message = "Service temporarily unavailable. Please try again later.",
            path = path,
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse)
        )
    }

    /**
     * Handles ServerWebInputException (HTTP 400).
     *
     * Returns a structured error response for invalid input such as malformed UUIDs,
     * type mismatches, or other input parsing errors.
     *
     * @param ex The ServerWebInputException thrown
     * @param exchange The server web exchange containing request details
     * @return Mono containing ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleServerWebInputException(
        ex: ServerWebInputException,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> {
        val path = exchange.request.path.value()

        logger.warn("Invalid input - path: $path, error: ${ex.reason}", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.reason ?: "Invalid request input",
            path = path,
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse)
        )
    }

    /**
     * Handles all other unhandled exceptions (HTTP 500).
     *
     * Provides a generic error response for unexpected errors while logging full details.
     *
     * @param ex The exception thrown
     * @param exchange The server web exchange containing request details
     * @return Mono containing ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> {
        val path = exchange.request.path.value()

        logger.error("Unhandled exception - path: $path, error: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "An unexpected error occurred. Please try again later.",
            path = path,
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse)
        )
    }
}
