package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("QueryMetrics - AC11")
class QueryMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var queryMetrics: QueryMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        queryMetrics = QueryMetrics(meterRegistry)
    }

    @Nested
    @DisplayName("AC11: Custom metrics track query latency percentiles")
    inner class LatencyTracking {

        @Test
        @DisplayName("should record query execution time")
        fun shouldRecordQueryExecutionTime() {
            queryMetrics.recordQuery("findById", Duration.ofMillis(25))

            val timer = meterRegistry.find("product.query.duration")
                .tag("query_type", "findById")
                .timer()

            assertNotNull(timer)
            assertEquals(1L, timer.count())
        }

        @Test
        @DisplayName("should record query with result count")
        fun shouldRecordQueryWithResultCount() {
            queryMetrics.recordQueryWithResultSize("findAll", Duration.ofMillis(50), 25)

            val timer = meterRegistry.find("product.query.duration")
                .tag("query_type", "findAll")
                .timer()

            val summary = meterRegistry.find("product.query.results.count")
                .tag("query_type", "findAll")
                .summary()

            assertNotNull(timer)
            assertNotNull(summary)
            assertEquals(1L, timer.count())
            assertEquals(1L, summary.count())
            assertEquals(25.0, summary.totalAmount())
        }

        @Test
        @DisplayName("should track total query count")
        fun shouldTrackTotalQueryCount() {
            queryMetrics.recordQuery("search", Duration.ofMillis(30))
            queryMetrics.recordQuery("search", Duration.ofMillis(40))
            queryMetrics.recordQuery("search", Duration.ofMillis(35))

            val counter = meterRegistry.find("product.query.total")
                .tag("query_type", "search")
                .counter()

            assertNotNull(counter)
            assertEquals(3.0, counter.count())
        }

        @Test
        @DisplayName("should track different query types separately")
        fun shouldTrackDifferentQueryTypesSeparately() {
            queryMetrics.recordQuery("findById", Duration.ofMillis(20))
            queryMetrics.recordQuery("findAll", Duration.ofMillis(100))
            queryMetrics.recordQuery("findById", Duration.ofMillis(25))
            queryMetrics.recordQuery("search", Duration.ofMillis(50))

            val findByIdCounter = meterRegistry.find("product.query.total")
                .tag("query_type", "findById")
                .counter()
            val findAllCounter = meterRegistry.find("product.query.total")
                .tag("query_type", "findAll")
                .counter()
            val searchCounter = meterRegistry.find("product.query.total")
                .tag("query_type", "search")
                .counter()

            assertNotNull(findByIdCounter)
            assertNotNull(findAllCounter)
            assertNotNull(searchCounter)
            assertEquals(2.0, findByIdCounter.count())
            assertEquals(1.0, findAllCounter.count())
            assertEquals(1.0, searchCounter.count())
        }

        @Test
        @DisplayName("should not record result size when zero")
        fun shouldNotRecordResultSizeWhenZero() {
            queryMetrics.recordQuery("findById", Duration.ofMillis(25), 0)

            val summary = meterRegistry.find("product.query.results.count")
                .tag("query_type", "findById")
                .summary()

            // Summary should not be registered when resultCount is 0
            assertEquals(null, summary)
        }

        @Test
        @DisplayName("should not increment result size count when resultCount is zero after previous non-zero calls")
        fun shouldNotIncrementResultSizeWhenZeroAfterPreviousCalls() {
            // First call with non-zero resultCount registers the summary
            queryMetrics.recordQuery("findById", Duration.ofMillis(20), 5)

            val summaryAfterFirstCall = meterRegistry.find("product.query.results.count")
                .tag("query_type", "findById")
                .summary()

            assertNotNull(summaryAfterFirstCall)
            assertEquals(1L, summaryAfterFirstCall.count())
            assertEquals(5.0, summaryAfterFirstCall.totalAmount())

            // Second call with zero resultCount should not increment the summary count
            queryMetrics.recordQuery("findById", Duration.ofMillis(25), 0)

            val summaryAfterSecondCall = meterRegistry.find("product.query.results.count")
                .tag("query_type", "findById")
                .summary()

            assertNotNull(summaryAfterSecondCall)
            // Count should still be 1, not 2
            assertEquals(1L, summaryAfterSecondCall.count())
            // Total amount should still be 5.0, not changed
            assertEquals(5.0, summaryAfterSecondCall.totalAmount())
        }
    }
}
