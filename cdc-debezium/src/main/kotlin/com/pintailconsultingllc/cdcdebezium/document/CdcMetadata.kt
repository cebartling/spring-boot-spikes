package com.pintailconsultingllc.cdcdebezium.document

import java.time.Instant

data class CdcMetadata(
    val sourceTimestamp: Long,
    val operation: CdcOperation,
    val kafkaOffset: Long,
    val kafkaPartition: Int,
    val processedAt: Instant = Instant.now()
)

enum class CdcOperation {
    INSERT,
    UPDATE,
    DELETE
}
