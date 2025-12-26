package com.pintailconsultingllc.cdcdebezium.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Configuration

/**
 * Configures OpenTelemetry Logback Appender integration.
 *
 * The OpenTelemetry Logback Appender needs to be installed after the OpenTelemetry SDK
 * is initialized by Spring Boot. This configuration class implements InitializingBean
 * to install the appender at the appropriate time during application startup.
 *
 * This enables log export via OTLP to the OpenTelemetry Collector, which forwards
 * logs to Loki for storage and querying.
 */
@Configuration
class OpenTelemetryLoggingConfig(
    private val openTelemetry: OpenTelemetry
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(OpenTelemetryLoggingConfig::class.java)

    override fun afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry)
        logger.info("OpenTelemetry Logback Appender installed for OTLP log export")
    }
}
