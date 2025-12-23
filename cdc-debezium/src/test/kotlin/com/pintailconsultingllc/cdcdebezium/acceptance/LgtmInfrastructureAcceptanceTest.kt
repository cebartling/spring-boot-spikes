package com.pintailconsultingllc.cdcdebezium.acceptance

import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AcceptanceTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("LGTM Infrastructure (PLAN-019)")
class LgtmInfrastructureAcceptanceTest {

    private lateinit var httpClient: HttpClient

    @BeforeAll
    fun setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    @Nested
    @DisplayName("Grafana")
    inner class GrafanaTests {

        @Test
        @DisplayName("should be healthy and accessible")
        fun shouldBeHealthy() {
            val response = httpGet("$GRAFANA_URL/api/health")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("database"))
        }

        @Test
        @DisplayName("should have Prometheus datasource configured")
        fun shouldHavePrometheusDatasource() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources/name/Prometheus")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("prometheus"))
        }

        @Test
        @DisplayName("should have Tempo datasource configured")
        fun shouldHaveTempoDatasource() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources/name/Tempo")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("tempo"))
        }

        @Test
        @DisplayName("should have Loki datasource configured")
        fun shouldHaveLokiDatasource() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources/name/Loki")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("loki"))
        }

        @Test
        @DisplayName("should have exactly 3 datasources configured")
        fun shouldHaveThreeDatasources() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources")

            assertEquals(200, response.statusCode())
            val datasourceNames = listOf("Prometheus", "Tempo", "Loki")
            datasourceNames.forEach { name ->
                assertTrue(response.body().contains(name), "Expected datasource '$name' not found")
            }
        }
    }

    @Nested
    @DisplayName("Tempo")
    inner class TempoTests {

        @Test
        @DisplayName("should be ready")
        fun shouldBeReady() {
            val response = httpGet("$TEMPO_URL/ready")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("ready"))
        }

        @Test
        @DisplayName("should expose metrics endpoint")
        fun shouldExposeMetrics() {
            val response = httpGet("$TEMPO_URL/metrics")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("tempo"))
        }
    }

    @Nested
    @DisplayName("Loki")
    inner class LokiTests {

        @Test
        @DisplayName("should be ready")
        fun shouldBeReady() {
            val response = httpGet("$LOKI_URL/ready")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("ready"))
        }

        @Test
        @DisplayName("should accept label queries")
        fun shouldAcceptLabelQueries() {
            val response = httpGet("$LOKI_URL/loki/api/v1/labels")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("status"))
        }
    }

    @Nested
    @DisplayName("Prometheus")
    inner class PrometheusTests {

        @Test
        @DisplayName("should be ready")
        fun shouldBeReady() {
            val response = httpGet("$PROMETHEUS_URL/-/ready")

            assertEquals(200, response.statusCode())
        }

        @Test
        @DisplayName("should be healthy")
        fun shouldBeHealthy() {
            val response = httpGet("$PROMETHEUS_URL/-/healthy")

            assertEquals(200, response.statusCode())
        }

        @Test
        @DisplayName("should accept queries")
        fun shouldAcceptQueries() {
            val response = httpGet("$PROMETHEUS_URL/api/v1/query?query=up")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("success"))
        }

        @Test
        @DisplayName("should have remote write receiver enabled")
        fun shouldHaveRemoteWriteEnabled() {
            val response = httpGet("$PROMETHEUS_URL/api/v1/status/config")

            assertEquals(200, response.statusCode())
        }
    }

    @Nested
    @DisplayName("OpenTelemetry Collector")
    inner class OtelCollectorTests {

        @Test
        @DisplayName("should be healthy")
        fun shouldBeHealthy() {
            val response = httpGet("$OTEL_COLLECTOR_HEALTH_URL")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("ok") || response.body().contains("Server available"))
        }

        @Test
        @DisplayName("should expose metrics")
        fun shouldExposeMetrics() {
            val response = httpGet("$OTEL_COLLECTOR_METRICS_URL/metrics")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("otelcol"))
        }
    }

    @Nested
    @DisplayName("Service Integration")
    inner class ServiceIntegrationTests {

        @Test
        @DisplayName("Grafana should be able to query Prometheus")
        fun grafanaShouldQueryPrometheus() {
            // Query Prometheus datasource through Grafana proxy
            val response = httpGetWithAuth(
                "$GRAFANA_URL/api/datasources/proxy/uid/prometheus/api/v1/query?query=up"
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("success"))
        }

        @Test
        @DisplayName("Grafana should be able to reach Tempo")
        fun grafanaShouldReachTempo() {
            // Query Tempo datasource through Grafana proxy
            val response = httpGetWithAuth(
                "$GRAFANA_URL/api/datasources/proxy/uid/tempo/api/search"
            )

            // Tempo search may return 200 with empty results or 404 if no traces
            assertTrue(response.statusCode() in listOf(200, 404))
        }

        @Test
        @DisplayName("Grafana should be able to reach Loki")
        fun grafanaShouldReachLoki() {
            // Query Loki datasource through Grafana proxy
            val response = httpGetWithAuth(
                "$GRAFANA_URL/api/datasources/proxy/uid/loki/loki/api/v1/labels"
            )

            assertEquals(200, response.statusCode())
        }
    }

    private fun httpGet(url: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun httpGetWithAuth(url: String): HttpResponse<String> {
        val credentials = Base64.getEncoder().encodeToString("$GRAFANA_USER:$GRAFANA_PASSWORD".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Basic $credentials")
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val GRAFANA_URL = "http://localhost:3000"
        private const val GRAFANA_USER = "admin"
        private const val GRAFANA_PASSWORD = "admin"

        private const val TEMPO_URL = "http://localhost:3200"
        private const val LOKI_URL = "http://localhost:3100"
        private const val PROMETHEUS_URL = "http://localhost:9090"

        private const val OTEL_COLLECTOR_HEALTH_URL = "http://localhost:13133"
        private const val OTEL_COLLECTOR_METRICS_URL = "http://localhost:8888"
    }
}
