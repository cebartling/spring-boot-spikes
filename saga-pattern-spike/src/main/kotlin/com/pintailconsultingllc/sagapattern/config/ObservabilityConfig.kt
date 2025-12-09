package com.pintailconsultingllc.sagapattern.config

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Micrometer Observation API integration.
 *
 * This enables the @Observed annotation for automatic span creation
 * and metrics recording on annotated methods.
 */
@Configuration
class ObservabilityConfig {

    /**
     * Enable @Observed annotation support for automatic observation creation.
     * This allows methods to be instrumented with just an annotation:
     *
     * ```kotlin
     * @Observed(name = "saga.step", contextualName = "execute-step")
     * suspend fun executeStep() { ... }
     * ```
     *
     * The aspect will automatically:
     * - Create spans for distributed tracing
     * - Record timing metrics
     * - Propagate context across async boundaries
     */
    @Bean
    fun observedAspect(observationRegistry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(observationRegistry)
    }
}
