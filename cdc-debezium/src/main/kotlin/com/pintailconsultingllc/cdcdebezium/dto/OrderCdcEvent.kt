package com.pintailconsultingllc.cdcdebezium.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderCdcEvent(
    val id: UUID,

    @JsonProperty("customer_id")
    val customerId: UUID,

    val status: String?,

    @JsonProperty("total_amount")
    val totalAmount: BigDecimal?,

    @JsonProperty("created_at")
    val createdAt: Instant?,

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
