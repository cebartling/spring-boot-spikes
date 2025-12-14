package com.pintailconsultingllc.sagapattern.service

/**
 * Base exception for all saga service errors.
 *
 * Provides a consistent structure for exceptions from external service calls
 * with optional error codes and retryability information.
 *
 * @param message Human-readable error description
 * @param errorCode Service-specific error code for programmatic handling
 * @param retryable Whether the operation may succeed if retried
 * @param cause The underlying exception that caused this error
 */
abstract class SagaServiceException(
    message: String,
    open val errorCode: String? = null,
    open val retryable: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(message, cause)
