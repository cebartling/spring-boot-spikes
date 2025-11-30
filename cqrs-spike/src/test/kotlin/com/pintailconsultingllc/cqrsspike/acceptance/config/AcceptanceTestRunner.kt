package com.pintailconsultingllc.cqrsspike.acceptance.config

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * JUnit Platform Suite runner for Cucumber acceptance tests.
 *
 * This class configures and runs all Cucumber feature files located
 * in the features/acceptance directory. It integrates with JUnit 5
 * for test execution and reporting.
 *
 * Features:
 * - Runs all .feature files from the classpath resource
 * - Integrates with Gradle test task
 * - Supports tag filtering via system properties
 * - Generates reports in multiple formats
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/acceptance")
@ConfigurationParameter(
    key = "cucumber.glue",
    value = "com.pintailconsultingllc.cqrsspike.acceptance.steps"
)
@ConfigurationParameter(
    key = "cucumber.plugin",
    value = "pretty, html:build/reports/cucumber/cucumber-report.html, json:build/reports/cucumber/cucumber-report.json"
)
@ConfigurationParameter(
    key = "cucumber.publish.quiet",
    value = "true"
)
class AcceptanceTestRunner
