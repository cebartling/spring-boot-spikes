package com.pintailconsultingllc.cqrsspike.acceptance.config

import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Spring configuration for Cucumber acceptance tests.
 *
 * This class configures the Spring application context for acceptance tests,
 * including:
 * - Full Spring Boot context with WebFlux
 * - Testcontainers PostgreSQL for database isolation
 * - Dynamic property configuration for database connections
 *
 * The configuration ensures each test run has an isolated database instance,
 * providing consistent and reproducible test results.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CucumberSpringConfiguration {

    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("cqrs_test_db")
            .withUsername("test_user")
            .withPassword("test_password")
            .withInitScript("init-test-schema.sql")
            .withReuse(true)

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // R2DBC properties for reactive database access
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgresContainer.host}:${postgresContainer.firstMappedPort}/${postgresContainer.databaseName}"
            }
            registry.add("spring.r2dbc.username") { postgresContainer.username }
            registry.add("spring.r2dbc.password") { postgresContainer.password }

            // Disable Flyway - schema is created via init script
            registry.add("spring.flyway.enabled") { "false" }

            // Disable Vault for acceptance tests
            registry.add("spring.cloud.vault.enabled") { "false" }
        }
    }
}
