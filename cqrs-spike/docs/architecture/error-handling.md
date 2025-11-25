# Error Handling

This document describes the error handling architecture for the CQRS Spike REST API.

## Overview

The application uses a centralized error handling approach via `@RestControllerAdvice` to ensure consistent, structured error responses across all API endpoints.

## Error Response Structure

All API errors return a structured JSON response:

```json
{
  "status": 415,
  "error": "Unsupported Media Type",
  "message": "Content-Type 'text/plain' is not supported. Supported types: application/json",
  "path": "/api/products",
  "timestamp": "2025-11-25T12:00:00.000Z"
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `status` | Integer | HTTP status code |
| `error` | String | HTTP status reason phrase |
| `message` | String | Human-readable error description |
| `path` | String | Request path that caused the error |
| `timestamp` | ISO 8601 | When the error occurred |

## HTTP Status Codes

### Client Errors (4xx)

| Status | Error | When Returned |
|--------|-------|---------------|
| 415 | Unsupported Media Type | Request has invalid `Content-Type` header |

### Server Errors (5xx)

| Status | Error | When Returned |
|--------|-------|---------------|
| 500 | Internal Server Error | Unexpected server error or configuration issues |
| 503 | Service Unavailable | External dependency (e.g., Vault) unavailable |

## Unsupported Media Type (415)

When a request is made with an unsupported `Content-Type` header, the API returns a 415 error with details about supported types.

### Example Request

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: text/plain" \
  -d "invalid data"
```

### Example Response

```json
{
  "status": 415,
  "error": "Unsupported Media Type",
  "message": "Content-Type 'text/plain' is not supported. Supported types: application/json",
  "path": "/api/products",
  "timestamp": "2025-11-25T12:00:00.000Z"
}
```

### Controller Configuration

Controllers should specify accepted media types using the `consumes` attribute:

```kotlin
@RestController
@RequestMapping("/api/products")
class ProductController(private val service: ProductService) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(@RequestBody request: CreateProductRequest): Mono<ProductResponse> {
        return service.create(request)
    }

    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateProductRequest
    ): Mono<ProductResponse> {
        return service.update(id, request)
    }
}
```

## Global Exception Handler

The `GlobalExceptionHandler` class handles all exceptions and converts them to structured responses.

### Location

```
src/main/kotlin/com/pintailconsultingllc/cqrsspike/exception/GlobalExceptionHandler.kt
```

### Handled Exceptions

| Exception | HTTP Status | User Message |
|-----------|-------------|--------------|
| `UnsupportedMediaTypeStatusException` | 415 | Includes unsupported type and supported alternatives |
| `SecretNotFoundException` | 500 | "Configuration error. Please contact support." |
| `VaultConnectionException` | 503 | "Service temporarily unavailable. Please try again later." |
| `Exception` (generic) | 500 | "An unexpected error occurred. Please try again later." |

### Implementation Pattern

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

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

        logger.error("Unsupported media type - path: $path, contentType: $contentType", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            error = HttpStatus.UNSUPPORTED_MEDIA_TYPE.reasonPhrase,
            message = "Content-Type '$contentType' is not supported. Supported types: $supportedTypes",
            path = path,
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse)
        )
    }
}
```

## Error Response DTO

### Location

```
src/main/kotlin/com/pintailconsultingllc/cqrsspike/dto/ErrorResponse.kt
```

### Definition

```kotlin
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
```

## Security Considerations

### Information Disclosure Prevention

- Internal error details are **never** exposed to clients
- Stack traces are logged but not returned in responses
- Database errors return generic "service unavailable" messages
- Secret-related errors do not reveal secret paths or values

### Logging

All errors are logged with full context:

```kotlin
logger.error("Fallback triggered - path: $path, error: ${ex.message}", ex)
```

Log output includes:
- Request path
- Error message
- Full stack trace
- Trace ID (via OpenTelemetry)

## Testing Error Handling

### Unit Test Example

```kotlin
@ExtendWith(MockitoExtension::class)
class GlobalExceptionHandlerTest {

    @Mock
    private lateinit var exchange: ServerWebExchange

    @Test
    fun `should return 415 for unsupported media type`() {
        val exception = UnsupportedMediaTypeStatusException(
            MediaType.TEXT_PLAIN,
            listOf(MediaType.APPLICATION_JSON)
        )

        val result = handler.handleUnsupportedMediaType(exception, exchange)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                response.statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE &&
                    response.body?.status == 415
            }
            .verifyComplete()
    }
}
```

### Integration Test Example

```kotlin
@WebFluxTest(ProductController::class)
class ProductControllerTest {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `should return 415 for invalid content type`() {
        client.post()
            .uri("/api/products")
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("invalid")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .expectBody()
            .jsonPath("$.status").isEqualTo(415)
            .jsonPath("$.error").isEqualTo("Unsupported Media Type")
            .jsonPath("$.path").isEqualTo("/api/products")
    }
}
```

## See Also

- [CONSTITUTION.md](../../documentation/CONSTITUTION.md) - Section 6.4 and 6.5 on error handling
- [Overview](overview.md) - Architecture overview
- [Security](security.md) - Security model
