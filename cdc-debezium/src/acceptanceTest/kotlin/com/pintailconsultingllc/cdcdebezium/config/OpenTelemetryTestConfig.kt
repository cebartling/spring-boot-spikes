package com.pintailconsultingllc.cdcdebezium.config

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.annotation.PreDestroy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test")
class OpenTelemetryTestConfig {

    @Bean
    fun inMemorySpanExporter(): InMemorySpanExporter {
        return InMemorySpanExporter.create()
    }

    @Bean
    fun inMemoryMetricReader(): InMemoryMetricReader {
        return InMemoryMetricReader.create()
    }

    @Bean
    fun sdkTracerProvider(spanExporter: InMemorySpanExporter): SdkTracerProvider {
        return SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
    }

    @Bean
    fun sdkMeterProvider(metricReader: InMemoryMetricReader): SdkMeterProvider {
        return SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .build()
    }

    @Bean
    @Primary
    fun openTelemetry(
        tracerProvider: SdkTracerProvider,
        meterProvider: SdkMeterProvider
    ): OpenTelemetry {
        GlobalOpenTelemetry.resetForTest()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal()
    }

    @Bean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer("cdc-consumer-test", "1.0.0")
    }

    @Bean
    fun meter(openTelemetry: OpenTelemetry): Meter {
        return openTelemetry.getMeter("cdc-consumer-test")
    }

    @PreDestroy
    fun cleanup() {
        GlobalOpenTelemetry.resetForTest()
    }
}
