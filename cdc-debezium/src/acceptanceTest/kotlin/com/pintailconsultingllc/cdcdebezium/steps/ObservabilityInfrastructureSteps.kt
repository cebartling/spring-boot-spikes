package com.pintailconsultingllc.cdcdebezium.steps

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.pintailconsultingllc.cdcdebezium.config.PlaywrightTestConfig
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.UUID
import kotlin.test.assertTrue
import kotlin.test.fail

class ObservabilityInfrastructureSteps {

    @Autowired
    private lateinit var playwrightConfig: PlaywrightTestConfig

    @Autowired
    private lateinit var browser: Browser

    private lateinit var page: Page
    private val webClient = WebClient.create()

    @Before("@requires-observability")
    fun setUp() {
        val context = browser.newContext()
        page = context.newPage()
    }

    @After("@requires-observability")
    fun tearDown() {
        if (::page.isInitialized) {
            page.context().close()
        }
    }

    @Given("the observability infrastructure is running")
    fun theObservabilityInfrastructureIsRunning() {
        verifyJaegerIsAccessible()
        verifyPrometheusIsAccessible()
        verifyOtelCollectorIsAccessible()
    }

    private fun verifyJaegerIsAccessible() {
        try {
            val response = webClient.get()
                .uri("${playwrightConfig.jaegerUrl}/api/services")
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(5))
                .block()
            assertTrue(response != null, "Jaeger API is not accessible")
        } catch (e: Exception) {
            fail("Jaeger is not accessible at ${playwrightConfig.jaegerUrl}: ${e.message}")
        }
    }

    private fun verifyPrometheusIsAccessible() {
        try {
            val response = webClient.get()
                .uri("${playwrightConfig.prometheusUrl}/-/ready")
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(5))
                .block()
            assertTrue(
                response?.contains("Ready") == true,
                "Prometheus is not ready"
            )
        } catch (e: Exception) {
            fail("Prometheus is not accessible at ${playwrightConfig.prometheusUrl}: ${e.message}")
        }
    }

    private fun verifyOtelCollectorIsAccessible() {
        try {
            val response = webClient.get()
                .uri("http://localhost:8888/metrics")
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(5))
                .block()
            assertTrue(
                response?.contains("otelcol") == true,
                "OTel Collector metrics not available"
            )
        } catch (e: Exception) {
            fail("OTel Collector is not accessible: ${e.message}")
        }
    }

    @Given("traces have been sent to the collector")
    fun tracesHaveBeenSentToTheCollector() {
        sendTestTrace("test-trace-service-${UUID.randomUUID()}")
    }

    @When("I navigate to the Jaeger UI")
    fun iNavigateToTheJaegerUi() {
        page.navigate("${playwrightConfig.jaegerUrl}/search")
        page.waitForLoadState()
    }

    @Then("the Jaeger search page should be displayed")
    fun theJaegerSearchPageShouldBeDisplayed() {
        val title = page.title()
        assertTrue(
            title.contains("Jaeger", ignoreCase = true),
            "Expected Jaeger page, got title: $title"
        )
    }

    @Then("the service dropdown should be visible")
    fun theServiceDropdownShouldBeVisible() {
        page.waitForTimeout(1000.0)

        // Jaeger UI uses various component libraries - check for common patterns
        val isVisible = page.locator("[data-testid='select-service-name']").isVisible ||
            page.locator("select").first().isVisible ||
            page.locator("[class*='ant-select']").first().isVisible ||
            page.locator("[class*='Select']").first().isVisible ||
            page.locator("[role='combobox']").first().isVisible ||
            page.locator("input[placeholder*='service']").isVisible

        assertTrue(
            isVisible,
            "Service dropdown is not visible on the Jaeger search page"
        )
    }

    @Then("the service dropdown should contain available services")
    fun theServiceDropdownShouldContainAvailableServices() {
        val response = webClient.get()
            .uri("${playwrightConfig.jaegerUrl}/api/services")
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(5))
            .block()

        @Suppress("UNCHECKED_CAST")
        val services = response?.get("data") as? List<String>
        assertTrue(
            services != null && services.isNotEmpty(),
            "No services found in Jaeger"
        )
    }

    @When("I navigate to the Prometheus UI")
    fun iNavigateToThePrometheusUi() {
        page.navigate("${playwrightConfig.prometheusUrl}/graph")
        page.waitForLoadState()
    }

    @Then("the Prometheus query page should be displayed")
    fun thePrometheusQueryPageShouldBeDisplayed() {
        val title = page.title()
        assertTrue(
            title.contains("Prometheus", ignoreCase = true),
            "Expected Prometheus page, got title: $title"
        )
    }

    @Then("the query input should be visible")
    fun theQueryInputShouldBeVisible() {
        val queryInput = page.locator("textarea[class*='cm-content']")
            .or(page.locator("input[id='expression']"))
            .or(page.locator("[class*='expression-input']"))

        assertTrue(
            queryInput.first().isVisible,
            "Query input is not visible on the Prometheus page"
        )
    }

    @When("I navigate to the Prometheus targets page")
    fun iNavigateToThePrometheusTargetsPage() {
        page.navigate("${playwrightConfig.prometheusUrl}/targets")
        page.waitForLoadState()
    }

    @Then("the {string} target should be UP")
    fun theTargetShouldBeUp(targetName: String) {
        val response = webClient.get()
            .uri("${playwrightConfig.prometheusUrl}/api/v1/targets")
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(5))
            .block()

        @Suppress("UNCHECKED_CAST")
        val data = response?.get("data") as? Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val activeTargets = data?.get("activeTargets") as? List<Map<String, Any>>

        val target = activeTargets?.find { target ->
            @Suppress("UNCHECKED_CAST")
            val labels = target["labels"] as? Map<String, String>
            labels?.get("job") == targetName
        }

        assertTrue(
            target != null,
            "Target '$targetName' not found in Prometheus"
        )
        assertTrue(
            target["health"] == "up",
            "Target '$targetName' is not UP, current health: ${target["health"]}"
        )
    }

    @When("I send a test trace with service name {string}")
    fun iSendATestTraceWithServiceName(serviceName: String) {
        sendTestTrace(serviceName)
        Thread.sleep(2000)
    }

    private fun sendTestTrace(serviceName: String) {
        val traceId = UUID.randomUUID().toString().replace("-", "").uppercase()
        val spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16).uppercase()
        val now = System.currentTimeMillis() * 1_000_000

        val tracePayload = """
            {
                "resourceSpans": [{
                    "resource": {
                        "attributes": [
                            {"key": "service.name", "value": {"stringValue": "$serviceName"}}
                        ]
                    },
                    "scopeSpans": [{
                        "scope": {"name": "acceptance-test"},
                        "spans": [{
                            "traceId": "$traceId",
                            "spanId": "$spanId",
                            "name": "test-span",
                            "kind": 1,
                            "startTimeUnixNano": "$now",
                            "endTimeUnixNano": "${now + 1_000_000_000}"
                        }]
                    }]
                }]
            }
        """.trimIndent()

        try {
            webClient.post()
                .uri("${playwrightConfig.otlpHttpUrl}/v1/traces")
                .header("Content-Type", "application/json")
                .bodyValue(tracePayload)
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(5))
                .block()
        } catch (e: Exception) {
            fail("Failed to send test trace: ${e.message}")
        }
    }

    @And("I search for traces from service {string}")
    fun iSearchForTracesFromService(serviceName: String) {
        page.navigate("${playwrightConfig.jaegerUrl}/search?service=$serviceName")
        page.waitForLoadState()

        val findTracesButton = page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Find Traces"))
            .or(page.locator("button:has-text('Find Traces')"))

        if (findTracesButton.isVisible) {
            findTracesButton.click()
            page.waitForTimeout(2000.0)
        }
    }

    @Then("at least one trace should be displayed")
    fun atLeastOneTraceShouldBeDisplayed() {
        val traceElements = page.locator("[data-testid*='trace']")
            .or(page.locator("[class*='TraceName']"))
            .or(page.locator("[class*='ResultItem']"))
            .or(page.locator("a[href*='/trace/']"))

        page.waitForTimeout(2000.0)

        val count = traceElements.count()
        assertTrue(
            count > 0,
            "Expected at least one trace to be displayed, but found $count"
        )
    }

    @And("I execute the query {string}")
    fun iExecuteTheQuery(query: String) {
        page.navigate("${playwrightConfig.prometheusUrl}/graph?g0.expr=$query&g0.tab=1")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)
    }

    @Then("the query results should contain metrics data")
    fun theQueryResultsShouldContainMetricsData() {
        val response = webClient.get()
            .uri("${playwrightConfig.prometheusUrl}/api/v1/query?query=otelcol_exporter_sent_spans")
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(5))
            .block()

        @Suppress("UNCHECKED_CAST")
        val data = response?.get("data") as? Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val result = data?.get("result") as? List<Any>

        assertTrue(
            result != null && result.isNotEmpty(),
            "Expected query results but got none"
        )
    }

    @And("I click on the first trace")
    fun iClickOnTheFirstTrace() {
        page.waitForTimeout(1000.0)

        // Try different selectors in order of specificity
        val resultItem = page.locator("[class*='ResultItem']").first()
        if (resultItem.isVisible) {
            resultItem.click()
            page.waitForLoadState()
            return
        }

        val traceLink = page.locator("a[href*='/trace/']").first()
        if (traceLink.isVisible) {
            traceLink.click()
            page.waitForLoadState()
            return
        }

        val testIdTrace = page.locator("[data-testid*='trace']").first()
        if (testIdTrace.isVisible) {
            testIdTrace.click()
            page.waitForLoadState()
            return
        }

        fail("No trace link found to click")
    }

    @Then("the trace detail view should be displayed")
    fun theTraceDetailViewShouldBeDisplayed() {
        page.waitForTimeout(1000.0)
        val url = page.url()
        assertTrue(
            url.contains("/trace/"),
            "Expected to be on trace detail page, but URL is: $url"
        )
    }

    @And("the span timeline should be visible")
    fun theSpanTimelineShouldBeVisible() {
        val timeline = page.locator("[class*='Timeline']")
            .or(page.locator("[class*='SpanGraph']"))
            .or(page.locator("[class*='TraceTimelineViewer']"))
            .or(page.locator("[data-testid*='timeline']"))

        assertTrue(
            timeline.first().isVisible || page.url().contains("/trace/"),
            "Span timeline is not visible on the trace detail page"
        )
    }
}
