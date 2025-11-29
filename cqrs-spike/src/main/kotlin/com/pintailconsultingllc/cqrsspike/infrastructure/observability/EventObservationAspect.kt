package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * Aspect for observing event store operations.
 *
 * Implements AC11: "Event publication and consumption are traced"
 */
@Aspect
@Component
class EventObservationAspect(
    private val observationRegistry: ObservationRegistry,
    private val eventMetrics: EventMetrics
) {
    private val logger = LoggerFactory.getLogger(EventObservationAspect::class.java)

    @Pointcut("execution(* com.pintailconsultingllc.cqrsspike.product.command.infrastructure.EventStoreRepository.saveEvents(..))")
    fun eventStoreSaveMethods() {
    }

    @Pointcut("execution(* com.pintailconsultingllc.cqrsspike.product.query.projection.ProductProjector.processEvent(..))")
    fun projectionProcessMethods() {
    }

    @Around("eventStoreSaveMethods()")
    fun observeEventPublish(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = Instant.now()
        val eventTypes = extractEventTypes(joinPoint)

        val observation = Observation.createNotStarted("product.event.publish", observationRegistry)
            .lowCardinalityKeyValue("event.types", eventTypes.joinToString(","))
            .start()

        @Suppress("UNCHECKED_CAST")
        return (joinPoint.proceed() as Mono<*>)
            .doOnSuccess {
                val duration = Duration.between(startTime, Instant.now())
                eventTypes.forEach { eventType ->
                    eventMetrics.recordEventPublished(eventType, duration)
                }
                observation.stop()
                logger.debug("Events published: types={}, duration={}ms", eventTypes, duration.toMillis())
            }
            .doOnError { error ->
                observation.error(error)
                observation.stop()
                logger.error("Event publish failed: types={}, error={}", eventTypes, error.message)
            }
    }

    @Around("projectionProcessMethods()")
    fun observeEventConsume(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = Instant.now()
        val eventType = extractEventTypeFromProjection(joinPoint)

        val observation = Observation.createNotStarted("product.event.consume", observationRegistry)
            .lowCardinalityKeyValue("event.type", eventType)
            .lowCardinalityKeyValue("projection", "ProductReadModel")
            .start()

        @Suppress("UNCHECKED_CAST")
        return (joinPoint.proceed() as Mono<*>)
            .doOnSuccess {
                val duration = Duration.between(startTime, Instant.now())
                val lagMs = if (joinPoint.args.isNotEmpty() && joinPoint.args[0] is ProductEvent) {
                    val event = joinPoint.args[0] as ProductEvent
                    Duration.between(event.occurredAt, Instant.now()).toMillis()
                } else {
                    -1L // Unknown lag
                }
                eventMetrics.recordEventConsumed(eventType, duration, lagMs)
                observation.stop()
                logger.debug("Event consumed: type={}, duration={}ms, lag={}ms", eventType, duration.toMillis(), lagMs)
            }
            .doOnError { error ->
                observation.error(error)
                observation.stop()
                logger.error("Event consume failed: type={}, error={}", eventType, error.message)
            }
    }

    private fun extractEventTypes(joinPoint: ProceedingJoinPoint): List<String> {
        val args = joinPoint.args
        return if (args.isNotEmpty() && args[0] is List<*>) {
            @Suppress("UNCHECKED_CAST")
            (args[0] as List<Any>).map { event ->
                event::class.simpleName ?: "UnknownEvent"
            }
        } else {
            listOf("UnknownEvent")
        }
    }

    private fun extractEventTypeFromProjection(joinPoint: ProceedingJoinPoint): String {
        val args = joinPoint.args
        return if (args.isNotEmpty() && args[0] is ProductEvent) {
            args[0]::class.simpleName ?: "UnknownEvent"
        } else {
            "UnknownEvent"
        }
    }
}
