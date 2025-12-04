package com.pintailconsultingllc.cqrsspike.acceptance.steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import com.pintailconsultingllc.cqrsspike.acceptance.helpers.ResponseParsingHelper
import com.pintailconsultingllc.cqrsspike.product.api.dto.CreateProductRequest
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Step definitions for observability platform acceptance tests.
 *
 * Covers:
 * - Health endpoint verification
 * - Prometheus metrics endpoint
 * - Custom product metrics validation
 * - Metrics endpoint and individual metric retrieval
 * - Correlation ID propagation
 * - Info endpoint
 */
class ObservabilitySteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var testContext: TestContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var responseParsingHelper: ResponseParsingHelper

    // Storage for prometheus metrics response
    private var prometheusMetrics: String? = null

    // ========== When Steps ==========

    @When("I check the health endpoint")
    fun iCheckTheHealthEndpoint() {
        val response = webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
    }

    @When("I request the prometheus metrics endpoint")
    fun iRequestThePrometheusMetricsEndpoint() {
        val response = webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        // Collect all chunks of the prometheus response (it can be large and chunked)
        prometheusMetrics = response.responseBody
            .collectList()
            .block()
            ?.joinToString("")
        testContext.lastResponseBody = prometheusMetrics
    }

    @When("I request the metrics endpoint")
    fun iRequestTheMetricsEndpoint() {
        val response = webTestClient.get()
            .uri("/actuator/metrics")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
    }

    @When("I request the metric {string}")
    fun iRequestTheMetric(metricName: String) {
        val response = webTestClient.get()
            .uri("/actuator/metrics/{name}", metricName)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
    }

    @When("I request the info endpoint")
    fun iRequestTheInfoEndpoint() {
        val response = webTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
    }

    @When("I retrieve the created product")
    fun iRetrieveTheCreatedProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        // Use retry logic to handle eventual consistency
        val maxAttempts = 10
        val initialDelayMs = 100L

        for (attempt in 1..maxAttempts) {
            Thread.sleep(initialDelayMs * attempt)

            val response = webTestClient.get()
                .uri("/api/products/{id}", productId)
                .exchange()
                .returnResult(String::class.java)

            val body = response.responseBody.blockFirst()
            if (response.status.is2xxSuccessful && body != null && body.contains("\"id\":")) {
                testContext.lastResponseStatus = response.status
                testContext.lastResponseBody = body
                return
            }
        }

        // If we get here, product was not found
        testContext.lastResponseStatus = org.springframework.http.HttpStatus.NOT_FOUND
    }

    @When("I create a product with correlation ID {string}")
    fun iCreateAProductWithCorrelationId(correlationId: String) {
        val request = CreateProductRequest(
            sku = "CORR-${System.currentTimeMillis()}",
            name = "Correlation Test Product",
            description = null,
            priceCents = 1999
        )

        val response = webTestClient.post()
            .uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Correlation-ID", correlationId)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()

        // Extract correlation ID from response headers
        val correlationHeader = response.responseHeaders["X-Correlation-ID"]?.firstOrNull()
        if (correlationHeader != null) {
            testContext.lastResponseHeaders["X-Correlation-ID"] = correlationHeader
        }

        responseParsingHelper.extractProductIdFromResponse()
    }

    @When("I retrieve a product with non-existent ID using correlation ID {string}")
    fun iRetrieveAProductWithNonExistentIdUsingCorrelationId(correlationId: String) {
        val nonExistentId = java.util.UUID.randomUUID()

        val response = webTestClient.get()
            .uri("/api/products/{id}", nonExistentId)
            .header("X-Correlation-ID", correlationId)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()

        // Extract correlation ID from response headers
        val correlationHeader = response.responseHeaders["X-Correlation-ID"]?.firstOrNull()
        if (correlationHeader != null) {
            testContext.lastResponseHeaders["X-Correlation-ID"] = correlationHeader
        }

        responseParsingHelper.parseErrorResponse()
    }

    // ========== Then Steps ==========

    @Then("the health status should be {string}")
    fun theHealthStatusShouldBe(expectedStatus: String) {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Health response body")
            .isNotNull

        val jsonNode = objectMapper.readTree(body)
        val status = jsonNode.get("status")?.asText()

        assertThat(status)
            .describedAs("Health status")
            .isEqualTo(expectedStatus)
    }

    @And("the health response should include component {string}")
    fun theHealthResponseShouldIncludeComponent(componentName: String) {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Health response body")
            .isNotNull

        val jsonNode = objectMapper.readTree(body)
        val components = jsonNode.get("components")

        assertThat(components)
            .describedAs("Components node")
            .isNotNull

        assertThat(components.has(componentName))
            .describedAs("Component '$componentName' should exist")
            .isTrue
    }

    @And("the response should contain prometheus metrics")
    fun theResponseShouldContainPrometheusMetrics() {
        assertThat(prometheusMetrics)
            .describedAs("Prometheus metrics response")
            .isNotNull
            .isNotEmpty

        // Prometheus metrics format should contain typical metric entries
        assertThat(prometheusMetrics)
            .describedAs("Prometheus metrics should contain metric definitions")
            .contains("# HELP")
            .contains("# TYPE")
    }

    @And("the prometheus metrics should include {string}")
    fun thePrometheusMetricsShouldInclude(metricPrefix: String) {
        assertThat(prometheusMetrics)
            .describedAs("Prometheus metrics should include '$metricPrefix'")
            .isNotNull
            .contains(metricPrefix)
    }

    @And("the metrics list should not be empty")
    fun theMetricsListShouldNotBeEmpty() {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Metrics response body")
            .isNotNull

        val jsonNode = objectMapper.readTree(body)
        val names = jsonNode.get("names")

        assertThat(names)
            .describedAs("Metrics names")
            .isNotNull

        assertThat(names.isArray)
            .describedAs("Names should be an array")
            .isTrue

        assertThat(names.size())
            .describedAs("Metrics list size")
            .isGreaterThan(0)
    }

    @And("the metric response should contain measurements")
    fun theMetricResponseShouldContainMeasurements() {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Metric response body")
            .isNotNull

        val jsonNode = objectMapper.readTree(body)
        val measurements = jsonNode.get("measurements")

        assertThat(measurements)
            .describedAs("Measurements node")
            .isNotNull

        assertThat(measurements.isArray)
            .describedAs("Measurements should be an array")
            .isTrue

        assertThat(measurements.size())
            .describedAs("Measurements should not be empty")
            .isGreaterThan(0)
    }

    @And("the response should include correlation ID header")
    fun theResponseShouldIncludeCorrelationIdHeader() {
        val correlationId = testContext.lastResponseHeaders["X-Correlation-ID"]
        assertThat(correlationId)
            .describedAs("X-Correlation-ID header should be present")
            .isNotNull
            .isNotEmpty
    }
}
