package com.pintailconsultingllc.cqrsspike.infrastructure.database.migration

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * FlywayMigrationLogger logs database migration status after application startup.
 *
 * This component listens for the ApplicationReadyEvent and logs detailed information
 * about the current database migration state, including:
 * - Current schema version
 * - Total number of migrations
 * - Number of pending migrations
 *
 * @property dataSource The DataSource used to connect to the database
 */
@Component
class FlywayMigrationLogger(dataSource: DataSource) : ApplicationListener<ApplicationReadyEvent> {

    private val logger = LoggerFactory.getLogger(FlywayMigrationLogger::class.java)

    private val flyway: Flyway = Flyway.configure()
        .dataSource(dataSource)
        .load()

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        logMigrationStatus()
    }

    private fun logMigrationStatus() {
        logger.info("=".repeat(80))
        logger.info("DATABASE MIGRATION STATUS")
        logger.info("=".repeat(80))

        val info = flyway.info()
        val current = info.current()

        if (current != null) {
            logger.info("Current schema version: {}", current.version)
            logger.info("Current description: {}", current.description)
            logger.info("Applied on: {}", current.installedOn)
        } else {
            logger.warn("No migrations have been applied yet")
        }

        val allMigrations = info.all()
        val pendingMigrations = info.pending()

        logger.info("Total migrations: {}", allMigrations.size)
        logger.info("Pending migrations: {}", pendingMigrations.size)

        if (pendingMigrations.isNotEmpty()) {
            logger.warn("Warning: {} pending migrations detected", pendingMigrations.size)
            pendingMigrations.forEach { migration ->
                logger.warn("  - {} ({})", migration.description, migration.version)
            }
        }

        logger.info("=".repeat(80))
    }
}
