package com.pintailconsultingllc.cqrsspike.exception

import com.pintailconsultingllc.cqrsspike.dto.ErrorResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.RequestPath
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.UnsupportedMediaTypeStatusException
import reactor.test.StepVerifier

/**
 * Unit tests for GlobalExceptionHandler.
 *
 * Tests verify that the exception handler correctly processes various
 * exception types and returns properly structured error responses.
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Mock
    private lateinit var exchange: ServerWebExchange

    @Mock
    private lateinit var request: ServerHttpRequest

    @Mock
    private lateinit var requestPath: RequestPath

    private lateinit var handler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler()
        whenever(exchange.request).thenReturn(request)
        whenever(request.path).thenReturn(requestPath)
        whenever(requestPath.value()).thenReturn("/api/products")
    }

    @Nested
    @DisplayName("handleUnsupportedMediaType")
    inner class HandleUnsupportedMediaType {

        @Test
        @DisplayName("should return 415 status with structured error response")
        fun shouldReturn415WithStructuredErrorResponse() {
            val exception = UnsupportedMediaTypeStatusException(
                MediaType.TEXT_PLAIN,
                listOf(MediaType.APPLICATION_JSON)
            )

            val result = handler.handleUnsupportedMediaType(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE &&
                        response.body?.status == 415 &&
                        response.body?.error == "Unsupported Media Type" &&
                        response.body?.message?.contains("text/plain") == true &&
                        response.body?.message?.contains("application/json") == true &&
                        response.body?.path == "/api/products"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should include supported media types in error message")
        fun shouldIncludeSupportedMediaTypesInErrorMessage() {
            val supportedTypes = listOf(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
            val exception = UnsupportedMediaTypeStatusException(
                MediaType.TEXT_PLAIN,
                supportedTypes
            )

            val result = handler.handleUnsupportedMediaType(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    val message = response.body?.message ?: ""
                    message.contains("application/json") && message.contains("application/xml")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle null content type gracefully")
        fun shouldHandleNullContentTypeGracefully() {
            val nullContentType: MediaType? = null
            val exception = UnsupportedMediaTypeStatusException(
                nullContentType,
                listOf(MediaType.APPLICATION_JSON)
            )

            val result = handler.handleUnsupportedMediaType(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE &&
                        response.body?.message?.contains("unknown") == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should set response content type to application/json")
        fun shouldSetResponseContentTypeToJson() {
            val exception = UnsupportedMediaTypeStatusException(
                MediaType.TEXT_PLAIN,
                listOf(MediaType.APPLICATION_JSON)
            )

            val result = handler.handleUnsupportedMediaType(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.headers.contentType == MediaType.APPLICATION_JSON
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("handleSecretNotFound")
    inner class HandleSecretNotFound {

        @Test
        @DisplayName("should return 500 status with user-friendly error message")
        fun shouldReturn500WithUserFriendlyErrorMessage() {
            val exception = SecretNotFoundException("Secret not found at path: cqrs-spike/database")

            val result = handler.handleSecretNotFound(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR &&
                        response.body?.status == 500 &&
                        response.body?.error == "Internal Server Error" &&
                        response.body?.message == "Configuration error. Please contact support." &&
                        response.body?.path == "/api/products"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("handleVaultConnectionError")
    inner class HandleVaultConnectionError {

        @Test
        @DisplayName("should return 503 status with service unavailable message")
        fun shouldReturn503WithServiceUnavailableMessage() {
            val exception = VaultConnectionException("Connection refused")

            val result = handler.handleVaultConnectionError(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.SERVICE_UNAVAILABLE &&
                        response.body?.status == 503 &&
                        response.body?.error == "Service Unavailable" &&
                        response.body?.message == "Service temporarily unavailable. Please try again later." &&
                        response.body?.path == "/api/products"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("handleGenericException")
    inner class HandleGenericException {

        @Test
        @DisplayName("should return 500 status for unexpected exceptions")
        fun shouldReturn500ForUnexpectedExceptions() {
            val exception = RuntimeException("Unexpected error")

            val result = handler.handleGenericException(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR &&
                        response.body?.status == 500 &&
                        response.body?.error == "Internal Server Error" &&
                        response.body?.message == "An unexpected error occurred. Please try again later." &&
                        response.body?.path == "/api/products"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should not expose internal error details to client")
        fun shouldNotExposeInternalErrorDetailsToClient() {
            val exception = RuntimeException("Database connection failed: password=secret123")

            val result = handler.handleGenericException(exception, exchange)

            StepVerifier.create(result)
                .expectNextMatches { response ->
                    val message = response.body?.message ?: ""
                    !message.contains("Database") &&
                        !message.contains("password") &&
                        !message.contains("secret123")
                }
                .verifyComplete()
        }
    }
}
