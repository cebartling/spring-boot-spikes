package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenTelemetryMetricsSteps {

    @Autowired
    private lateinit var metricReader: InMemoryMetricReader

    @Autowired
    private lateinit var metricsService: CdcMetricsService

    private var lastMetricName: String? = null
    private var lastAttributes: Map<String, String> = emptyMap()

    // Baseline values per metric and attribute combination
    // Key: "metricName|attr1=val1,attr2=val2", Value: counter value
    private var baselinePointValues: Map<String, Long> = emptyMap()
    private var baselineHistogramCounts: Map<String, Long> = emptyMap()

    @Given("the metrics infrastructure is initialized")
    fun theMetricsInfrastructureIsInitialized() {
        assertNotNull(metricReader)
        assertNotNull(metricsService)
    }

    @And("the metric reader is cleared")
    fun theMetricReaderIsCleared() {
        // Capture baseline values for all metric points (per attribute combination)
        val metrics = metricReader.collectAllMetrics()

        baselinePointValues = mutableMapOf<String, Long>().apply {
            metrics.filter { it.longSumData.points.isNotEmpty() }.forEach { metric ->
                metric.longSumData.points.forEach { point ->
                    val key = buildPointKey(metric.name, point)
                    this[key] = point.value
                }
            }
        }

        baselineHistogramCounts = mutableMapOf<String, Long>().apply {
            metrics.filter { it.histogramData.points.isNotEmpty() }.forEach { metric ->
                metric.histogramData.points.forEach { point ->
                    val attrs = point.attributes.asMap().entries
                        .sortedBy { it.key.key }
                        .joinToString(",") { "${it.key.key}=${it.value}" }
                    val key = "${metric.name}|$attrs"
                    this[key] = point.count
                }
            }
        }

        lastMetricName = null
        lastAttributes = emptyMap()
    }

    private fun buildPointKey(metricName: String, point: LongPointData): String {
        val attrs = point.attributes.asMap().entries
            .sortedBy { it.key.key }
            .joinToString(",") { "${it.key.key}=${it.value}" }
        return "$metricName|$attrs"
    }

    @When("a CDC insert event is processed for metrics")
    fun aCdcInsertEventIsProcessedForMetrics() {
        metricsService.recordMessageProcessed("cdc.public.customer", 0, "upsert")
    }

    @When("a CDC update event is processed for metrics")
    fun aCdcUpdateEventIsProcessedForMetrics() {
        metricsService.recordMessageProcessed("cdc.public.customer", 0, "upsert")
    }

    @When("a CDC delete event is processed for metrics")
    fun aCdcDeleteEventIsProcessedForMetrics() {
        metricsService.recordMessageProcessed("cdc.public.customer", 0, "delete")
    }

    @When("a tombstone event is processed for metrics")
    fun aTombstoneEventIsProcessedForMetrics() {
        metricsService.recordMessageProcessed("cdc.public.customer", 0, "ignore")
    }

    @When("a CDC event is processed with simulated latency")
    fun aCdcEventIsProcessedWithSimulatedLatency() {
        val startTime = Instant.now().minusMillis(100)
        metricsService.recordProcessingLatency(startTime, "cdc.public.customer", 0)
    }

    @When("a CDC event processing fails for metrics")
    fun aCdcEventProcessingFailsForMetrics() {
        metricsService.recordMessageError("cdc.public.customer", 0)
    }

    @When("a CDC upsert operation is recorded")
    fun aCdcUpsertOperationIsRecorded() {
        metricsService.recordDbUpsert()
    }

    @When("a CDC delete operation is recorded")
    fun aCdcDeleteOperationIsRecorded() {
        metricsService.recordDbDelete()
    }

    @When("{int} CDC insert events are processed for metrics")
    fun multipleCdcInsertEventsAreProcessedForMetrics(count: Int) {
        repeat(count) {
            metricsService.recordMessageProcessed("cdc.public.customer", 0, "upsert")
        }
    }

    @When("a CDC event is processed on partition {int} for metrics")
    fun aCdcEventIsProcessedOnPartitionForMetrics(partition: Int) {
        metricsService.recordMessageProcessed("cdc.public.customer", partition, "upsert")
    }

    @Then("the counter {string} should have value {long}")
    fun theCounterShouldHaveValue(metricName: String, expectedValue: Long) {
        lastMetricName = metricName
        val metrics = metricReader.collectAllMetrics()
        val metric = findMetric(metrics, metricName)
        assertNotNull(metric, "Metric '$metricName' should exist")

        // Find the point that was incremented in this scenario
        var foundPoint: LongPointData? = null
        var totalDelta = 0L

        metric.longSumData.points.forEach { point ->
            val key = buildPointKey(metricName, point)
            val baseline = baselinePointValues[key] ?: 0L
            val delta = point.value - baseline
            if (delta > 0) {
                totalDelta += delta
                foundPoint = point
            }
        }

        assertEquals(
            expectedValue,
            totalDelta,
            "Counter '$metricName' should have incremented by $expectedValue"
        )

        // Store the found point's attributes for subsequent attribute checks
        foundPoint?.let { point ->
            lastAttributes = point.attributes.asMap().entries.associate {
                it.key.key to it.value.toString()
            }
        }
    }

    @Then("the counter {string} should have total value {long}")
    fun theCounterShouldHaveTotalValue(metricName: String, expectedValue: Long) {
        lastMetricName = metricName
        val metrics = metricReader.collectAllMetrics()
        val metric = findMetric(metrics, metricName)
        assertNotNull(metric, "Metric '$metricName' should exist")

        var totalDelta = 0L
        metric.longSumData.points.forEach { point ->
            val key = buildPointKey(metricName, point)
            val baseline = baselinePointValues[key] ?: 0L
            totalDelta += point.value - baseline
        }

        assertEquals(
            expectedValue,
            totalDelta,
            "Counter '$metricName' should have incremented by $expectedValue total"
        )
    }

    @And("the counter should have attribute {string} with value {string}")
    fun theCounterShouldHaveAttributeWithValue(attributeName: String, expectedValue: String) {
        val actualValue = lastAttributes[attributeName]
        assertEquals(
            expectedValue,
            actualValue,
            "Counter attribute '$attributeName' should be '$expectedValue' but was '$actualValue'"
        )
    }

    @Then("the histogram {string} should have recorded a value")
    fun theHistogramShouldHaveRecordedAValue(metricName: String) {
        lastMetricName = metricName
        val metrics = metricReader.collectAllMetrics()
        val metric = findMetric(metrics, metricName)
        assertNotNull(metric, "Histogram '$metricName' should exist")

        var foundDelta = false
        metric.histogramData.points.forEach { point ->
            val attrs = point.attributes.asMap().entries
                .sortedBy { it.key.key }
                .joinToString(",") { "${it.key.key}=${it.value}" }
            val key = "$metricName|$attrs"
            val baseline = baselineHistogramCounts[key] ?: 0L
            val delta = point.count - baseline
            if (delta > 0) {
                foundDelta = true
                lastAttributes = point.attributes.asMap().entries.associate {
                    it.key.key to it.value.toString()
                }
            }
        }

        assertTrue(foundDelta, "Histogram '$metricName' should have recorded at least one new value")
    }

    @And("the histogram should have attribute {string} with value {string}")
    fun theHistogramShouldHaveAttributeWithValue(attributeName: String, expectedValue: String) {
        val actualValue = lastAttributes[attributeName]
        assertEquals(
            expectedValue,
            actualValue,
            "Histogram attribute '$attributeName' should be '$expectedValue' but was '$actualValue'"
        )
    }

    @Then("the counter {string} should have attribute {string} with value {string}")
    fun theCounterShouldHaveAttributeWithValue(
        metricName: String,
        attributeName: String,
        expectedValue: String
    ) {
        val metrics = metricReader.collectAllMetrics()
        val metric = findMetric(metrics, metricName)
        assertNotNull(metric, "Metric '$metricName' should exist")

        // Find the point with matching attribute value that was incremented
        val matchingPoint = metric.longSumData.points.find { point ->
            val key = buildPointKey(metricName, point)
            val baseline = baselinePointValues[key] ?: 0L
            val wasIncremented = point.value > baseline

            val attrs = point.attributes.asMap().entries.associate {
                it.key.key to it.value.toString()
            }
            wasIncremented && attrs[attributeName] == expectedValue
        }

        assertNotNull(
            matchingPoint,
            "Counter '$metricName' should have a point with attribute '$attributeName' = '$expectedValue' that was incremented"
        )
    }

    private fun findMetric(metrics: Collection<MetricData>, name: String): MetricData? {
        return metrics.find { it.name == name }
    }
}
