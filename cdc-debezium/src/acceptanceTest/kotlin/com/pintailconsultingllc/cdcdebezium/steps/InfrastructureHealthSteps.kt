package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.repository.CustomerRepository
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.Properties
import kotlin.test.assertTrue
import kotlin.test.fail

class InfrastructureHealthSteps {

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var kafkaBootstrapServers: String

    @Value("\${kafka.connect.url:http://localhost:8083}")
    private lateinit var kafkaConnectUrl: String

    private val webClient = WebClient.create()

    private var postgresHealthy = false
    private var kafkaHealthy = false
    private var kafkaConnectHealthy = false

    @Given("PostgreSQL is running and accessible")
    fun postgresIsRunningAndAccessible() {
        try {
            val result = customerRepository.count()
                .timeout(Duration.ofSeconds(5))
                .block()
            postgresHealthy = result != null && result >= 0
            assertTrue(postgresHealthy, "PostgreSQL health check failed: unable to query database")
        } catch (e: Exception) {
            fail("PostgreSQL is not accessible: ${e.message}")
        }
    }

    @Given("Kafka is running and accessible")
    fun kafkaIsRunningAndAccessible() {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000")
            put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000")
        }

        try {
            AdminClient.create(props).use { adminClient ->
                val clusterId = adminClient.describeCluster()
                    .clusterId()
                    .get()
                kafkaHealthy = clusterId != null && clusterId.isNotBlank()
                assertTrue(kafkaHealthy, "Kafka health check failed: unable to get cluster ID")
            }
        } catch (e: Exception) {
            fail("Kafka is not accessible at $kafkaBootstrapServers: ${e.message}")
        }
    }

    @Given("Kafka Connect is running and accessible")
    fun kafkaConnectIsRunningAndAccessible() {
        try {
            val response = webClient.get()
                .uri("$kafkaConnectUrl/")
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(5))
                .block()

            kafkaConnectHealthy = response != null && response.contains("version")
            assertTrue(kafkaConnectHealthy, "Kafka Connect health check failed: unexpected response")
        } catch (e: Exception) {
            fail("Kafka Connect is not accessible at $kafkaConnectUrl: ${e.message}")
        }
    }

    @Given("the CDC infrastructure is healthy")
    fun theCdcInfrastructureIsHealthy() {
        postgresIsRunningAndAccessible()
        kafkaIsRunningAndAccessible()
        kafkaConnectIsRunningAndAccessible()
    }

    @Given("the Debezium connector {string} is registered")
    fun theDebeziumConnectorIsRegistered(connectorName: String) {
        try {
            val response = webClient.get()
                .uri("$kafkaConnectUrl/connectors/$connectorName/status")
                .retrieve()
                .bodyToMono(Map::class.java)
                .timeout(Duration.ofSeconds(5))
                .block()

            val connectorState = (response?.get("connector") as? Map<*, *>)?.get("state") as? String
            assertTrue(
                connectorState == "RUNNING",
                "Connector '$connectorName' is not running. State: $connectorState"
            )
        } catch (e: Exception) {
            fail("Unable to check connector '$connectorName' status: ${e.message}")
        }
    }

    @Then("PostgreSQL should be healthy")
    fun postgresShouldBeHealthy() {
        assertTrue(postgresHealthy, "PostgreSQL is not healthy")
    }

    @Then("Kafka should be healthy")
    fun kafkaShouldBeHealthy() {
        assertTrue(kafkaHealthy, "Kafka is not healthy")
    }

    @Then("Kafka Connect should be healthy")
    fun kafkaConnectShouldBeHealthy() {
        assertTrue(kafkaConnectHealthy, "Kafka Connect is not healthy")
    }
}
