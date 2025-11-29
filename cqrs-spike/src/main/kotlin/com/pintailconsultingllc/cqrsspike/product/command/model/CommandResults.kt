package com.pintailconsultingllc.cqrsspike.product.command.model

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Base interface for command results.
 */
sealed interface CommandResult {
    val success: Boolean
    val timestamp: OffsetDateTime
}

/**
 * Successful command execution result.
 */
data class CommandSuccess(
    val productId: UUID,
    val version: Long,
    val status: String? = null,
    override val timestamp: OffsetDateTime = OffsetDateTime.now()
) : CommandResult {
    override val success: Boolean = true
}

/**
 * Result for idempotent commands that were already processed.
 */
data class CommandAlreadyProcessed(
    val productId: UUID,
    val version: Long,
    val status: String? = null,
    val idempotencyKey: String,
    override val timestamp: OffsetDateTime = OffsetDateTime.now()
) : CommandResult {
    override val success: Boolean = true
}
