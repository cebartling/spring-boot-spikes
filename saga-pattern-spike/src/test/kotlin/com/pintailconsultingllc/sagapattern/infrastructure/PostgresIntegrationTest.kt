package com.pintailconsultingllc.sagapattern.infrastructure

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.Socket

/**
 * Integration tests for PostgreSQL database infrastructure.
 *
 * These tests verify that the PostgreSQL server is running and the schema
 * has been properly initialized with all required tables.
 *
 * Requires Docker Compose services to be running:
 *   docker compose up -d
 */
@Tag("integration")
@DisplayName("PostgreSQL Infrastructure Integration Tests")
class PostgresIntegrationTest {

    companion object {
        private const val POSTGRES_HOST = "localhost"
        private const val POSTGRES_PORT = 5432
        private const val POSTGRES_DB = "saga_db"
        private const val POSTGRES_USER = "saga_user"
        private const val POSTGRES_PASSWORD = "saga_password"

        private lateinit var connectionFactory: ConnectionFactory

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Skip tests if PostgreSQL is not running
            Assumptions.assumeTrue(
                isPortOpen(POSTGRES_HOST, POSTGRES_PORT),
                "PostgreSQL server is not running at $POSTGRES_HOST:$POSTGRES_PORT. Start with: docker compose up -d"
            )

            connectionFactory = ConnectionFactories.get(
                ConnectionFactoryOptions.builder()
                    .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                    .option(ConnectionFactoryOptions.HOST, POSTGRES_HOST)
                    .option(ConnectionFactoryOptions.PORT, POSTGRES_PORT)
                    .option(ConnectionFactoryOptions.DATABASE, POSTGRES_DB)
                    .option(ConnectionFactoryOptions.USER, POSTGRES_USER)
                    .option(ConnectionFactoryOptions.PASSWORD, POSTGRES_PASSWORD)
                    .build()
            )
        }

        private fun isPortOpen(host: String, port: Int): Boolean {
            return try {
                Socket(host, port).use { true }
            } catch (e: Exception) {
                false
            }
        }
    }

    @Nested
    @DisplayName("Database Connectivity")
    inner class ConnectivityTests {

        @Test
        @DisplayName("Should connect to PostgreSQL database")
        fun shouldConnectToDatabase() {
            val connection = Mono.from(connectionFactory.create())

            StepVerifier.create(connection)
                .expectNextMatches { conn ->
                    val metadata = conn.metadata
                    metadata.databaseProductName.contains("PostgreSQL")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should execute simple query")
        fun shouldExecuteSimpleQuery() {
            val result: Flux<Int> = Mono.from(connectionFactory.create())
                .flatMapMany { connection ->
                    Flux.from(connection.createStatement("SELECT 1 as value").execute())
                        .flatMap { result ->
                            Flux.from(result.map { row, _ -> row.get("value", Integer::class.java)?.toInt() ?: 0 })
                        }
                        .doFinally { connection.close() }
                }

            StepVerifier.create(result)
                .expectNext(1)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Schema Verification")
    inner class SchemaTests {

        @Test
        @DisplayName("Should have orders table")
        fun shouldHaveOrdersTable() {
            verifyTableExists("orders")
        }

        @Test
        @DisplayName("Should have order_items table")
        fun shouldHaveOrderItemsTable() {
            verifyTableExists("order_items")
        }

        @Test
        @DisplayName("Should have saga_executions table")
        fun shouldHaveSagaExecutionsTable() {
            verifyTableExists("saga_executions")
        }

        @Test
        @DisplayName("Should have saga_step_results table")
        fun shouldHaveSagaStepResultsTable() {
            verifyTableExists("saga_step_results")
        }

        @Test
        @DisplayName("Should have order_events table")
        fun shouldHaveOrderEventsTable() {
            verifyTableExists("order_events")
        }

        @Test
        @DisplayName("Should have retry_attempts table")
        fun shouldHaveRetryAttemptsTable() {
            verifyTableExists("retry_attempts")
        }

        private fun verifyTableExists(tableName: String) {
            val query = """
                SELECT COUNT(*) as count
                FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = '$tableName'
            """.trimIndent()

            val result: Flux<Long> = Mono.from(connectionFactory.create())
                .flatMapMany { connection ->
                    Flux.from(connection.createStatement(query).execute())
                        .flatMap { result ->
                            Flux.from(result.map { row, _ -> row.get("count", java.lang.Long::class.java)?.toLong() ?: 0L })
                        }
                        .doFinally { connection.close() }
                }

            StepVerifier.create(result)
                .expectNextMatches { count -> count > 0 }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Table Structure Verification")
    inner class TableStructureTests {

        @Test
        @DisplayName("Orders table should have required columns")
        fun ordersTableShouldHaveRequiredColumns() {
            val expectedColumns = listOf("id", "customer_id", "total_amount", "status", "created_at", "updated_at")
            verifyTableColumns("orders", expectedColumns)
        }

        @Test
        @DisplayName("Saga executions table should have required columns")
        fun sagaExecutionsTableShouldHaveRequiredColumns() {
            val expectedColumns = listOf(
                "id", "order_id", "current_step", "status", "failed_step",
                "failure_reason", "started_at", "completed_at"
            )
            verifyTableColumns("saga_executions", expectedColumns)
        }

        @Test
        @DisplayName("Order events table should have required columns")
        fun orderEventsTableShouldHaveRequiredColumns() {
            val expectedColumns = listOf(
                "id", "order_id", "event_type", "step_name", "outcome",
                "details", "error_code", "error_message", "timestamp"
            )
            verifyTableColumns("order_events", expectedColumns)
        }

        private fun verifyTableColumns(tableName: String, expectedColumns: List<String>) {
            val query = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                AND table_name = '$tableName'
            """.trimIndent()

            val result: Mono<List<String>> = Mono.from(connectionFactory.create())
                .flatMapMany { connection ->
                    Flux.from(connection.createStatement(query).execute())
                        .flatMap { result ->
                            Flux.from(result.map { row, _ -> row.get("column_name", String::class.java) ?: "" })
                        }
                        .doFinally { connection.close() }
                }
                .collectList()

            StepVerifier.create(result)
                .expectNextMatches { columns ->
                    expectedColumns.all { expected -> columns.contains(expected) }
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Index Verification")
    inner class IndexTests {

        @Test
        @DisplayName("Should have index on orders customer_id")
        fun shouldHaveIndexOnOrdersCustomerId() {
            verifyIndexExists("idx_orders_customer_id")
        }

        @Test
        @DisplayName("Should have index on orders status")
        fun shouldHaveIndexOnOrdersStatus() {
            verifyIndexExists("idx_orders_status")
        }

        @Test
        @DisplayName("Should have index on saga_executions order_id")
        fun shouldHaveIndexOnSagaExecutionsOrderId() {
            verifyIndexExists("idx_saga_executions_order_id")
        }

        @Test
        @DisplayName("Should have index on order_events order_id")
        fun shouldHaveIndexOnOrderEventsOrderId() {
            verifyIndexExists("idx_order_events_order_id")
        }

        private fun verifyIndexExists(indexName: String) {
            val query = """
                SELECT COUNT(*) as count
                FROM pg_indexes
                WHERE schemaname = 'public'
                AND indexname = '$indexName'
            """.trimIndent()

            val result: Flux<Long> = Mono.from(connectionFactory.create())
                .flatMapMany { connection ->
                    Flux.from(connection.createStatement(query).execute())
                        .flatMap { result ->
                            Flux.from(result.map { row, _ -> row.get("count", java.lang.Long::class.java)?.toLong() ?: 0L })
                        }
                        .doFinally { connection.close() }
                }

            StepVerifier.create(result)
                .expectNextMatches { count -> count > 0 }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Trigger Verification")
    inner class TriggerTests {

        @Test
        @DisplayName("Should have update_orders_updated_at trigger")
        fun shouldHaveUpdateOrdersUpdatedAtTrigger() {
            val query = """
                SELECT COUNT(*) as count
                FROM information_schema.triggers
                WHERE trigger_schema = 'public'
                AND trigger_name = 'update_orders_updated_at'
            """.trimIndent()

            val result: Flux<Long> = Mono.from(connectionFactory.create())
                .flatMapMany { connection ->
                    Flux.from(connection.createStatement(query).execute())
                        .flatMap { result ->
                            Flux.from(result.map { row, _ -> row.get("count", java.lang.Long::class.java)?.toLong() ?: 0L })
                        }
                        .doFinally { connection.close() }
                }

            StepVerifier.create(result)
                .expectNextMatches { count -> count > 0 }
                .verifyComplete()
        }
    }
}
