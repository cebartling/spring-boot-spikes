package com.pintailconsultingllc.cdcdebezium.validation

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.ValidationMetricsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ValidationService(
    private val rules: List<ValidationRule<CustomerCdcEvent>>,
    private val metricsService: ValidationMetricsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Validate a CDC event against all registered rules.
     * Returns aggregated results with pass/fail for each rule.
     */
    fun validate(event: CustomerCdcEvent): AggregatedValidationResult {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<ValidationResult>()

        for (rule in rules) {
            val result = try {
                rule.validate(event)
            } catch (e: Exception) {
                logger.error("Validation rule {} threw exception", rule.ruleId, e)
                ValidationResult.failure(
                    ruleId = rule.ruleId,
                    message = "Rule execution failed: ${e.message}",
                    details = mapOf("exception" to e.javaClass.simpleName)
                )
            }

            results.add(result)

            if (!result.valid && !rule.continueOnFailure) {
                break
            }
        }

        val aggregatedResult = AggregatedValidationResult.fromResults(
            results = results,
            eventId = event.id.toString(),
            entityType = "customer"
        )

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordValidation(aggregatedResult, duration)

        if (!aggregatedResult.valid) {
            logger.warn(
                "Validation failed for event {}: {} failures - {}",
                event.id,
                aggregatedResult.failureCount,
                aggregatedResult.failures.map { it.ruleId }
            )
        }

        return aggregatedResult
    }

    /**
     * Quick validation check - returns true/false without full results.
     */
    fun isValid(event: CustomerCdcEvent): Boolean = validate(event).valid
}
