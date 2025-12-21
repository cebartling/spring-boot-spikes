package com.pintailconsultingllc.cdcdebezium.metrics

import com.pintailconsultingllc.cdcdebezium.validation.AggregatedValidationResult
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.Meter
import org.springframework.stereotype.Service

@Service
class ValidationMetricsService {

    private val meter: Meter by lazy {
        GlobalOpenTelemetry.getMeter("cdc-validation")
    }

    private val validationsPassed: LongCounter by lazy {
        meter.counterBuilder("cdc.validation.passed")
            .setDescription("Number of events passing validation")
            .setUnit("{events}")
            .build()
    }

    private val validationsFailed: LongCounter by lazy {
        meter.counterBuilder("cdc.validation.failed")
            .setDescription("Number of events failing validation")
            .setUnit("{events}")
            .build()
    }

    private val validationLatency: LongHistogram by lazy {
        meter.histogramBuilder("cdc.validation.latency")
            .setDescription("Time taken to validate CDC events")
            .setUnit("ms")
            .ofLongs()
            .build()
    }

    private val ruleFailures: LongCounter by lazy {
        meter.counterBuilder("cdc.validation.failed.by_rule")
            .setDescription("Number of validation failures by rule")
            .setUnit("{failures}")
            .build()
    }

    fun recordValidation(result: AggregatedValidationResult, durationMs: Long) {
        validationLatency.record(
            durationMs,
            Attributes.of(ENTITY_TYPE, result.entityType)
        )

        if (result.valid) {
            validationsPassed.add(
                1,
                Attributes.of(ENTITY_TYPE, result.entityType)
            )
        } else {
            validationsFailed.add(
                1,
                Attributes.of(ENTITY_TYPE, result.entityType)
            )

            result.failures.forEach { failure ->
                ruleFailures.add(
                    1,
                    Attributes.of(
                        RULE_ID, failure.ruleId,
                        ENTITY_TYPE, result.entityType
                    )
                )
            }
        }
    }

    companion object {
        private val ENTITY_TYPE = AttributeKey.stringKey("entity_type")
        private val RULE_ID = AttributeKey.stringKey("rule_id")
    }
}
