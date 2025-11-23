package com.pintailconsultingllc.cqrsspike.infrastructure.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Database configuration for PostgreSQL with HikariCP connection pooling.
 *
 * This configuration retrieves database credentials from Vault via Spring Cloud Vault
 * and sets up an optimized HikariCP connection pool for the CQRS application.
 *
 * Note: R2DBC configuration is handled by Spring Boot auto-configuration via application.yml.
 * This class provides JDBC DataSource for blocking operations (migrations, admin tools).
 */
@Configuration
class DatabaseConfiguration {

    private val logger = LoggerFactory.getLogger(DatabaseConfiguration::class.java)

    @Value("\${spring.datasource.url}")
    private lateinit var jdbcUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var username: String

    @Value("\${spring.datasource.password}")
    private lateinit var password: String

    /**
     * Creates a HikariCP DataSource for blocking JDBC operations.
     *
     * This is used for migrations, admin tools, and any non-reactive database access.
     * For reactive operations, use R2DBC via Spring Data R2DBC repositories.
     *
     * @return Configured HikariCP DataSource
     */
    @Bean
    @Primary
    fun dataSource(): DataSource {
        logger.info("Configuring HikariCP DataSource")
        logger.info("Database URL: {}", jdbcUrl)
        logger.info("Database User: {}", username)

        val config = HikariConfig().apply {
            this.jdbcUrl = this@DatabaseConfiguration.jdbcUrl
            this.username = this@DatabaseConfiguration.username
            this.password = this@DatabaseConfiguration.password
            driverClassName = "org.postgresql.Driver"

            // Pool settings
            maximumPoolSize = 10
            minimumIdle = 5
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            isAutoCommit = false

            // Performance optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

            // Connection test
            connectionTestQuery = "SELECT 1"
        }

        logger.info("HikariCP configuration complete")
        return HikariDataSource(config)
    }
}
