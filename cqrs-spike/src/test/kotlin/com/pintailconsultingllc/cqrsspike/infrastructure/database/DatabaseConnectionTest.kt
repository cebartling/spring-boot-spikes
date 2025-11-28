package com.pintailconsultingllc.cqrsspike.infrastructure.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

/**
 * Integration tests for database connectivity.
 *
 * These tests verify that the application can connect to PostgreSQL
 * and that the required schemas are created during initialization.
 *
 * Note: These tests require a running PostgreSQL instance.
 * Run `docker-compose up -d postgres` before executing these tests.
 */
@Disabled("Integration test requiring running PostgreSQL instance")
@SpringBootTest
@ActiveProfiles("test")
class DatabaseConnectionTest {

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `should connect to database`() {
        dataSource.connection.use { conn ->
            assertTrue(conn.isValid(5), "Connection should be valid")
            assertEquals("cqrs_db", conn.catalog, "Should connect to cqrs_db database")
        }
    }

    @Test
    fun `should have required schemas`() {
        dataSource.connection.use { conn ->
            val schemas = mutableSetOf<String>()

            conn.metaData.schemas.use { rs ->
                while (rs.next()) {
                    schemas.add(rs.getString("TABLE_SCHEM"))
                }
            }

            assertTrue(schemas.contains("event_store"), "Should have event_store schema")
            assertTrue(schemas.contains("read_model"), "Should have read_model schema")
            assertTrue(schemas.contains("command_model"), "Should have command_model schema")
        }
    }

    @Test
    fun `should have event store tables`() {
        dataSource.connection.use { conn ->
            val tables = mutableSetOf<String>()

            conn.metaData.getTables(null, "event_store", "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"))
                }
            }

            assertTrue(tables.contains("event_stream"), "Should have event_stream table")
            assertTrue(tables.contains("domain_event"), "Should have domain_event table")
        }
    }

    @Test
    fun `should have uuid-ossp extension`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp')").use { rs ->
                    assertTrue(rs.next(), "Query should return a result")
                    assertTrue(rs.getBoolean(1), "uuid-ossp extension should be installed")
                }
            }
        }
    }

    @Test
    fun `should have pgcrypto extension`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto')").use { rs ->
                    assertTrue(rs.next(), "Query should return a result")
                    assertTrue(rs.getBoolean(1), "pgcrypto extension should be installed")
                }
            }
        }
    }
}
