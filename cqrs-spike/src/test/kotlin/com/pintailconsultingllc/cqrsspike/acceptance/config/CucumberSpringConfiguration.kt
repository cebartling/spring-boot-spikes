package com.pintailconsultingllc.cqrsspike.acceptance.config

import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles

/**
 * Spring configuration for Cucumber acceptance tests.
 *
 * This class configures the Spring application context for acceptance tests,
 * including:
 * - Full Spring Boot context with WebFlux
 * - Connection to Docker Compose PostgreSQL instance
 * - Test profile activation for appropriate configuration
 *
 * IMPORTANT: Before running acceptance tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 *
 * The Docker Compose setup provides:
 * - PostgreSQL database with required schemas
 * - Vault (disabled in test profile)
 * - Observability stack (optional for tests)
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CucumberSpringConfiguration
