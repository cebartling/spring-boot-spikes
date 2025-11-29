package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Metrics for monitoring query execution.
 *
 * Implements AC11: "Custom metrics track query latency percentiles"
 */
@Component
class QueryMetrics(private val meterRegistry: MeterRegistry) {

    private val queryTimers = ConcurrentHashMap<String, Timer>()
    private val queryCounters = ConcurrentHashMap<String, Counter>()
    private val resultSizeSummaries = ConcurrentHashMap<String, DistributionSummary>()

    /**
     * Record query execution.
     */
    fun recordQuery(queryType: String, duration: Duration, resultCount: Int = 0) {
        getTimer(queryType).record(duration)
        getCounter(queryType).increment()
        if (resultCount > 0) {
            getResultSizeSummary(queryType).record(resultCount.toDouble())
        }
    }

    /**
     * Record query execution with result size.
     */
    fun recordQueryWithResultSize(queryType: String, duration: Duration, resultCount: Int) {
        recordQuery(queryType, duration, resultCount)
    }

    private fun getTimer(queryType: String): Timer {
        return queryTimers.computeIfAbsent(queryType) {
            Timer.builder("product.query.duration")
                .description("Query execution time")
                .tag("query_type", queryType)
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry)
        }
    }

    private fun getCounter(queryType: String): Counter {
        return queryCounters.computeIfAbsent(queryType) {
            Counter.builder("product.query.total")
                .description("Total query executions")
                .tag("query_type", queryType)
                .register(meterRegistry)
        }
    }

    private fun getResultSizeSummary(queryType: String): DistributionSummary {
        return resultSizeSummaries.computeIfAbsent(queryType) {
            DistributionSummary.builder("product.query.results.count")
                .description("Query result set sizes")
                .tag("query_type", queryType)
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry)
        }
    }
}
