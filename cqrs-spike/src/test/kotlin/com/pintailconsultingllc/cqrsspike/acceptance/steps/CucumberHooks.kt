package com.pintailconsultingllc.cqrsspike.acceptance.steps

import io.cucumber.java.Before
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Cucumber hooks for test setup and cleanup.
 *
 * This class provides database cleanup before each scenario
 * to ensure test isolation.
 *
 * Uses direct DataSource connection with explicit commit to ensure
 * cleanup is not rolled back by Spring's test transaction management.
 */
class CucumberHooks(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(CucumberHooks::class.java)

    /**
     * Clean up all data before each scenario to ensure test isolation.
     * Order of deletion matters due to foreign key constraints.
     *
     * Uses direct JDBC connection to bypass Spring transaction management
     * and ensure deletes are committed immediately.
     */
    @Before(order = 0)
    fun cleanDatabase() {
        logger.info("Cleaning database before scenario...")

        dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { statement ->
                // Clean read_model schema
                statement.execute("DELETE FROM read_model.product")
                statement.execute("DELETE FROM read_model.projection_position")

                // Clean command_model schema
                statement.execute("DELETE FROM command_model.processed_command")
                statement.execute("DELETE FROM command_model.product")

                // Clean event_store schema - domain_event first due to FK
                statement.execute("DELETE FROM event_store.domain_event")
                statement.execute("DELETE FROM event_store.event_stream")
            }
        }

        logger.info("Database cleaned successfully")
    }
}
