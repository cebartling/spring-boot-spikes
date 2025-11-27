package com.pintailconsultingllc.cqrsspike.product.command.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.IdempotencyRepository
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ProcessedCommandEntity
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandSuccess
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

/**
 * Service for handling command idempotency.
 *
 * Tracks processed commands by idempotency key to prevent duplicate processing.
 */
@Service
class IdempotencyService(
    private val repository: IdempotencyRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${product.command.idempotency.ttl-hours:24}")
    private val ttlHours: Long
) {
    private val logger = LoggerFactory.getLogger(IdempotencyService::class.java)

    /**
     * Checks if a command with the given idempotency key has already been processed.
     *
     * @param idempotencyKey The unique key for the command
     * @return Mono<Optional<CommandSuccess>> - The previous result if found, empty otherwise
     */
    fun checkIdempotency(idempotencyKey: String?): Mono<CommandSuccess?> {
        if (idempotencyKey == null) {
            return Mono.empty()
        }

        return repository.findByIdempotencyKey(idempotencyKey)
            .map { entity ->
                try {
                    objectMapper.readValue(entity.resultData, CommandSuccess::class.java)
                } catch (e: Exception) {
                    logger.warn("Failed to deserialize result for key $idempotencyKey", e)
                    CommandSuccess(
                        productId = entity.productId,
                        version = 0,
                        timestamp = entity.processedAt
                    )
                }
            }
            .onErrorResume { error ->
                logger.error("Error checking idempotency for key $idempotencyKey", error)
                Mono.empty()
            }
    }

    /**
     * Records a successfully processed command.
     *
     * @param idempotencyKey The unique key for the command
     * @param commandType The type of command processed
     * @param productId The affected product ID
     * @param result The command result to store
     */
    fun recordProcessedCommand(
        idempotencyKey: String?,
        commandType: String,
        productId: UUID,
        result: CommandSuccess
    ): Mono<Void> {
        if (idempotencyKey == null) {
            return Mono.empty()
        }

        val entity = ProcessedCommandEntity(
            idempotencyKey = idempotencyKey,
            commandType = commandType,
            productId = productId,
            resultData = objectMapper.writeValueAsString(result),
            expiresAt = OffsetDateTime.now().plusHours(ttlHours)
        )

        return repository.save(entity)
            .doOnSuccess {
                logger.debug("Recorded processed command: key=$idempotencyKey, type=$commandType")
            }
            .then()
    }

    /**
     * Cleans up expired idempotency records.
     * Should be called by a scheduled job.
     */
    fun cleanupExpired(): Mono<Long> {
        return repository.deleteExpiredBefore(OffsetDateTime.now())
            .doOnSuccess { count ->
                if (count > 0) {
                    logger.info("Cleaned up $count expired idempotency records")
                }
            }
    }
}
