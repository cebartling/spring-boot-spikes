package com.pintailconsultingllc.cqrsspike.acceptance.config

import io.cucumber.java.Before
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Cucumber hooks for test setup and cleanup.
 *
 * This class provides database cleanup before each scenario
 * to ensure test isolation.
 */
class CucumberHooks {

    private val logger = LoggerFactory.getLogger(CucumberHooks::class.java)

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    /**
     * Clean up all data before each scenario to ensure test isolation.
     * Order of deletion matters due to foreign key constraints.
     */
    @Before(order = 0)
    fun cleanDatabase() {
        logger.info("Cleaning database before scenario...")

        // Clean read_model schema
        jdbcTemplate.execute("DELETE FROM read_model.product")
        jdbcTemplate.execute("DELETE FROM read_model.projection_position")

        // Clean command_model schema
        jdbcTemplate.execute("DELETE FROM command_model.processed_command")
        jdbcTemplate.execute("DELETE FROM command_model.product")

        // Clean event_store schema - domain_event first due to FK
        jdbcTemplate.execute("DELETE FROM event_store.domain_event")
        jdbcTemplate.execute("DELETE FROM event_store.event_stream")

        logger.info("Database cleaned successfully")
    }
}
