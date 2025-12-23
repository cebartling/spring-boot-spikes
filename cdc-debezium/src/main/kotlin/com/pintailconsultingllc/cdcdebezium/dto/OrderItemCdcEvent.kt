package com.pintailconsultingllc.cdcdebezium.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.util.UUID

data class OrderItemCdcEvent(
    val id: UUID,

    @JsonProperty("order_id")
    val orderId: UUID,

    @JsonProperty("product_sku")
    val productSku: String?,

    @JsonProperty("product_name")
    val productName: String?,

    val quantity: Int?,

    @JsonProperty("unit_price")
    val unitPrice: BigDecimal?,

    @JsonProperty("line_total")
    val lineTotal: BigDecimal?,

    @JsonProperty("__deleted")
    val deleted: String? = null,

    @JsonProperty("__op")
    val operation: String? = null,

    @JsonProperty("__source_ts_ms")
    val sourceTimestamp: Long? = null
) {
    fun isDelete(): Boolean = deleted == "true" || operation == "d"
}
