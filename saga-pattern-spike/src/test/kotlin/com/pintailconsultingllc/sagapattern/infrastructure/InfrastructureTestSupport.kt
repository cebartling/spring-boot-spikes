package com.pintailconsultingllc.sagapattern.infrastructure

import org.junit.jupiter.api.Assumptions
import java.net.Socket

/**
 * Shared utilities for integration tests that depend on Docker infrastructure.
 *
 * Provides consistent port checking and clear messaging when infrastructure is unavailable.
 * Messages are printed directly to System.err to ensure visibility in Gradle output.
 */
object InfrastructureTestSupport {

    // ANSI color codes for terminal output
    private const val RESET = "\u001B[0m"
    private const val RED = "\u001B[31m"
    private const val YELLOW = "\u001B[33m"
    private const val GREEN = "\u001B[32m"
    private const val BOLD = "\u001B[1m"

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
     * This method prints detailed information about the infrastructure requirement
     * directly to System.err to ensure visibility in Gradle output.
     *
     * @param service The required Docker service
     */
    fun assumeServiceAvailable(service: Service) {
        val available = isServiceAvailable(service)

        if (!available) {
            // Print directly to System.err to ensure visibility in Gradle output
            System.err.println(buildUnavailableMessage(service))
            System.err.flush()
        }

        Assumptions.assumeTrue(
            available,
            "Docker service ${service.displayName} is not available at ${service.host}:${service.port}"
        )

        // Print success message
        System.out.println("$GREEN✓ ${service.displayName} is available at ${service.host}:${service.port}$RESET")
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
            System.err.println(message)
            System.err.flush()
            Assumptions.assumeTrue(false, "Required Docker services are not available")
        }

        services.forEach { service ->
            System.out.println("$GREEN✓ ${service.displayName} is available at ${service.host}:${service.port}$RESET")
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
     * Print the status of all Docker services to console.
     */
    fun logServicesStatus() {
        println()
        println("${BOLD}Docker Infrastructure Status:$RESET")
        println("=" .repeat(60))
        Service.entries.forEach { service ->
            val available = isServiceAvailable(service)
            val status = if (available) "${GREEN}✓ AVAILABLE$RESET" else "${RED}✗ UNAVAILABLE$RESET"
            println("  ${service.displayName.padEnd(20)} ${service.host}:${service.port.toString().padEnd(6)} $status")
        }
        println("=" .repeat(60))
        println()
    }

    private fun buildUnavailableMessage(service: Service): String {
        val boxWidth = 78  // Inner width between the vertical bars
        val border = "═".repeat(boxWidth)
        val emptyLine = "║" + " ".repeat(boxWidth) + "║"

        fun padLine(content: String): String {
            val padding = boxWidth - content.length
            return "║  $content${" ".repeat(maxOf(0, padding - 2))}║"
        }

        val serviceName = service.displayName
        val serviceDesc = service.description
        val serviceAddr = "${service.host}:${service.port}"

        return buildString {
            appendLine()
            appendLine("$YELLOW$BOLD╔$border╗$RESET")
            appendLine("$YELLOW$BOLD║  INTEGRATION TEST SKIPPED - Docker Infrastructure Not Available            ║$RESET")
            appendLine("$YELLOW$BOLD╠$border╣$RESET")
            appendLine("$YELLOW$emptyLine$RESET")
            appendLine("$YELLOW${padLine("Required Service: $serviceName")}$RESET")
            appendLine("$YELLOW${padLine("Description:      $serviceDesc")}$RESET")
            appendLine("$YELLOW${padLine("Expected at:      $serviceAddr")}$RESET")
            appendLine("$YELLOW$emptyLine$RESET")
            appendLine("$YELLOW${padLine("To start the Docker infrastructure, run:")}$RESET")
            appendLine("$YELLOW$emptyLine$RESET")
            appendLine("$GREEN$BOLD${padLine("    docker compose up -d")}$RESET")
            appendLine("$YELLOW$emptyLine$RESET")
            appendLine("$YELLOW${padLine("Then re-run the integration tests:")}$RESET")
            appendLine("$YELLOW$emptyLine$RESET")
            appendLine("$GREEN$BOLD${padLine("    ./gradlew integrationTest")}$RESET")
            appendLine("$YELLOW$emptyLine$RESET")
            appendLine("$YELLOW$BOLD╚$border╝$RESET")
            appendLine()
        }
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
