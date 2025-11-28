package com.pintailconsultingllc.cqrsspike.product.query.projection

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Health indicator for the projection infrastructure.
 *
 * Reports projection status to Spring Boot Actuator health endpoint.
 */
@Component("projectionHealth")
class ProjectionHealthIndicator(
    private val runner: ProjectionRunner,
    private val orchestrator: ProjectionOrchestrator,
    private val config: ProjectionConfig
) : ReactiveHealthIndicator {

    override fun health(): Mono<Health> {
        return orchestrator.getProjectionHealth()
            .map { health ->
                val builder = if (health.healthy) {
                    Health.up()
                } else {
                    Health.down()
                }

                builder
                    .withDetail("projectionName", health.projectionName)
                    .withDetail("state", runner.getState().name)
                    .withDetail("running", runner.isRunning())
                    .withDetail("eventLag", health.eventLag)
                    .withDetail("lagWarningThreshold", config.lagWarningThreshold)
                    .withDetail("lagErrorThreshold", config.lagErrorThreshold)
                    .withDetail("lastProcessedAt", health.lastProcessedAt?.toString() ?: "never")
                    .withDetail("message", health.message)
                    .build()
            }
            .onErrorResume { error ->
                Mono.just(
                    Health.down()
                        .withDetail("error", error.message)
                        .build()
                )
            }
    }
}
