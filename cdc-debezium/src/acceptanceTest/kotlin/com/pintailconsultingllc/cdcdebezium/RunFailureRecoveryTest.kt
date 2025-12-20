package com.pintailconsultingllc.cdcdebezium

import io.cucumber.junit.platform.engine.Constants
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/failure_recovery.feature")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.pintailconsultingllc.cdcdebezium")
@ConfigurationParameter(
    key = Constants.PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber/failure-recovery.html, json:build/reports/cucumber/failure-recovery.json"
)
@ConfigurationParameter(key = Constants.JUNIT_PLATFORM_NAMING_STRATEGY_PROPERTY_NAME, value = "long")
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "@failure-recovery")
class RunFailureRecoveryTest
