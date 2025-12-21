package com.pintailconsultingllc.cdcdebezium.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class AddressCdcEvent(
    val id: UUID,

    @JsonProperty("customer_id")
    val customerId: UUID,

    val type: String?,
    val street: String?,
    val city: String?,
    val state: String?,

    @JsonProperty("postal_code")
    val postalCode: String?,

    val country: String?,

    @JsonProperty("is_default")
    val isDefault: Boolean?,

    @JsonProperty("updated_at")
    val updatedAt: Instant?,

    @JsonProperty("__deleted")
    val deleted: String? = null,

    @JsonProperty("__op")
    val operation: String? = null,

    @JsonProperty("__source_ts_ms")
    val sourceTimestamp: Long? = null
) {
    fun isDelete(): Boolean = deleted == "true" || operation == "d"
}
