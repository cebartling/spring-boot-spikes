package com.pintailconsultingllc.cdcdebezium.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class CustomerCdcEvent(
    val id: UUID,
    val email: String?,
    val status: String?,
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
