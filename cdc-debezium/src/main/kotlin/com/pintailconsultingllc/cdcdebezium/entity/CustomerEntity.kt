package com.pintailconsultingllc.cdcdebezium.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("customer_materialized")
class CustomerEntity private constructor(
    @Id
    @get:JvmName("customerId")
    val id: UUID,
    val email: String,
    val status: String,
    @Column("updated_at")
    val updatedAt: Instant,
    @Column("source_timestamp")
    val sourceTimestamp: Long? = null,
    @Transient
    private val _isNew: Boolean
) : Persistable<UUID> {

    @PersistenceCreator
    constructor(
        id: UUID,
        email: String,
        status: String,
        updatedAt: Instant,
        sourceTimestamp: Long? = null
    ) : this(id, email, status, updatedAt, sourceTimestamp, false)

    override fun getId(): UUID = id
    override fun isNew(): Boolean = _isNew

    fun copy(
        id: UUID = this.id,
        email: String = this.email,
        status: String = this.status,
        updatedAt: Instant = this.updatedAt,
        sourceTimestamp: Long? = this.sourceTimestamp,
        isNewEntity: Boolean = this._isNew
    ) = CustomerEntity(id, email, status, updatedAt, sourceTimestamp, isNewEntity)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomerEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "CustomerEntity(id=$id, email=$email, status=$status, updatedAt=$updatedAt, sourceTimestamp=$sourceTimestamp)"

    companion object {
        fun create(
            id: UUID,
            email: String,
            status: String,
            updatedAt: Instant,
            sourceTimestamp: Long? = null,
            isNewEntity: Boolean = true
        ) = CustomerEntity(id, email, status, updatedAt, sourceTimestamp, isNewEntity)
    }
}
