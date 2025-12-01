package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Entity for tracking processed commands for idempotency.
 *
 * Implements Persistable to control INSERT vs UPDATE behavior in Spring Data R2DBC.
 * Since the idempotency key is always provided (not auto-generated), we use
 * the isNew flag to explicitly indicate when this is a new entity.
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
    val expiresAt: OffsetDateTime,

    /**
     * Flag to indicate if this entity is new (should INSERT) or existing (should UPDATE).
     * Not persisted to the database.
     */
    @Transient
    private val isNew: Boolean = true
) : Persistable<String> {

    override fun getId(): String = idempotencyKey

    override fun isNew(): Boolean = isNew
}
