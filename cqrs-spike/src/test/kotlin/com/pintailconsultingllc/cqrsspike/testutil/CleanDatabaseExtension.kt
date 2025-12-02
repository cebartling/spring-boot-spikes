package com.pintailconsultingllc.cqrsspike.testutil

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.sql.Connection
import java.sql.DriverManager

/**
 * JUnit 5 extension that cleans the database before all tests in a test class.
 *
 * This extension can be used in two ways:
 *
 * 1. **@ExtendWith annotation** - Add to a test class to clean database before all tests:
 *    ```
 *    @ExtendWith(CleanDatabaseExtension::class)
 *    @SpringBootTest
 *    class MyIntegrationTest { ... }
 *    ```
 *
 * 2. **@CleanDatabase annotation** - Use the custom annotation for cleaner syntax:
 *    ```
 *    @CleanDatabase
 *    @SpringBootTest
 *    class MyIntegrationTest { ... }
 *    ```
 *
 * The extension connects directly to PostgreSQL using JDBC to perform cleanup,
 * ensuring it works even before the Spring context is fully initialized.
 *
 * Configuration is read from environment variables or defaults:
 * - TEST_DB_URL: jdbc:postgresql://localhost:5432/cqrs_db
 * - TEST_DB_USER: cqrs_user
 * - TEST_DB_PASSWORD: local_dev_password
 */
class CleanDatabaseExtension : BeforeAllCallback, BeforeEachCallback {

    private val logger = LoggerFactory.getLogger(CleanDatabaseExtension::class.java)

    companion object {
        private val DB_URL = System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:5432/cqrs_db"
        private val DB_USER = System.getenv("TEST_DB_USER") ?: "cqrs_user"
        private val DB_PASSWORD = System.getenv("TEST_DB_PASSWORD") ?: "local_dev_password"

        private const val CLEANUP_SQL = """
            -- Clean idempotency records
            DELETE FROM command_model.processed_command;
            -- Clean read model
            DELETE FROM read_model.product;
            DELETE FROM read_model.projection_position;
            -- Clean event store (events before streams due to FK)
            DELETE FROM event_store.domain_event;
            DELETE FROM event_store.event_stream;
        """

        // Track which test classes have been cleaned to avoid redundant cleanup
        private val cleanedClasses = mutableSetOf<String>()
    }

    override fun beforeAll(context: ExtensionContext) {
        val testClassName = context.requiredTestClass.name

        // Only clean once per test class
        if (testClassName in cleanedClasses) {
            logger.debug("Database already cleaned for $testClassName, skipping")
            return
        }

        cleanDatabase(testClassName)
        cleanedClasses.add(testClassName)
    }

    override fun beforeEach(context: ExtensionContext) {
        // Check if the test method or class has @CleanDatabaseBeforeEach
        val testClass = context.requiredTestClass
        val testMethod = context.requiredTestMethod

        val cleanBeforeEach = testMethod.isAnnotationPresent(CleanDatabaseBeforeEach::class.java) ||
            testClass.isAnnotationPresent(CleanDatabaseBeforeEach::class.java)

        if (cleanBeforeEach) {
            cleanDatabase("${testClass.simpleName}.${testMethod.name}")
        }
    }

    private fun cleanDatabase(context: String) {
        logger.info("Cleaning database before tests: $context")

        try {
            DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD).use { connection ->
                connection.autoCommit = false
                try {
                    connection.createStatement().use { statement ->
                        statement.execute(CLEANUP_SQL)
                    }
                    connection.commit()
                    logger.info("Database cleaned successfully for: $context")
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to clean database for $context: ${e.message}", e)
            // Don't fail the test - the test itself might handle missing data gracefully
            // or the TestDatabaseCleanup component will be used
        }
    }
}

/**
 * Annotation to mark a test class for database cleanup before all tests.
 *
 * Usage:
 * ```
 * @CleanDatabase
 * @SpringBootTest
 * class MyIntegrationTest { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@org.junit.jupiter.api.extension.ExtendWith(CleanDatabaseExtension::class)
annotation class CleanDatabase

/**
 * Annotation to mark a test class or method for database cleanup before each test.
 *
 * Usage on class (cleans before every test method):
 * ```
 * @CleanDatabaseBeforeEach
 * @SpringBootTest
 * class MyIntegrationTest { ... }
 * ```
 *
 * Usage on method (cleans before this specific test):
 * ```
 * @Test
 * @CleanDatabaseBeforeEach
 * fun myTest() { ... }
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@org.junit.jupiter.api.extension.ExtendWith(CleanDatabaseExtension::class)
annotation class CleanDatabaseBeforeEach
