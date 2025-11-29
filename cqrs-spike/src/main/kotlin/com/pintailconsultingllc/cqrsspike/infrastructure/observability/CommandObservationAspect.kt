package com.pintailconsultingllc.cqrsspike.infrastructure.observability

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
 * Aspect for observing command handler execution.
 *
 * Implements AC11: "All command operations emit trace spans"
 */
@Aspect
@Component
class CommandObservationAspect(
    private val observationRegistry: ObservationRegistry,
    private val commandMetrics: CommandMetrics
) {
    private val logger = LoggerFactory.getLogger(CommandObservationAspect::class.java)

    @Pointcut("execution(* com.pintailconsultingllc.cqrsspike.product.command.handler.ProductCommandHandler.handle(..))")
    fun commandHandlerMethods() {
    }

    @Around("commandHandlerMethods()")
    fun observeCommand(joinPoint: ProceedingJoinPoint): Any? {
        val commandType = extractCommandType(joinPoint)
        val startTime = Instant.now()

        val observation = Observation.createNotStarted("product.command", observationRegistry)
            .lowCardinalityKeyValue("command.type", commandType)
            .start()

        @Suppress("UNCHECKED_CAST")
        return (joinPoint.proceed() as Mono<*>)
            .doOnSuccess {
                val duration = Duration.between(startTime, Instant.now())
                commandMetrics.recordSuccess(commandType, duration)
                observation.stop()
                logger.debug("Command completed: type={}, duration={}ms", commandType, duration.toMillis())
            }
            .doOnError { error ->
                val duration = Duration.between(startTime, Instant.now())
                val errorType = error::class.simpleName ?: "Unknown"
                commandMetrics.recordFailure(commandType, errorType, duration)
                observation.error(error)
                observation.stop()
                logger.error(
                    "Command failed: type={}, error={}, duration={}ms",
                    commandType, errorType, duration.toMillis()
                )
            }
    }

    private fun extractCommandType(joinPoint: ProceedingJoinPoint): String {
        val args = joinPoint.args
        return if (args.isNotEmpty()) {
            args[0]::class.simpleName ?: "UnknownCommand"
        } else {
            "UnknownCommand"
        }
    }
}
