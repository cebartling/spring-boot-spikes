package com.pintailconsultingllc.cdcdebezium.document

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomerDocumentTest {

    private val testId = "550e8400-e29b-41d4-a716-446655440000"
    private val testEmail = "test@example.com"
    private val testStatus = "active"
    private val testUpdatedAt = Instant.parse("2024-01-15T10:30:00Z")
    private val testSourceTimestamp = 1705315800000L
    private val testKafkaOffset = 100L
    private val testKafkaPartition = 0

    @Nested
    inner class FromCdcEvent {

        @Test
        fun `creates CustomerDocument with correct fields from CDC event data`() {
            val document = CustomerDocument.fromCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                sourceTimestamp = testSourceTimestamp,
                operation = CdcOperation.INSERT,
                kafkaOffset = testKafkaOffset,
                kafkaPartition = testKafkaPartition
            )

            assertEquals(testId, document.id)
            assertEquals(testEmail, document.email)
            assertEquals(testStatus, document.status)
            assertEquals(testUpdatedAt, document.updatedAt)
            assertEquals(testSourceTimestamp, document.cdcMetadata.sourceTimestamp)
            assertEquals(CdcOperation.INSERT, document.cdcMetadata.operation)
            assertEquals(testKafkaOffset, document.cdcMetadata.kafkaOffset)
            assertEquals(testKafkaPartition, document.cdcMetadata.kafkaPartition)
        }

        @Test
        fun `creates document with UPDATE operation`() {
            val document = CustomerDocument.fromCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                sourceTimestamp = testSourceTimestamp,
                operation = CdcOperation.UPDATE,
                kafkaOffset = testKafkaOffset,
                kafkaPartition = testKafkaPartition
            )

            assertEquals(CdcOperation.UPDATE, document.cdcMetadata.operation)
        }

        @Test
        fun `creates document with DELETE operation`() {
            val document = CustomerDocument.fromCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                sourceTimestamp = testSourceTimestamp,
                operation = CdcOperation.DELETE,
                kafkaOffset = testKafkaOffset,
                kafkaPartition = testKafkaPartition
            )

            assertEquals(CdcOperation.DELETE, document.cdcMetadata.operation)
        }
    }

    @Nested
    inner class IsNewerThan {

        @Test
        fun `returns true when this document has higher source timestamp`() {
            val older = createDocument(sourceTimestamp = 1000L)
            val newer = createDocument(sourceTimestamp = 2000L)

            assertTrue(newer.isNewerThan(older))
        }

        @Test
        fun `returns false when this document has lower source timestamp`() {
            val older = createDocument(sourceTimestamp = 1000L)
            val newer = createDocument(sourceTimestamp = 2000L)

            assertFalse(older.isNewerThan(newer))
        }

        @Test
        fun `returns false when documents have same source timestamp`() {
            val doc1 = createDocument(sourceTimestamp = 1000L)
            val doc2 = createDocument(sourceTimestamp = 1000L)

            assertFalse(doc1.isNewerThan(doc2))
        }
    }

    private fun createDocument(
        id: String = testId,
        email: String = testEmail,
        status: String = testStatus,
        updatedAt: Instant = testUpdatedAt,
        sourceTimestamp: Long = testSourceTimestamp,
        operation: CdcOperation = CdcOperation.INSERT,
        kafkaOffset: Long = testKafkaOffset,
        kafkaPartition: Int = testKafkaPartition
    ): CustomerDocument = CustomerDocument.fromCdcEvent(
        id = id,
        email = email,
        status = status,
        updatedAt = updatedAt,
        sourceTimestamp = sourceTimestamp,
        operation = operation,
        kafkaOffset = kafkaOffset,
        kafkaPartition = kafkaPartition
    )
}
