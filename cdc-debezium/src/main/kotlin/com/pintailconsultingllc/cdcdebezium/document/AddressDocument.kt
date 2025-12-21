package com.pintailconsultingllc.cdcdebezium.document

import com.pintailconsultingllc.cdcdebezium.dto.AddressCdcEvent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "addresses")
@CompoundIndex(name = "idx_customer_type", def = "{'customerId': 1, 'type': 1}")
data class AddressDocument(
    @Id
    val id: String,

    @Indexed
    val customerId: String,

    val type: AddressType,
    val street: String,
    val city: String,
    val state: String?,
    val postalCode: String,
    val country: String,
    val isDefault: Boolean,
    val updatedAt: Instant,
    val cdcMetadata: CdcMetadata
) {
    fun isNewerThan(other: AddressDocument): Boolean =
        this.cdcMetadata.sourceTimestamp > other.cdcMetadata.sourceTimestamp

    companion object {
        fun fromCdcEvent(
            event: AddressCdcEvent,
            operation: CdcOperation,
            kafkaOffset: Long,
            kafkaPartition: Int
        ): AddressDocument = AddressDocument(
            id = event.id.toString(),
            customerId = event.customerId.toString(),
            type = AddressType.fromString(event.type),
            street = event.street ?: "",
            city = event.city ?: "",
            state = event.state,
            postalCode = event.postalCode ?: "",
            country = event.country ?: "USA",
            isDefault = event.isDefault ?: false,
            updatedAt = event.updatedAt ?: Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
                operation = operation,
                kafkaOffset = kafkaOffset,
                kafkaPartition = kafkaPartition
            )
        )
    }
}

enum class AddressType {
    BILLING,
    SHIPPING,
    HOME,
    WORK;

    companion object {
        fun fromString(value: String?): AddressType =
            when (value?.lowercase()) {
                "billing" -> BILLING
                "shipping" -> SHIPPING
                "home" -> HOME
                "work" -> WORK
                else -> SHIPPING
            }
    }
}
