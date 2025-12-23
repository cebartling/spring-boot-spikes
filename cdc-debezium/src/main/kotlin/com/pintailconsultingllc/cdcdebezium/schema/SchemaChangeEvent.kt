package com.pintailconsultingllc.cdcdebezium.schema

import java.time.Instant

data class SchemaChangeEvent(
    val entityType: String,
    val changeType: SchemaChangeType,
    val fieldName: String?,
    val oldValue: Any?,
    val newValue: Any?,
    val detectedAt: Instant = Instant.now(),
    val kafkaOffset: Long,
    val kafkaPartition: Int
)

enum class SchemaChangeType {
    NEW_FIELD,
    REMOVED_FIELD,
    TYPE_CHANGE,
    NEW_ENUM_VALUE,
    UNKNOWN
}

data class SchemaVersion(
    val entityType: String,
    val version: Int,
    val fields: Set<String>,
    val detectedAt: Instant = Instant.now()
)
