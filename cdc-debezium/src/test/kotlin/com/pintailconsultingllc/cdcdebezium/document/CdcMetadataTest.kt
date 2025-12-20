package com.pintailconsultingllc.cdcdebezium.document

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CdcMetadataTest {

    @Test
    fun `creates CdcMetadata with all fields`() {
        val sourceTimestamp = 1705315800000L
        val operation = CdcOperation.INSERT
        val kafkaOffset = 100L
        val kafkaPartition = 0
        val beforeCreation = Instant.now()

        val metadata = CdcMetadata(
            sourceTimestamp = sourceTimestamp,
            operation = operation,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )

        assertEquals(sourceTimestamp, metadata.sourceTimestamp)
        assertEquals(operation, metadata.operation)
        assertEquals(kafkaOffset, metadata.kafkaOffset)
        assertEquals(kafkaPartition, metadata.kafkaPartition)
        assertTrue(metadata.processedAt >= beforeCreation)
    }

    @Test
    fun `supports all CDC operations`() {
        CdcOperation.entries.forEach { operation ->
            val metadata = CdcMetadata(
                sourceTimestamp = 1000L,
                operation = operation,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
            assertEquals(operation, metadata.operation)
        }
    }

    @Test
    fun `CdcOperation enum has expected values`() {
        val operations = CdcOperation.entries
        assertEquals(3, operations.size)
        assertTrue(operations.contains(CdcOperation.INSERT))
        assertTrue(operations.contains(CdcOperation.UPDATE))
        assertTrue(operations.contains(CdcOperation.DELETE))
    }
}
