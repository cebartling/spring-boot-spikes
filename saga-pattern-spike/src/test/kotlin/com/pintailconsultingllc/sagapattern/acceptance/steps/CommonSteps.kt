package com.pintailconsultingllc.sagapattern.acceptance.steps

import com.pintailconsultingllc.sagapattern.acceptance.config.AcceptanceTestConfig
import com.pintailconsultingllc.sagapattern.acceptance.config.TestContext
import io.cucumber.java.en.Given
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.jupiter.api.Assumptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import java.net.Socket
import java.util.UUID

/**
 * Common step definitions shared across all feature files.
 * These steps handle infrastructure setup and common preconditions.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CommonSteps(
    @Autowired private val applicationContext: ApplicationContext,
    @Autowired private val testContext: TestContext
) {

    private val wireMockWebClient: WebClient = WebClient.builder()
        .baseUrl(AcceptanceTestConfig.WIREMOCK_BASE_URL)
        .build()

    @Given("the saga pattern service is running")
    fun theSagaPatternServiceIsRunning() {
        // Verify the Spring Boot application context is loaded
        // This is handled by @SpringBootTest - if we get here, the context loaded successfully
        assert(applicationContext != null) { "Application context should be loaded" }
    }

    @Given("the inventory service is available")
    fun theInventoryServiceIsAvailable() {
        // Check if WireMock is running - skip test if not available (for CI without Docker)
        if (!isWireMockRunning()) {
            Assumptions.assumeTrue(false, "WireMock is not running at ${AcceptanceTestConfig.WIREMOCK_BASE_URL}. Start with: docker compose up -d")
        }

        // Verify WireMock inventory stubs are responding
        val response = wireMockWebClient.get()
            .uri("/__admin/mappings")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()

        assert(response?.contains("inventory") == true) {
            "Inventory service stubs should be loaded in WireMock"
        }
    }

    @Given("the payment service is available")
    fun thePaymentServiceIsAvailable() {
        // Check if WireMock is running
        if (!isWireMockRunning()) {
            Assumptions.assumeTrue(false, "WireMock is not running at ${AcceptanceTestConfig.WIREMOCK_BASE_URL}. Start with: docker compose up -d")
        }

        // Verify WireMock payment stubs are responding
        val response = wireMockWebClient.get()
            .uri("/__admin/mappings")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()

        assert(response?.contains("payment") == true) {
            "Payment service stubs should be loaded in WireMock"
        }
    }

    @Given("the shipping service is available")
    fun theShippingServiceIsAvailable() {
        // Check if WireMock is running
        if (!isWireMockRunning()) {
            Assumptions.assumeTrue(false, "WireMock is not running at ${AcceptanceTestConfig.WIREMOCK_BASE_URL}. Start with: docker compose up -d")
        }

        // Verify WireMock shipping stubs are responding
        val response = wireMockWebClient.get()
            .uri("/__admin/mappings")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()

        assert(response?.contains("shipment") == true) {
            "Shipping service stubs should be loaded in WireMock"
        }
    }

    @Given("I have a valid customer account")
    fun iHaveAValidCustomerAccount() {
        // Set up test customer context with a generated UUID
        testContext.customerId = UUID.randomUUID()
    }

    private fun isWireMockRunning(): Boolean {
        return try {
            Socket("localhost", 8081).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
