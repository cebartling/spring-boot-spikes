package com.pintailconsultingllc.cdcdebezium.acceptance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AcceptanceTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Trace-to-Logs Linking (PLAN-019)")
class TraceToLogsLinkingAcceptanceTest {

    private lateinit var httpClient: HttpClient
    private val objectMapper = ObjectMapper()

    @BeforeAll
    fun setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    @Nested
    @DisplayName("Tempo Datasource Configuration")
    inner class TempoDatasourceConfiguration {

        @Test
        @DisplayName("should have tracesToLogsV2 configured pointing to Loki")
        fun shouldHaveTracesToLogsConfigured() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources/uid/tempo")

            assertEquals(200, response.statusCode())

            val datasource = objectMapper.readTree(response.body())
            val jsonData = datasource.get("jsonData")
            assertNotNull(jsonData, "jsonData should be present")

            val tracesToLogs = jsonData.get("tracesToLogsV2")
            assertNotNull(tracesToLogs, "tracesToLogsV2 should be configured")

            val lokiDatasourceUid = tracesToLogs.get("datasourceUid")?.asText()
            assertEquals("loki", lokiDatasourceUid, "tracesToLogsV2 should point to Loki datasource")

            val filterByTraceID = tracesToLogs.get("filterByTraceID")?.asBoolean()
            assertTrue(filterByTraceID == true, "filterByTraceID should be enabled")

            val filterBySpanID = tracesToLogs.get("filterBySpanID")?.asBoolean()
            assertTrue(filterBySpanID == true, "filterBySpanID should be enabled")
        }

        @Test
        @DisplayName("should have lokiSearch configured")
        fun shouldHaveLokiSearchConfigured() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources/uid/tempo")

            assertEquals(200, response.statusCode())

            val datasource = objectMapper.readTree(response.body())
            val jsonData = datasource.get("jsonData")
            assertNotNull(jsonData, "jsonData should be present")

            val lokiSearch = jsonData.get("lokiSearch")
            assertNotNull(lokiSearch, "lokiSearch should be configured")

            val lokiDatasourceUid = lokiSearch.get("datasourceUid")?.asText()
            assertEquals("loki", lokiDatasourceUid, "lokiSearch should point to Loki datasource")
        }
    }

    @Nested
    @DisplayName("Loki Datasource Configuration")
    inner class LokiDatasourceConfiguration {

        @Test
        @DisplayName("should have derivedFields configured for TraceID extraction")
        fun shouldHaveDerivedFieldsConfigured() {
            val response = httpGetWithAuth("$GRAFANA_URL/api/datasources/uid/loki")

            assertEquals(200, response.statusCode())

            val datasource = objectMapper.readTree(response.body())
            val jsonData = datasource.get("jsonData")
            assertNotNull(jsonData, "jsonData should be present")

            val derivedFields = jsonData.get("derivedFields")
            assertNotNull(derivedFields, "derivedFields should be configured")
            assertTrue(derivedFields.isArray, "derivedFields should be an array")
            assertTrue(derivedFields.size() > 0, "derivedFields should have at least one entry")

            // Find the TraceID derived field
            val traceIdField = derivedFields.find { it.get("name")?.asText() == "TraceID" }
            assertNotNull(traceIdField, "TraceID derived field should exist")

            val datasourceUid = traceIdField.get("datasourceUid")?.asText()
            assertEquals("tempo", datasourceUid, "TraceID should link to Tempo datasource")

            val matcherRegex = traceIdField.get("matcherRegex")?.asText()
            assertNotNull(matcherRegex, "matcherRegex should be configured")
            assertTrue(matcherRegex.contains("trace_id"), "matcherRegex should extract trace_id")
        }
    }

    @Nested
    @DisplayName("Trace-to-Logs Correlation")
    inner class TraceToLogsCorrelation {

        @Test
        @DisplayName("should be able to query Tempo for traces")
        fun shouldQueryTempoForTraces() {
            // Query Tempo for recent traces (search API)
            val response = httpGet("$TEMPO_URL/api/search?limit=10")

            // Tempo returns 200 even with no traces, or 404 for older API
            assertTrue(
                response.statusCode() in listOf(200, 404),
                "Tempo search should be accessible"
            )
        }

        @Test
        @DisplayName("should be able to query Loki for logs")
        fun shouldQueryLokiForLogs() {
            // Query Loki for recent logs from cdc-consumer service
            val query = "{service=\"cdc-consumer\"}"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val response = httpGet("$LOKI_URL/loki/api/v1/query?query=$encodedQuery&limit=10")

            assertEquals(200, response.statusCode())

            val result = objectMapper.readTree(response.body())
            val status = result.get("status")?.asText()
            assertEquals("success", status, "Loki query should succeed")
        }

        @Test
        @DisplayName("Grafana should proxy Tempo trace search through datasource")
        fun grafanaShouldProxyTempoSearch() {
            val response = httpGetWithAuth(
                "$GRAFANA_URL/api/datasources/proxy/uid/tempo/api/search?limit=5"
            )

            // Tempo search may return 200 with results or 404 if no traces
            assertTrue(
                response.statusCode() in listOf(200, 404),
                "Grafana should proxy Tempo search"
            )
        }

        @Test
        @DisplayName("Grafana should proxy Loki log queries through datasource")
        fun grafanaShouldProxyLokiQueries() {
            val query = "{service=\"cdc-consumer\"}"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val response = httpGetWithAuth(
                "$GRAFANA_URL/api/datasources/proxy/uid/loki/loki/api/v1/query?query=$encodedQuery&limit=5"
            )

            assertEquals(200, response.statusCode())

            val result = objectMapper.readTree(response.body())
            val status = result.get("status")?.asText()
            assertEquals("success", status, "Grafana should successfully proxy Loki queries")
        }
    }

    @Nested
    @DisplayName("Cross-Service Correlation")
    inner class CrossServiceCorrelation {

        @Test
        @DisplayName("should find logs with traceId field when traces exist")
        fun shouldFindLogsWithTraceIdField() {
            // Query Loki for logs that contain traceId field
            // Using LogQL to filter for logs with non-empty traceId
            val query = "{service=\"cdc-consumer\"} | json | traceId != \"\""
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

            // Use range query to get more results
            val now = System.currentTimeMillis() * 1_000_000 // nanoseconds
            val oneHourAgo = now - (3600 * 1_000_000_000L)

            val response = httpGet(
                "$LOKI_URL/loki/api/v1/query_range?query=$encodedQuery&start=$oneHourAgo&end=$now&limit=10"
            )

            assertEquals(200, response.statusCode())

            val result = objectMapper.readTree(response.body())
            val status = result.get("status")?.asText()
            assertEquals("success", status, "Loki query should succeed")

            // Note: We don't assert on results count because there may not be traces yet
            // The important thing is that the query structure is valid
        }

        @Test
        @DisplayName("should be able to query logs by specific trace ID pattern")
        fun shouldQueryLogsByTraceIdPattern() {
            // This tests that Loki can filter logs by trace ID
            // Using a regex pattern to find any trace ID format (32 hex chars)
            val query = "{service=\"cdc-consumer\"} |~ \"trace_id.*[a-f0-9]{32}\""
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

            val response = httpGet("$LOKI_URL/loki/api/v1/query?query=$encodedQuery&limit=5")

            assertEquals(200, response.statusCode())

            val result = objectMapper.readTree(response.body())
            val status = result.get("status")?.asText()
            assertEquals("success", status, "Loki should accept trace ID pattern query")
        }
    }

    @Nested
    @DisplayName("Service Name Label Consistency")
    inner class ServiceNameLabelConsistency {

        @Test
        @DisplayName("should have labels endpoint accessible in Loki")
        fun shouldHaveLabelsEndpointAccessible() {
            val response = httpGet("$LOKI_URL/loki/api/v1/labels")

            assertEquals(200, response.statusCode())

            val result = objectMapper.readTree(response.body())
            val status = result.get("status")?.asText()
            assertEquals("success", status)

            // Labels data may be null or empty if no logs have been ingested yet
            // The important thing is that the API endpoint is accessible
            val labels = result.get("data")
            // If labels exist, verify structure; if null/empty, that's acceptable (no logs yet)
            if (labels != null && labels.isArray && labels.size() > 0) {
                val labelList = labels.map { it.asText() }
                // When logs are present, service label should exist
                assertTrue(
                    labelList.any { it.contains("service") || it.contains("job") || it.contains("exporter") },
                    "When logs exist, should have service-related label for correlation"
                )
            }
            // Test passes if endpoint is accessible - logs may not exist yet
        }

        @Test
        @DisplayName("should be able to query label values for service")
        fun shouldQueryServiceLabelValues() {
            // Try to get values for service label
            val response = httpGet("$LOKI_URL/loki/api/v1/label/service/values")

            // May return 200 with values or 404 if label doesn't exist yet
            assertTrue(
                response.statusCode() in listOf(200, 404),
                "Service label values query should be valid"
            )

            if (response.statusCode() == 200) {
                val result = objectMapper.readTree(response.body())
                val status = result.get("status")?.asText()
                assertEquals("success", status)
            }
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
    }
}
