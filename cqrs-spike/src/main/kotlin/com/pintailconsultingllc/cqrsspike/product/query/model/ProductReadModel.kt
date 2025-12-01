package com.pintailconsultingllc.cqrsspike.product.query.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.jvm.JvmName

/**
 * Read model entity for Product queries.
 *
 * This is a denormalized view optimized for read operations.
 * It is updated asynchronously from domain events via projections.
 *
 * Maps to read_model.product table.
 *
 * Implements Persistable to control INSERT vs UPDATE behavior in Spring Data R2DBC.
 * Since the product ID comes from the aggregate (not auto-generated), we use
 * the isNew flag to explicitly indicate when this is a new entity.
 */
@Table("read_model\".\"product")
class ProductReadModel @PersistenceCreator constructor(
    @field:Id
    @field:Column("id")
    private val productId: UUID,

    @Column("sku")
    val sku: String,

    @Column("name")
    val name: String,

    @Column("description")
    val description: String?,

    @Column("price_cents")
    val priceCents: Int,

    @Column("status")
    val status: String,

    @Column("created_at")
    val createdAt: OffsetDateTime,

    @Column("updated_at")
    val updatedAt: OffsetDateTime,

    @Column("version")
    val aggregateVersion: Long,

    @Column("is_deleted")
    val isDeleted: Boolean = false,

    @Column("price_display")
    val priceDisplay: String? = null,

    @Column("search_text")
    val searchText: String? = null,

    @Column("last_event_id")
    val lastEventId: UUID? = null
) : Persistable<UUID> {

    /**
     * Flag to indicate if this entity is new (should INSERT) or existing (should UPDATE).
     * Not persisted to the database. Defaults to false because entities loaded from the
     * database are existing (not new). For new entities, use the companion object's
     * newInstance() method which sets this to true.
     */
    @Transient
    private var isNewEntity: Boolean = false

    /** Public accessor for the product ID. */
    val id: UUID
        @JvmName("productId")
        get() = productId

    override fun getId(): UUID = productId

    override fun isNew(): Boolean = isNewEntity

    /**
     * Creates a copy of this entity with optional property overrides.
     * When used for updates, the copy is automatically marked as existing (not new).
     */
    fun copy(
        id: UUID = this.productId,
        sku: String = this.sku,
        name: String = this.name,
        description: String? = this.description,
        priceCents: Int = this.priceCents,
        status: String = this.status,
        createdAt: OffsetDateTime = this.createdAt,
        updatedAt: OffsetDateTime = this.updatedAt,
        aggregateVersion: Long = this.aggregateVersion,
        isDeleted: Boolean = this.isDeleted,
        priceDisplay: String? = this.priceDisplay,
        searchText: String? = this.searchText,
        lastEventId: UUID? = this.lastEventId,
        isNew: Boolean = false  // Updates should always be marked as existing
    ): ProductReadModel {
        val copy = ProductReadModel(
            productId = id,
            sku = sku,
            name = name,
            description = description,
            priceCents = priceCents,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            aggregateVersion = aggregateVersion,
            isDeleted = isDeleted,
            priceDisplay = priceDisplay,
            searchText = searchText,
            lastEventId = lastEventId
        )
        copy.isNewEntity = isNew
        return copy
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProductReadModel
        return productId == other.productId
    }

    override fun hashCode(): Int = productId.hashCode()

    companion object {
        /**
         * Creates a new ProductReadModel instance marked as new for insertion.
         */
        fun newInstance(
            productId: UUID,
            sku: String,
            name: String,
            description: String?,
            priceCents: Int,
            status: String,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
            aggregateVersion: Long,
            isDeleted: Boolean = false,
            priceDisplay: String? = null,
            searchText: String? = null,
            lastEventId: UUID? = null
        ): ProductReadModel {
            val instance = ProductReadModel(
                productId = productId,
                sku = sku,
                name = name,
                description = description,
                priceCents = priceCents,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                aggregateVersion = aggregateVersion,
                isDeleted = isDeleted,
                priceDisplay = priceDisplay,
                searchText = searchText,
                lastEventId = lastEventId
            )
            instance.isNewEntity = true
            return instance
        }
    }
}
