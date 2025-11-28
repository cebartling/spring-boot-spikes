package com.pintailconsultingllc.cqrsspike.product.query.projection

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Configuration properties for event projections.
 */
@Configuration
@ConfigurationProperties(prefix = "projection")
class ProjectionConfig {
    /**
     * Number of events to process in each batch.
     */
    var batchSize: Int = 100

    /**
     * Delay between polling for new events when caught up.
     */
    var pollInterval: Duration = Duration.ofSeconds(1)

    /**
     * Maximum time to wait for events before considering the projection idle.
     */
    var idleTimeout: Duration = Duration.ofMinutes(5)

    /**
     * Whether to start projections automatically on application startup.
     */
    var autoStart: Boolean = true

    /**
     * Number of retry attempts for failed event processing.
     */
    var maxRetries: Int = 3

    /**
     * Initial delay between retry attempts.
     */
    var retryDelay: Duration = Duration.ofMillis(500)

    /**
     * Multiplier for exponential backoff between retries.
     */
    var retryBackoffMultiplier: Double = 2.0

    /**
     * Maximum delay between retry attempts.
     */
    var maxRetryDelay: Duration = Duration.ofSeconds(30)

    /**
     * Lag threshold (in events) that triggers a warning.
     */
    var lagWarningThreshold: Long = 100

    /**
     * Lag threshold (in events) that triggers an error/alert.
     */
    var lagErrorThreshold: Long = 1000
}
