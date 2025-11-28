package com.pintailconsultingllc.cqrsspike.infrastructure.database.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Flyway database migrations.
 *
 * These tests verify that:
 * - All migrations are applied successfully
 * - Schema versions are tracked correctly
 * - Migration checksums are valid
 * - Required schemas and tables exist
 */
@Disabled("Integration test requiring running PostgreSQL instance with Flyway migrations")
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private lateinit var dataSource: DataSource

    private val flyway: Flyway by lazy {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .schemas("event_store", "read_model", "command_model")
            .defaultSchema("command_model")
            .table("flyway_schema_history")
            .load()
    }

    @Test
    fun `should apply all migrations successfully`() {
        val info = flyway.info()
        val applied = info.applied()

        assertTrue(applied.isNotEmpty(), "No migrations applied")
        assertEquals(0, info.pending().size, "Pending migrations found: ${info.pending().joinToString { it.description }}")
    }

    @Test
    fun `should have correct schema version`() {
        val current = flyway.info().current()

        assertNotNull(current, "No current version")
        assertTrue(
            current.version.version.matches(Regex("\\d+")),
            "Invalid version format: ${current.version.version}"
        )
    }

    @Test
    fun `should validate migration checksums`() {
        val info = flyway.info()

        info.all().forEach { migration ->
            // Skip Flyway internal migrations (like schema creation) which don't have checksums
            if (migration.state.isApplied && migration.description != "<< Flyway Schema Creation >>") {
                assertNotNull(
                    migration.checksum,
                    "Applied migration missing checksum: ${migration.description}"
                )
            }
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

            assertTrue(schemas.contains("event_store"), "event_store schema not found")
            assertTrue(schemas.contains("read_model"), "read_model schema not found")
            assertTrue(schemas.contains("command_model"), "command_model schema not found")
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

            assertTrue(tables.contains("event_stream"), "event_stream table not found")
            assertTrue(tables.contains("domain_event"), "domain_event table not found")
        }
    }

    @Test
    fun `should have required PostgreSQL extensions`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    """
                    SELECT extname
                    FROM pg_extension
                    WHERE extname IN ('uuid-ossp', 'pgcrypto')
                    ORDER BY extname
                    """.trimIndent()
                ).use { rs ->
                    val extensions = mutableSetOf<String>()
                    while (rs.next()) {
                        extensions.add(rs.getString("extname"))
                    }

                    assertTrue(extensions.contains("uuid-ossp"), "uuid-ossp extension not found")
                    assertTrue(extensions.contains("pgcrypto"), "pgcrypto extension not found")
                }
            }
        }
    }

    @Test
    fun `should have event_stream indexes`() {
        dataSource.connection.use { conn ->
            conn.metaData.getIndexInfo(null, "event_store", "event_stream", false, false).use { rs ->
                val indexes = mutableSetOf<String>()
                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME")
                    if (indexName != null && indexName != "event_stream_pkey") {
                        indexes.add(indexName.lowercase())
                    }
                }

                assertTrue(
                    indexes.any { it.contains("aggregate") },
                    "event_stream aggregate index not found. Found: $indexes"
                )
                assertTrue(
                    indexes.any { it.contains("updated") },
                    "event_stream updated index not found. Found: $indexes"
                )
            }
        }
    }

    @Test
    fun `should have domain_event indexes`() {
        dataSource.connection.use { conn ->
            conn.metaData.getIndexInfo(null, "event_store", "domain_event", false, false).use { rs ->
                val indexes = mutableSetOf<String>()
                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME")
                    if (indexName != null && indexName != "domain_event_pkey") {
                        indexes.add(indexName.lowercase())
                    }
                }

                assertTrue(
                    indexes.any { it.contains("stream") },
                    "domain_event stream index not found. Found: $indexes"
                )
                assertTrue(
                    indexes.any { it.contains("type") },
                    "domain_event type index not found. Found: $indexes"
                )
                assertTrue(
                    indexes.any { it.contains("occurred") },
                    "domain_event occurred index not found. Found: $indexes"
                )
            }
        }
    }

    @Test
    fun `should have append_events function`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    """
                    SELECT proname
                    FROM pg_proc
                    WHERE proname = 'append_events'
                    AND pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'event_store')
                    """.trimIndent()
                ).use { rs ->
                    assertTrue(rs.next(), "append_events function not found")
                    assertEquals("append_events", rs.getString("proname"))
                }
            }
        }
    }

    @Test
    fun `should have event stream views`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    """
                    SELECT viewname
                    FROM pg_views
                    WHERE schemaname = 'event_store'
                    ORDER BY viewname
                    """.trimIndent()
                ).use { rs ->
                    val views = mutableSetOf<String>()
                    while (rs.next()) {
                        views.add(rs.getString("viewname"))
                    }

                    assertTrue(views.isNotEmpty(), "No views found in event_store schema")
                    assertTrue(
                        views.any { it.contains("recent_events") },
                        "v_recent_events view not found. Found: $views"
                    )
                }
            }
        }
    }

    @Test
    fun `should have flyway_schema_history table in command_model schema`() {
        dataSource.connection.use { conn ->
            val tables = mutableSetOf<String>()

            conn.metaData.getTables(null, "command_model", "flyway_schema_history", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"))
                }
            }

            assertTrue(
                tables.contains("flyway_schema_history"),
                "flyway_schema_history table not found in command_model schema"
            )
        }
    }

    @Test
    fun `should have stream update trigger`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    """
                    SELECT trigger_name
                    FROM information_schema.triggers
                    WHERE event_object_schema = 'event_store'
                    AND event_object_table = 'domain_event'
                    AND trigger_name = 'trigger_update_stream_timestamp'
                    """.trimIndent()
                ).use { rs ->
                    assertTrue(rs.next(), "trigger_update_stream_timestamp not found")
                    assertEquals("trigger_update_stream_timestamp", rs.getString("trigger_name"))
                }
            }
        }
    }
}
