package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductSearchResponse
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
 * Aspect for observing query service execution.
 *
 * Implements AC11: "All query operations emit trace spans"
 */
@Aspect
@Component
class QueryObservationAspect(
    private val observationRegistry: ObservationRegistry,
    private val queryMetrics: QueryMetrics
) {
    private val logger = LoggerFactory.getLogger(QueryObservationAspect::class.java)

    @Pointcut("execution(* com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService.findById(..))")
    fun findByIdMethod() {
    }

    @Pointcut("execution(* com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService.findAllPaginated(..))")
    fun findAllPaginatedMethod() {
    }

    @Pointcut("execution(* com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService.search(..))")
    fun searchMethod() {
    }

    @Around("findByIdMethod()")
    fun observeFindById(joinPoint: ProceedingJoinPoint): Any? {
        return observeQuery(joinPoint, "findById")
    }

    @Around("findAllPaginatedMethod()")
    fun observeFindAllPaginated(joinPoint: ProceedingJoinPoint): Any? {
        return observeQuery(joinPoint, "findAll")
    }

    @Around("searchMethod()")
    fun observeSearch(joinPoint: ProceedingJoinPoint): Any? {
        return observeQuery(joinPoint, "search")
    }

    private fun observeQuery(joinPoint: ProceedingJoinPoint, queryType: String): Any? {
        val startTime = Instant.now()

        val observation = Observation.createNotStarted("product.query", observationRegistry)
            .lowCardinalityKeyValue("query.type", queryType)
            .start()

        @Suppress("UNCHECKED_CAST")
        return (joinPoint.proceed() as Mono<*>)
            .doOnSuccess { result: Any? ->
                val duration = Duration.between(startTime, Instant.now())
                val resultCount = extractResultCount(result)
                queryMetrics.recordQuery(queryType, duration, resultCount)
                observation.stop()
                logger.debug(
                    "Query completed: type={}, results={}, duration={}ms",
                    queryType, resultCount, duration.toMillis()
                )
            }
            .doOnError { error ->
                val duration = Duration.between(startTime, Instant.now())
                queryMetrics.recordQuery(queryType, duration)
                observation.error(error)
                observation.stop()
                logger.error(
                    "Query failed: type={}, error={}, duration={}ms",
                    queryType, error::class.simpleName, duration.toMillis()
                )
            }
    }

    private fun extractResultCount(result: Any?): Int {
        return when (result) {
            is ProductPageResponse -> result.content.size
            is ProductSearchResponse -> result.content.size
            is Collection<*> -> result.size
            null -> 0
            else -> 1
        }
    }
}
