package com.pintailconsultingllc.cdcdebezium.validation.rules

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemporalConsistencyRuleTest {

    private lateinit var rule: TemporalConsistencyRule

    @BeforeEach
    fun setUp() {
        rule = TemporalConsistencyRule()
    }

    @Test
    fun `has correct rule id`() {
        assertEquals("TEMPORAL_001", rule.ruleId)
    }

    @Test
    fun `continues on failure`() {
        assertTrue(rule.continueOnFailure)
    }

    @Nested
    inner class ValidTimestamps {

        @Test
        fun `passes validation for recent timestamp`() {
            val recentTimestamp = System.currentTimeMillis() - 1000
            val event = createEvent(sourceTimestamp = recentTimestamp)

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Temporal validation passed", result.message)
        }

        @Test
        fun `passes validation for timestamp within clock drift`() {
            val nearFutureTimestamp = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis()
            val event = createEvent(sourceTimestamp = nearFutureTimestamp)

            val result = rule.validate(event)

            assertTrue(result.valid)
        }
    }

    @Nested
    inner class FutureTimestamps {

        @Test
        fun `fails validation for timestamp far in future`() {
            val farFutureTimestamp = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis()
            val event = createEvent(sourceTimestamp = farFutureTimestamp)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.any { it.contains("in the future") })
        }

        @Test
        fun `fails validation for updatedAt far in future`() {
            val futureUpdatedAt = Instant.now().plus(Duration.ofMinutes(10))
            val event = createEvent(updatedAt = futureUpdatedAt, sourceTimestamp = System.currentTimeMillis())

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertTrue(errors.any { it.contains("updatedAt timestamp is in the future") })
        }
    }

    @Nested
    inner class OldTimestamps {

        @Test
        fun `passes with warning for old timestamp`() {
            val oldTimestamp = System.currentTimeMillis() - Duration.ofHours(25).toMillis()
            val event = createEvent(sourceTimestamp = oldTimestamp)

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertTrue(result.message?.contains("warnings") == true)
        }
    }

    @Nested
    inner class MissingTimestamps {

        @Test
        fun `passes with warning when source timestamp is missing`() {
            val event = createEvent(sourceTimestamp = null)

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertTrue(result.message?.contains("warnings") == true)
        }

        @Test
        fun `passes when updatedAt is null`() {
            val event = createEvent(updatedAt = null, sourceTimestamp = System.currentTimeMillis())

            val result = rule.validate(event)

            assertTrue(result.valid)
            assertEquals("Temporal validation passed", result.message)
        }
    }

    @Nested
    inner class MultipleIssues {

        @Test
        fun `collects multiple errors`() {
            val futureTimestamp = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis()
            val futureUpdatedAt = Instant.now().plus(Duration.ofMinutes(10))
            val event = createEvent(sourceTimestamp = futureTimestamp, updatedAt = futureUpdatedAt)

            val result = rule.validate(event)

            assertFalse(result.valid)
            @Suppress("UNCHECKED_CAST")
            val errors = result.details["errors"] as List<String>
            assertEquals(2, errors.size)
        }
    }
}
