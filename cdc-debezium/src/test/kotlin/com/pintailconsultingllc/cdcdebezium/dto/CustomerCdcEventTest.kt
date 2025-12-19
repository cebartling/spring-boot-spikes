package com.pintailconsultingllc.cdcdebezium.dto

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerCdcEventTest {

    @Nested
    inner class IsDelete {

        @ParameterizedTest(name = "isDelete returns {2} when deleted={0} and operation={1}")
        @CsvSource(
            "true, , true",
            ", d, true",
            "true, d, true",
            "false, , false",
            ", c, false",
            ", u, false",
            ", r, false",
            ", , false",
            "false, u, false"
        )
        fun `isDelete returns expected result based on deleted and operation fields`(
            deleted: String?,
            operation: String?,
            expected: Boolean
        ) {
            val event = createEvent(deleted = deleted, operation = operation)
            assertEquals(expected, event.isDelete())
        }
    }

    @Nested
    inner class NullableFields {

        @Test
        fun `allows all nullable fields to be null`() {
            val event = createEvent(
                email = null,
                status = null,
                updatedAt = null,
                deleted = null,
                operation = null,
                sourceTimestamp = null
            )

            assertNull(event.email)
            assertNull(event.status)
            assertNull(event.updatedAt)
            assertNull(event.deleted)
            assertNull(event.operation)
            assertNull(event.sourceTimestamp)
        }

        @Test
        fun `sourceTimestamp defaults to null when not specified`() {
            val event = createEvent()
            assertNull(event.sourceTimestamp)
        }
    }
}
