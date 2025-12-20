package com.pintailconsultingllc.cdcdebezium.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "customers")
@CompoundIndex(name = "idx_status_updated", def = "{'status': 1, 'updatedAt': -1}")
data class CustomerDocument(
    @Id
    val id: String,

    @Indexed(unique = true)
    val email: String,

    @Indexed
    val status: String,

    val updatedAt: Instant,

    val cdcMetadata: CdcMetadata
) {
    companion object {
        fun fromCdcEvent(
            id: String,
            email: String,
            status: String,
            updatedAt: Instant,
            sourceTimestamp: Long,
            operation: CdcOperation,
            kafkaOffset: Long,
            kafkaPartition: Int
        ): CustomerDocument = CustomerDocument(
            id = id,
            email = email,
            status = status,
            updatedAt = updatedAt,
            cdcMetadata = CdcMetadata(
                sourceTimestamp = sourceTimestamp,
                operation = operation,
                kafkaOffset = kafkaOffset,
                kafkaPartition = kafkaPartition
            )
        )
    }

    fun isNewerThan(other: CustomerDocument): Boolean =
        this.cdcMetadata.sourceTimestamp > other.cdcMetadata.sourceTimestamp
}
