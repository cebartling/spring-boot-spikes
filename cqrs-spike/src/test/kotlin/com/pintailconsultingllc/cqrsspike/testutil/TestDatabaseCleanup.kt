package com.pintailconsultingllc.cqrsspike.testutil

import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Utility for cleaning up test data from the database.
 *
 * This component provides methods to clean up test data between tests
 * to ensure test isolation when running integration tests against a
 * shared PostgreSQL database.
 *
 * Usage in tests:
 * ```
 * @Autowired
 * private lateinit var testDatabaseCleanup: TestDatabaseCleanup
 *
 * @BeforeEach
 * fun setUp() {
 *     testDatabaseCleanup.cleanAllTestData().block()
 * }
 * ```
 */
@Component
class TestDatabaseCleanup(
    private val databaseClient: DatabaseClient
) {
    private val logger = LoggerFactory.getLogger(TestDatabaseCleanup::class.java)

    /**
     * Cleans all test data from all schemas.
     * This is the recommended method to call in @BeforeEach for full isolation.
     */
    fun cleanAllTestData(): Mono<Void> {
        return cleanIdempotencyRecords()
            .then(cleanReadModel())
            .then(cleanEventStore())
            .doOnTerminate {
                logger.debug("All test data cleaned successfully")
            }
            .doOnError { error ->
                logger.error("Error cleaning test data", error)
            }
    }

    /**
     * Cleans only idempotency records from the command_model schema.
     * Use this for targeted cleanup in idempotency-specific tests.
     */
    fun cleanIdempotencyRecords(): Mono<Void> {
        return databaseClient.sql(CLEAN_IDEMPOTENCY_SQL)
            .fetch()
            .rowsUpdated()
            .doOnNext { count ->
                if (count > 0) {
                    logger.debug("Deleted $count idempotency records")
                }
            }
            .then()
    }

    /**
     * Cleans all data from the read_model schema.
     */
    fun cleanReadModel(): Mono<Void> {
        return databaseClient.sql(CLEAN_PRODUCTS_SQL)
            .then()
            .then(databaseClient.sql(CLEAN_PROJECTION_POSITION_SQL).then())
            .doOnTerminate {
                logger.debug("Read model data cleaned")
            }
    }

    /**
     * Cleans all data from the event_store schema.
     * Note: This deletes events first (due to foreign key), then streams.
     */
    fun cleanEventStore(): Mono<Void> {
        return databaseClient.sql(CLEAN_EVENTS_SQL)
            .then()
            .then(databaseClient.sql(CLEAN_STREAMS_SQL).then())
            .doOnTerminate {
                logger.debug("Event store data cleaned")
            }
    }

    /**
     * Cleans test data matching specific SKU patterns.
     * Useful for cleaning up after specific test classes.
     *
     * @param skuPatterns List of SKU patterns to match (e.g., "CREATE-%", "LIFECYCLE-%")
     */
    fun cleanTestDataBySkuPatterns(vararg skuPatterns: String): Mono<Void> {
        if (skuPatterns.isEmpty()) {
            return Mono.empty()
        }

        val patternCondition = skuPatterns.joinToString(" OR ") { "sku LIKE '$it'" }

        // Clean read model products matching patterns
        val cleanReadModelSql = """
            DELETE FROM read_model.product
            WHERE $patternCondition
        """.trimIndent()

        // Find and clean event streams for products matching patterns
        // We need to get aggregate IDs from event data
        val cleanEventsSql = """
            DELETE FROM event_store.domain_event
            WHERE stream_id IN (
                SELECT stream_id FROM event_store.event_stream
                WHERE aggregate_type = 'Product'
            )
        """.trimIndent()

        return databaseClient.sql(cleanReadModelSql)
            .then()
            .then(databaseClient.sql(cleanEventsSql).then())
            .doOnTerminate {
                logger.debug("Cleaned test data for patterns: ${skuPatterns.joinToString()}")
            }
    }

    /**
     * Cleans idempotency records matching specific key patterns.
     *
     * @param keyPatterns List of idempotency key patterns to match
     */
    fun cleanIdempotencyByPatterns(vararg keyPatterns: String): Mono<Void> {
        if (keyPatterns.isEmpty()) {
            return Mono.empty()
        }

        val patternCondition = keyPatterns.joinToString(" OR ") { "idempotency_key LIKE '$it'" }

        val sql = """
            DELETE FROM command_model.processed_command
            WHERE $patternCondition
        """.trimIndent()

        return databaseClient.sql(sql)
            .fetch()
            .rowsUpdated()
            .doOnNext { count ->
                if (count > 0) {
                    logger.debug("Deleted $count idempotency records matching patterns: ${keyPatterns.joinToString()}")
                }
            }
            .then()
    }

    companion object {
        /**
         * SQL to clean all idempotency records.
         */
        private const val CLEAN_IDEMPOTENCY_SQL = """
            DELETE FROM command_model.processed_command
        """

        /**
         * SQL to clean products from read model.
         */
        private const val CLEAN_PRODUCTS_SQL = """
            DELETE FROM read_model.product
        """

        /**
         * SQL to clean projection positions from read model.
         */
        private const val CLEAN_PROJECTION_POSITION_SQL = """
            DELETE FROM read_model.projection_position
        """

        /**
         * SQL to clean domain events.
         */
        private const val CLEAN_EVENTS_SQL = """
            DELETE FROM event_store.domain_event
        """

        /**
         * SQL to clean event streams.
         */
        private const val CLEAN_STREAMS_SQL = """
            DELETE FROM event_store.event_stream
        """
    }
}
