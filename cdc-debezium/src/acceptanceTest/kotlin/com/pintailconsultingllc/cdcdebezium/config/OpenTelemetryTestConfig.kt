package com.pintailconsultingllc.cdcdebezium.config

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.annotation.PostConstruct
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
    fun sdkTracerProvider(spanExporter: InMemorySpanExporter): SdkTracerProvider {
        return SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
    }

    @Bean
    @Primary
    fun openTelemetry(tracerProvider: SdkTracerProvider): OpenTelemetry {
        GlobalOpenTelemetry.resetForTest()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal()
    }

    @Bean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer("cdc-consumer-test", "1.0.0")
    }

    @PreDestroy
    fun cleanup() {
        GlobalOpenTelemetry.resetForTest()
    }
}
