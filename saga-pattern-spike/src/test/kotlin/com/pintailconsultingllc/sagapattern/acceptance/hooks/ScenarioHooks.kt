package com.pintailconsultingllc.sagapattern.acceptance.hooks

import com.pintailconsultingllc.sagapattern.acceptance.config.AcceptanceTestConfig
import com.pintailconsultingllc.sagapattern.acceptance.config.TestContext
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import org.junit.jupiter.api.Assumptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClient
import java.net.Socket

/**
 * Cucumber hooks for scenario lifecycle management.
 *
 * Provides setup and teardown functionality for each scenario,
 * including infrastructure health checks for integration tests.
 */
class ScenarioHooks {

    private val logger = LoggerFactory.getLogger(ScenarioHooks::class.java)

    // ANSI color codes for terminal output
    private companion object {
        private const val RESET = "\u001B[0m"
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
    }

    @Autowired
    private lateinit var testContext: TestContext

    private val wireMockWebClient: WebClient = WebClient.builder()
        .baseUrl(AcceptanceTestConfig.WIREMOCK_BASE_URL)
        .build()

    /**
     * Runs before each scenario to set up clean test state.
     */
    @Before(order = 0)
    fun beforeScenario(scenario: Scenario) {
        testContext.reset()
        logger.info("Starting scenario: ${scenario.name}")
        logger.debug("Tags: ${scenario.sourceTagNames}")
    }

    /**
     * Runs after each scenario for cleanup and logging.
     */
    @After(order = 0)
    fun afterScenario(scenario: Scenario) {
        logger.info("Completed scenario: ${scenario.name} - Status: ${scenario.status}")

        if (scenario.isFailed) {
            logger.warn("Failure details - Order ID: ${testContext.orderId}")
            logger.warn("Last error: ${testContext.lastError}")
        }
    }

    /**
     * Runs before scenarios tagged with @integration to verify infrastructure.
     * Skips tests if Docker services are not available.
     */
    @Before("@integration", order = 1)
    fun beforeIntegrationScenario(scenario: Scenario) {
        logger.info("Integration test - verifying infrastructure...")

        val unavailableServices = mutableListOf<String>()

        // Check PostgreSQL
        val postgresAvailable = isPortOpen("localhost", 5432)
        if (!postgresAvailable) {
            unavailableServices.add("PostgreSQL (localhost:5432)")
        }

        // Check WireMock
        val wireMockAvailable = isPortOpen("localhost", 8081)
        if (!wireMockAvailable) {
            unavailableServices.add("WireMock (localhost:8081)")
        }

        // If any services are unavailable, print clear message and skip
        if (unavailableServices.isNotEmpty()) {
            val message = buildInfrastructureUnavailableMessage(unavailableServices, scenario.name)
            System.err.println(message)
            System.err.flush()
            Assumptions.assumeTrue(
                false,
                "Docker infrastructure not available: ${unavailableServices.joinToString(", ")}"
            )
        }

        // Verify WireMock health
        try {
            val response = wireMockWebClient.get()
                .uri("/__admin/health")
                .retrieve()
                .toBodilessEntity()
                .block()

            if (response?.statusCode?.is2xxSuccessful != true) {
                val message = buildWireMockHealthFailedMessage(scenario.name)
                System.err.println(message)
                System.err.flush()
                Assumptions.assumeTrue(false, "WireMock health check failed")
            }
        } catch (e: Exception) {
            logger.warn("WireMock health check failed: ${e.message}")
            val message = buildWireMockHealthFailedMessage(scenario.name)
            System.err.println(message)
            System.err.flush()
            Assumptions.assumeTrue(false, "WireMock health check failed: ${e.message}")
        }

        System.out.println("$GREEN✓ PostgreSQL is available at localhost:5432$RESET")
        System.out.println("$GREEN✓ WireMock is available at localhost:8081$RESET")
        logger.info("Infrastructure verified successfully")
    }

    /**
     * Cleanup after compensation tests to ensure clean state.
     */
    @After("@compensation", order = 1)
    fun afterCompensationScenario(scenario: Scenario) {
        logger.debug("Compensation test cleanup...")
        // Reset WireMock request journal if needed for isolation
        try {
            wireMockWebClient.delete()
                .uri("/__admin/requests")
                .retrieve()
                .toBodilessEntity()
                .block()
            logger.debug("WireMock request journal cleared")
        } catch (e: Exception) {
            logger.debug("Could not clear WireMock request journal: ${e.message}")
        }
    }

    /**
     * Runs before observability scenarios to log tracing context.
     */
    @Before("@observability", order = 1)
    fun beforeObservabilityScenario(scenario: Scenario) {
        logger.info("Observability test - tracing/metrics validation will be performed")
    }

    /**
     * Runs before retry scenarios to ensure clean retry state.
     */
    @Before("@retry", order = 1)
    fun beforeRetryScenario(scenario: Scenario) {
        logger.debug("Retry test - resetting retry context")
        testContext.retryEligibilityResponse = null
        testContext.retryResponse = null
        testContext.retryHistoryResponse = null
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun buildInfrastructureUnavailableMessage(unavailableServices: List<String>, scenarioName: String): String {
        val boxWidth = 78
        val border = "═".repeat(boxWidth)
        val emptyLine = "║" + " ".repeat(boxWidth) + "║"

        fun padLine(content: String): String {
            val padding = boxWidth - content.length
            return "║  $content${" ".repeat(maxOf(0, padding - 2))}║"
        }

        val title = "ACCEPTANCE TEST SKIPPED - Docker Infrastructure Not Available"

        return buildString {
            appendLine()
            appendLine("$RED╔$border╗$RESET")
            appendLine("$RED${padLine(title)}$RESET")
            appendLine("$RED╠$border╣$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("Scenario: $scenarioName")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("The following Docker services are required but not available:")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            unavailableServices.forEach { service ->
                appendLine("$RED${padLine("  • $service")}$RESET")
            }
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("To start the Docker infrastructure, run:")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("    docker compose up -d")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("Then re-run the acceptance tests:")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("    ./gradlew acceptanceTest")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED╚$border╝$RESET")
            appendLine()
        }
    }

    private fun buildWireMockHealthFailedMessage(scenarioName: String): String {
        val boxWidth = 78
        val border = "═".repeat(boxWidth)
        val emptyLine = "║" + " ".repeat(boxWidth) + "║"

        fun padLine(content: String): String {
            val padding = boxWidth - content.length
            return "║  $content${" ".repeat(maxOf(0, padding - 2))}║"
        }

        val title = "ACCEPTANCE TEST SKIPPED - WireMock Health Check Failed"

        return buildString {
            appendLine()
            appendLine("$RED╔$border╗$RESET")
            appendLine("$RED${padLine(title)}$RESET")
            appendLine("$RED╠$border╣$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("Scenario: $scenarioName")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("WireMock is running but health check failed.")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("Try restarting the Docker infrastructure:")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("    docker compose restart wiremock")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("Or reset all services:")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED${padLine("    docker compose down && docker compose up -d")}$RESET")
            appendLine("$RED$emptyLine$RESET")
            appendLine("$RED╚$border╝$RESET")
            appendLine()
        }
    }
}
