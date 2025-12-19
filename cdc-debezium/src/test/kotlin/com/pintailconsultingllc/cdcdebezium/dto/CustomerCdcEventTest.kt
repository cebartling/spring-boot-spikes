package com.pintailconsultingllc.cdcdebezium.dto

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomerCdcEventTest {

    private val testId = UUID.randomUUID()
    private val testEmail = "test@example.com"
    private val testStatus = "active"
    private val testUpdatedAt = Instant.now()

    @Nested
    inner class IsDelete {

        @Test
        fun `returns true when deleted field is true`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = "true",
                operation = null
            )

            assertTrue(event.isDelete())
        }

        @Test
        fun `returns true when operation is d`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = null,
                operation = "d"
            )

            assertTrue(event.isDelete())
        }

        @Test
        fun `returns true when both deleted is true and operation is d`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = "true",
                operation = "d"
            )

            assertTrue(event.isDelete())
        }

        @Test
        fun `returns false when deleted is false`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = "false",
                operation = null
            )

            assertFalse(event.isDelete())
        }

        @Test
        fun `returns false when operation is c for create`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = null,
                operation = "c"
            )

            assertFalse(event.isDelete())
        }

        @Test
        fun `returns false when operation is u for update`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = null,
                operation = "u"
            )

            assertFalse(event.isDelete())
        }

        @Test
        fun `returns false when operation is r for read (snapshot)`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = null,
                operation = "r"
            )

            assertFalse(event.isDelete())
        }

        @Test
        fun `returns false when both deleted and operation are null`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = null,
                operation = null
            )

            assertFalse(event.isDelete())
        }

        @Test
        fun `returns false for upsert event with all fields populated`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt,
                deleted = "false",
                operation = "u",
                sourceTimestamp = System.currentTimeMillis()
            )

            assertFalse(event.isDelete())
        }
    }

    @Nested
    inner class DataClassProperties {

        @Test
        fun `allows nullable email`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = null,
                status = testStatus,
                updatedAt = testUpdatedAt
            )

            assertTrue(event.email == null)
        }

        @Test
        fun `allows nullable status`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = null,
                updatedAt = testUpdatedAt
            )

            assertTrue(event.status == null)
        }

        @Test
        fun `allows nullable updatedAt`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = null
            )

            assertTrue(event.updatedAt == null)
        }

        @Test
        fun `sourceTimestamp defaults to null`() {
            val event = CustomerCdcEvent(
                id = testId,
                email = testEmail,
                status = testStatus,
                updatedAt = testUpdatedAt
            )

            assertTrue(event.sourceTimestamp == null)
        }
    }
}
