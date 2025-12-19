package com.pintailconsultingllc.cdcdebezium.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("customer_materialized")
data class CustomerEntity(
    @Id
    val id: UUID,
    val email: String,
    val status: String,
    val updatedAt: Instant,
    val sourceTimestamp: Long? = null
)
