package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * Integration tests for AC11: Observability.
 *
 * IMPORTANT: Before running these tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("AC11: Observability Integration Tests")
class ObservabilityIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient by lazy {
        WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:$port")
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            }
            .build()
    }

    @Nested
    @DisplayName("AC11: Prometheus metrics endpoint")
    inner class PrometheusMetrics {

        @Test
        @DisplayName("should expose prometheus metrics endpoint")
        fun shouldExposePrometheusEndpoint() {
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!
                    assert(body.contains("http_server_requests"))
                }
        }

        @Test
        @DisplayName("should include JVM metrics")
        fun shouldIncludeJvmMetrics() {
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!
                    assert(body.contains("jvm_memory"))
                }
        }
    }

    @Nested
    @DisplayName("AC11: Trace context propagation")
    inner class TraceContextPropagation {

        @Test
        @DisplayName("should return correlation ID in response headers")
        fun shouldReturnCorrelationIdInResponseHeaders() {
            webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk
                .expectHeader().exists("X-Correlation-ID")
        }

        @Test
        @DisplayName("should propagate provided correlation ID")
        fun shouldPropagateProvidedCorrelationId() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.get()
                .uri("/api/products")
                .header("X-Correlation-ID", correlationId)
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-Correlation-ID", correlationId)
        }
    }

    @Nested
    @DisplayName("AC11: Health endpoint")
    inner class HealthEndpoint {

        @Test
        @DisplayName("should expose health endpoint with details")
        fun shouldExposeHealthEndpoint() {
            webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
        }
    }

    @Nested
    @DisplayName("AC11: Metrics endpoint")
    inner class MetricsEndpoint {

        @Test
        @DisplayName("should expose metrics endpoint")
        fun shouldExposeMetricsEndpoint() {
            webTestClient.get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.names").isArray
        }
    }
}
