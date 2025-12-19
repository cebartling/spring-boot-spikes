package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
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
    private var lastCounterValue: Long = 0
    private var lastAttributes: Map<String, String> = emptyMap()

    @Given("the metrics infrastructure is initialized")
    fun theMetricsInfrastructureIsInitialized() {
        assertNotNull(metricReader)
        assertNotNull(metricsService)
    }

    @And("the metric reader is cleared")
    fun theMetricReaderIsCleared() {
        metricReader.collectAllMetrics()
        lastMetricName = null
        lastCounterValue = 0
        lastAttributes = emptyMap()
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

        val sum = metric.longSumData.points.sumOf { it.value }
        lastCounterValue = sum
        assertEquals(expectedValue, sum, "Counter '$metricName' should have value $expectedValue")

        metric.longSumData.points.firstOrNull()?.let { point ->
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

        val sum = metric.longSumData.points.sumOf { it.value }
        assertEquals(expectedValue, sum, "Counter '$metricName' should have total value $expectedValue")
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

        val totalCount = metric.histogramData.points.sumOf { it.count }
        assertTrue(totalCount > 0, "Histogram '$metricName' should have recorded at least one value")

        metric.histogramData.points.firstOrNull()?.let { point ->
            lastAttributes = point.attributes.asMap().entries.associate {
                it.key.key to it.value.toString()
            }
        }
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

        val point = metric.longSumData.points.firstOrNull()
        assertNotNull(point, "Metric '$metricName' should have at least one data point")

        val attributes = point.attributes.asMap().entries.associate {
            it.key.key to it.value.toString()
        }
        val actualValue = attributes[attributeName]
        assertEquals(
            expectedValue,
            actualValue,
            "Counter '$metricName' attribute '$attributeName' should be '$expectedValue' but was '$actualValue'"
        )
    }

    private fun findMetric(metrics: Collection<MetricData>, name: String): MetricData? {
        return metrics.find { it.name == name }
    }
}
