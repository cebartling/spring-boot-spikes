package com.pintailconsultingllc.cqrsspike.exception

/**
 * Exception thrown when a requested secret is not found in Vault.
 *
 * This exception is used to indicate that a secret retrieval operation failed
 * because the specified secret path or key does not exist.
 *
 * @param message The detail message explaining why the exception was thrown
 * @param cause The underlying cause of this exception (optional)
 */
class SecretNotFoundException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
