package com.pintailconsultingllc.sagapattern.acceptance

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import io.cucumber.junit.platform.engine.Constants

/**
 * Cucumber test runner for acceptance tests.
 *
 * Run all acceptance tests:
 *   ./gradlew test --tests "*.CucumberTestRunner"
 *
 * Run specific feature by tag:
 *   ./gradlew test --tests "*.CucumberTestRunner" -Dcucumber.filter.tags="@saga-001"
 *
 * Available tags:
 *   @saga         - All saga pattern tests
 *   @saga-001     - Multi-step order process tests
 *   @saga-002     - Automatic rollback tests
 *   @saga-003     - View order status tests
 *   @saga-004     - Retry failed orders tests
 *   @saga-005     - Order history tests
 *   @happy-path   - Success scenario tests
 *   @compensation - Compensation/rollback tests
 *   @retry        - Retry functionality tests
 *   @history      - History/timeline tests
 *   @integration  - Integration tests requiring infrastructure
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
    key = Constants.GLUE_PROPERTY_NAME,
    value = "com.pintailconsultingllc.sagapattern.acceptance.steps"
)
@ConfigurationParameter(
    key = Constants.PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber/cucumber-report.html, json:build/reports/cucumber/cucumber-report.json"
)
@ConfigurationParameter(
    key = Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME,
    value = "true"
)
class CucumberTestRunner
