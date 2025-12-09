package com.pintailconsultingllc.sagapattern.acceptance.hooks

import com.pintailconsultingllc.sagapattern.acceptance.config.TestContext
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import org.springframework.beans.factory.annotation.Autowired

/**
 * Cucumber hooks for scenario lifecycle management.
 */
class ScenarioHooks {

    @Autowired
    private lateinit var testContext: TestContext

    /**
     * Runs before each scenario to set up clean test state.
     */
    @Before
    fun beforeScenario(scenario: Scenario) {
        testContext.reset()
        println("Starting scenario: ${scenario.name}")
        println("Tags: ${scenario.sourceTagNames}")
    }

    /**
     * Runs after each scenario for cleanup and logging.
     */
    @After
    fun afterScenario(scenario: Scenario) {
        println("Completed scenario: ${scenario.name}")
        println("Status: ${scenario.status}")

        if (scenario.isFailed) {
            println("Failure details - Order ID: ${testContext.orderId}")
            println("Last error: ${testContext.lastError}")
        }
    }

    /**
     * Runs before scenarios tagged with @integration to verify infrastructure.
     */
    @Before("@integration")
    fun beforeIntegrationScenario(scenario: Scenario) {
        // Verify Docker services are running
        println("Integration test - verifying infrastructure...")
        // TODO: Add health checks for PostgreSQL and WireMock
    }

    /**
     * Cleanup after compensation tests to ensure clean state.
     */
    @After("@compensation")
    fun afterCompensationScenario(scenario: Scenario) {
        // Reset any WireMock state if needed
        println("Compensation test cleanup...")
    }
}
