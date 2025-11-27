package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * Repository for processed command tracking (idempotency).
 */
@Repository
interface IdempotencyRepository : ReactiveCrudRepository<ProcessedCommandEntity, String> {

    /**
     * Find a processed command by idempotency key.
     */
    @Query("""
        SELECT * FROM command_model.processed_command
        WHERE idempotency_key = :key AND expires_at > NOW()
    """)
    fun findByIdempotencyKey(key: String): Mono<ProcessedCommandEntity>

    // Cleanup logic for expired entries should be implemented in a custom repository using R2dbcEntityTemplate or DatabaseClient.
}
