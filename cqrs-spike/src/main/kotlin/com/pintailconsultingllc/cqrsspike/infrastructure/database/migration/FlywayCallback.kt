package com.pintailconsultingllc.cqrsspike.infrastructure.database.migration

import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * FlywayCallback provides hooks for Flyway migration lifecycle events.
 *
 * This component listens to key Flyway events and logs information about:
 * - When migrations start (BEFORE_MIGRATE)
 * - When migrations complete successfully (AFTER_MIGRATE)
 * - When migrations fail (AFTER_MIGRATE_ERROR)
 *
 * Useful for monitoring, debugging, and auditing database migrations.
 */
@Component
class FlywayCallback : Callback {

    private val logger = LoggerFactory.getLogger(FlywayCallback::class.java)

    /**
     * Returns the name of this callback for logging purposes.
     */
    override fun getCallbackName(): String = "FlywayCallback"

    /**
     * Determines which events this callback should handle.
     */
    override fun supports(event: Event, context: Context): Boolean {
        return event in setOf(
            Event.BEFORE_MIGRATE,
            Event.AFTER_MIGRATE,
            Event.AFTER_MIGRATE_ERROR
        )
    }

    /**
     * Indicates whether this callback can handle events within a transaction.
     */
    override fun canHandleInTransaction(event: Event, context: Context): Boolean = true

    /**
     * Handles the callback events.
     */
    override fun handle(event: Event, context: Context) {
        when (event) {
            Event.BEFORE_MIGRATE -> handleBeforeMigrate()
            Event.AFTER_MIGRATE -> handleAfterMigrate(context)
            Event.AFTER_MIGRATE_ERROR -> handleAfterMigrateError()
            else -> {
                // This should never happen due to supports() filter
                logger.debug("Unhandled event: {}", event)
            }
        }
    }

    private fun handleBeforeMigrate() {
        logger.info("=" .repeat(80))
        logger.info("Starting database migration...")
        logger.info("=" .repeat(80))
    }

    private fun handleAfterMigrate(context: Context) {
        val migrationInfo = context.migrationInfo
        val version = migrationInfo?.version?.toString() ?: "unknown"
        val description = migrationInfo?.description ?: "unknown"

        logger.info("=" .repeat(80))
        logger.info("Database migration completed successfully")
        logger.info("Migration applied: {} - {}", version, description)
        logger.info("=" .repeat(80))
    }

    private fun handleAfterMigrateError() {
        logger.error("=" .repeat(80))
        logger.error("Database migration failed!")
        logger.error("Check the logs above for details on the failure")
        logger.error("=" .repeat(80))
    }
}
