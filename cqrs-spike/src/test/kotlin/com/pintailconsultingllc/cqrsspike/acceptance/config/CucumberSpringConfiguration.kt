package com.pintailconsultingllc.cqrsspike.acceptance.config

import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.webtestclient.autoconfigure.WebTestClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import javax.sql.DataSource

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

/**
 * Test configuration that provides beans for acceptance tests.
 */
@Configuration
class TestConfiguration {

    /**
     * JdbcTemplate for database cleanup in Cucumber hooks.
     * Necessary because the main application uses R2DBC (reactive) and doesn't
     * auto-configure JdbcTemplate.
     */
    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate {
        return JdbcTemplate(dataSource)
    }

    /**
     * Customizer for WebTestClient to increase buffer size for large responses like Prometheus metrics.
     */
    @Bean
    fun webTestClientBuilderCustomizer(): WebTestClientBuilderCustomizer {
        return WebTestClientBuilderCustomizer { builder: WebTestClient.Builder ->
            builder.codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            }
        }
    }
}
