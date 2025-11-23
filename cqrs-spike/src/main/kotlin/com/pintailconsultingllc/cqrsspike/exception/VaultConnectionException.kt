package com.pintailconsultingllc.cqrsspike.exception

/**
 * Exception thrown when connection to Vault fails.
 *
 * This exception is used to indicate that the application cannot establish
 * a connection to Vault or that Vault is unavailable.
 *
 * @param message The detail message explaining the connection failure
 * @param cause The underlying cause of this exception (optional)
 */
class VaultConnectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
