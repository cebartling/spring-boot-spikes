package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Entity for tracking processed commands for idempotency.
 */
@Table("command_model\".\"processed_command")
data class ProcessedCommandEntity(
    @Id
    @Column("idempotency_key")
    val idempotencyKey: String,

    @Column("command_type")
    val commandType: String,

    @Column("product_id")
    val productId: UUID,

    @Column("result_data")
    val resultData: String?,

    @Column("processed_at")
    val processedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("expires_at")
    val expiresAt: OffsetDateTime
)
