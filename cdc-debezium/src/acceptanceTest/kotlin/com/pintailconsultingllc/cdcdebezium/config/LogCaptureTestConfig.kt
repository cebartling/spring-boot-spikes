package com.pintailconsultingllc.cdcdebezium.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Test configuration that provides a ListAppender to capture log events for assertion.
 *
 * This enables acceptance tests to verify:
 * - MDC fields are present in log output
 * - Log messages contain expected content
 * - Trace correlation fields are injected
 */
@Configuration
@Profile("test")
class LogCaptureTestConfig {

    @Bean
    fun logListAppender(): ListAppender<ILoggingEvent> {
        val listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()

        // Attach to the root logger to capture all logs
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.addAppender(listAppender)

        // Also attach to our application logger
        val appLogger = LoggerFactory.getLogger("com.pintailconsultingllc.cdcdebezium") as Logger
        appLogger.addAppender(listAppender)

        return listAppender
    }
}
