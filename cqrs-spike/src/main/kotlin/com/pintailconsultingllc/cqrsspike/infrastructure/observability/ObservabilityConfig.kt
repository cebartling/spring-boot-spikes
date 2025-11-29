package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for observability components.
 *
 * Implements AC11 observability infrastructure.
 */
@Configuration
class ObservabilityConfig {

    /**
     * Enable @Observed annotation support.
     */
    @Bean
    fun observedAspect(observationRegistry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(observationRegistry)
    }
}
