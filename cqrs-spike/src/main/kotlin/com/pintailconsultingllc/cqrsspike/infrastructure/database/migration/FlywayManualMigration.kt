package com.pintailconsultingllc.cqrsspike.infrastructure.database.migration

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Manual Flyway migration runner.
 *
 * This component explicitly runs Flyway migrations on application startup.
 * It's ordered to run early (@Order(1)) to ensure migrations complete
 * before other application components initialize.
 */
@Component
@Order(1)
class FlywayManualMigration(
    private val dataSource: DataSource
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(FlywayManualMigration::class.java)

    override fun run(vararg args: String) {
        logger.info("=".repeat(80))
        logger.info("Running Flyway migrations manually...")
        logger.info("=".repeat(80))

        try {
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("event_store", "read_model", "command_model")
                .defaultSchema("command_model")
                .createSchemas(true)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .table("flyway_schema_history")
                .placeholders(mapOf("application_user" to "cqrs_user"))
                .load()

            val result = flyway.migrate()

            logger.info("=".repeat(80))
            logger.info("Flyway migration completed")
            logger.info("Migrations executed: {}", result.migrationsExecuted)
            logger.info("Target schema version: {}", result.targetSchemaVersion)

            if (result.migrations.isNotEmpty()) {
                logger.info("Applied migrations:")
                result.migrations.forEach { migration ->
                    logger.info("  - {} ({})", migration.description, migration.version)
                }
            }
            logger.info("=".repeat(80))

        } catch (ex: Exception) {
            logger.error("=".repeat(80))
            logger.error("Flyway migration failed!", ex)
            logger.error("=".repeat(80))
            throw ex
        }
    }
}
