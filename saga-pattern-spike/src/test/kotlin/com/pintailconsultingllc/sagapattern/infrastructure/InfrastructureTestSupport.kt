package com.pintailconsultingllc.sagapattern.infrastructure

import org.junit.jupiter.api.Assumptions
import org.slf4j.LoggerFactory
import java.net.Socket

/**
 * Shared utilities for integration tests that depend on Docker infrastructure.
 *
 * Provides consistent port checking and clear messaging when infrastructure is unavailable.
 */
object InfrastructureTestSupport {

    private val logger = LoggerFactory.getLogger(InfrastructureTestSupport::class.java)

    /**
     * Docker infrastructure service definitions.
     */
    enum class Service(
        val displayName: String,
        val host: String,
        val port: Int,
        val description: String
    ) {
        POSTGRESQL(
            displayName = "PostgreSQL",
            host = "localhost",
            port = 5432,
            description = "PostgreSQL database server"
        ),
        WIREMOCK(
            displayName = "WireMock",
            host = "localhost",
            port = 8081,
            description = "WireMock API stub server"
        ),
        VAULT(
            displayName = "HashiCorp Vault",
            host = "localhost",
            port = 8200,
            description = "HashiCorp Vault secret management"
        ),
        JAEGER(
            displayName = "Jaeger",
            host = "localhost",
            port = 16686,
            description = "Jaeger tracing UI"
        ),
        PROMETHEUS(
            displayName = "Prometheus",
            host = "localhost",
            port = 9090,
            description = "Prometheus metrics server"
        ),
        GRAFANA(
            displayName = "Grafana",
            host = "localhost",
            port = 3000,
            description = "Grafana visualization dashboard"
        ),
        LOKI(
            displayName = "Loki",
            host = "localhost",
            port = 3100,
            description = "Loki log aggregation"
        )
    }

    /**
     * Check if a service port is open.
     *
     * @param host The hostname to check
     * @param port The port number to check
     * @return true if the port is accepting connections, false otherwise
     */
    fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a specific Docker service is available.
     *
     * @param service The service to check
     * @return true if the service is available, false otherwise
     */
    fun isServiceAvailable(service: Service): Boolean {
        return isPortOpen(service.host, service.port)
    }

    /**
     * Assume a Docker service is available, skipping the test with a clear message if not.
     *
     * This method logs detailed information about the infrastructure requirement and
     * provides clear instructions for starting the Docker infrastructure.
     *
     * @param service The required Docker service
     */
    fun assumeServiceAvailable(service: Service) {
        val available = isServiceAvailable(service)

        if (!available) {
            logger.warn(buildUnavailableMessage(service))
        }

        Assumptions.assumeTrue(
            available,
            buildAssumptionMessage(service)
        )

        logger.info("✓ ${service.displayName} is available at ${service.host}:${service.port}")
    }

    /**
     * Assume multiple Docker services are available.
     *
     * @param services The required Docker services
     */
    fun assumeServicesAvailable(vararg services: Service) {
        val unavailable = services.filter { !isServiceAvailable(it) }

        if (unavailable.isNotEmpty()) {
            val message = buildMultipleServicesUnavailableMessage(unavailable)
            logger.warn(message)
            Assumptions.assumeTrue(false, message)
        }

        services.forEach { service ->
            logger.info("✓ ${service.displayName} is available at ${service.host}:${service.port}")
        }
    }

    /**
     * Get the status of all Docker services.
     *
     * @return A map of service to availability status
     */
    fun getServicesStatus(): Map<Service, Boolean> {
        return Service.entries.associateWith { isServiceAvailable(it) }
    }

    /**
     * Log the status of all Docker services.
     */
    fun logServicesStatus() {
        logger.info("Docker Infrastructure Status:")
        logger.info("=" .repeat(60))
        Service.entries.forEach { service ->
            val status = if (isServiceAvailable(service)) "✓ AVAILABLE" else "✗ UNAVAILABLE"
            logger.info("  ${service.displayName.padEnd(20)} ${service.host}:${service.port.toString().padEnd(6)} $status")
        }
        logger.info("=" .repeat(60))
    }

    private fun buildAssumptionMessage(service: Service): String {
        return """
            |
            |╔══════════════════════════════════════════════════════════════════════════════╗
            |║  INTEGRATION TEST SKIPPED - Docker Infrastructure Not Available              ║
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║                                                                              ║
            |║  Required Service: ${service.displayName.padEnd(55)}║
            |║  Description:      ${service.description.padEnd(55)}║
            |║  Expected at:      ${service.host}:${service.port}                                               ║
            |║                                                                              ║
            |║  To start the Docker infrastructure, run:                                    ║
            |║                                                                              ║
            |║      docker compose up -d                                                    ║
            |║                                                                              ║
            |║  Then re-run the integration tests:                                          ║
            |║                                                                              ║
            |║      ./gradlew integrationTest                                               ║
            |║                                                                              ║
            |╚══════════════════════════════════════════════════════════════════════════════╝
        """.trimMargin()
    }

    private fun buildUnavailableMessage(service: Service): String {
        return """
            |
            |⚠️  ${service.displayName} is not available at ${service.host}:${service.port}
            |    ${service.description}
            |
            |    Start Docker infrastructure with: docker compose up -d
            |
        """.trimMargin()
    }

    private fun buildMultipleServicesUnavailableMessage(unavailable: List<Service>): String {
        val serviceList = unavailable.joinToString("\n") {
            "  - ${it.displayName} (${it.host}:${it.port}): ${it.description}"
        }
        return """
            |
            |╔══════════════════════════════════════════════════════════════════════════════╗
            |║  INTEGRATION TEST SKIPPED - Multiple Services Not Available                  ║
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║                                                                              ║
            |║  The following Docker services are required but not running:                 ║
            |║                                                                              ║
            |$serviceList
            |║                                                                              ║
            |║  To start the Docker infrastructure, run:                                    ║
            |║                                                                              ║
            |║      docker compose up -d                                                    ║
            |║                                                                              ║
            |╚══════════════════════════════════════════════════════════════════════════════╝
        """.trimMargin()
    }
}
