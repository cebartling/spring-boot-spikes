# PLAN-012: MongoDB Spring Data Configuration

## Implementation Status: COMPLETED

| Aspect | Status |
|--------|--------|
| Dependencies | Implemented |
| Configuration | Implemented |
| MongoConfig | Implemented |
| CdcMetadata | Implemented |
| CustomerDocument | Implemented |
| CustomerMongoRepository | Implemented |
| Unit Tests | Implemented |
| Acceptance Tests | Implemented |

**Pull Request:** [#132](https://github.com/cebartling/spring-boot-spikes/pull/132)

## Objective

Configure Spring Boot for reactive MongoDB access, replacing R2DBC PostgreSQL for the materialized store while maintaining the source PostgreSQL connection for CDC.

## Parent Feature

[FEATURE-002](../features/FEATURE-002.md) - Sections 2.1.2, 2.1.3: MongoDB Configuration and Document Model

## Dependencies

- PLAN-011: MongoDB Infrastructure Setup

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add MongoDB dependencies, adjust R2DBC scope |
| `src/main/resources/application.yml` | MongoDB connection configuration |
| `src/.../config/MongoConfig.kt` | MongoDB client configuration |
| `src/.../document/CustomerDocument.kt` | MongoDB document model |
| `src/.../document/CdcMetadata.kt` | Embedded CDC metadata |
| `src/.../repository/CustomerMongoRepository.kt` | Reactive MongoDB repository |

### build.gradle.kts Changes

```kotlin
dependencies {
    // Add MongoDB reactive support
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    // Keep R2DBC for source database queries if needed
    // Or remove if only using PostgreSQL as CDC source
    // implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    // implementation("org.postgresql:r2dbc-postgresql")

    // Test dependencies
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x:4.11.0")
}
```

### application.yml Configuration

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://cdc_app:cdc_app_password@localhost:27017/cdc_materialized?authSource=cdc_materialized
      database: cdc_materialized
      auto-index-creation: false  # Indexes created in init script

  # Keep R2DBC config if needed for source DB access
  # r2dbc:
  #   url: r2dbc:postgresql://localhost:5432/postgres
  #   username: postgres
  #   password: postgres

logging:
  level:
    org.springframework.data.mongodb: DEBUG
    org.mongodb.driver: INFO
```

### MongoConfig.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories
import java.util.concurrent.TimeUnit

@Configuration
@EnableReactiveMongoRepositories(basePackages = ["com.pintailconsultingllc.cdcdebezium.repository"])
class MongoConfig : AbstractReactiveMongoConfiguration() {

    @Value("\${spring.data.mongodb.uri}")
    private lateinit var mongoUri: String

    @Value("\${spring.data.mongodb.database}")
    private lateinit var databaseName: String

    override fun getDatabaseName(): String = databaseName

    @Bean
    override fun reactiveMongoClient(): MongoClient {
        val connectionString = ConnectionString(mongoUri)

        val settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings { builder ->
                builder
                    .maxSize(20)
                    .minSize(5)
                    .maxWaitTime(30, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { builder ->
                builder
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
            }
            .build()

        return MongoClients.create(settings)
    }

    @Bean
    fun reactiveMongoTemplate(
        factory: ReactiveMongoDatabaseFactory,
        converter: MappingMongoConverter
    ): ReactiveMongoTemplate {
        // Remove _class field from documents
        converter.setTypeMapper(DefaultMongoTypeMapper(null))
        return ReactiveMongoTemplate(factory, converter)
    }

    @Bean
    fun transactionManager(factory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(factory)
    }
}
```

### CdcMetadata.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.document

import java.time.Instant

/**
 * Embedded document containing CDC event metadata for audit and idempotency.
 */
data class CdcMetadata(
    val sourceTimestamp: Long,
    val operation: CdcOperation,
    val kafkaOffset: Long,
    val kafkaPartition: Int,
    val processedAt: Instant = Instant.now()
)

enum class CdcOperation {
    INSERT,
    UPDATE,
    DELETE
}
```

### CustomerDocument.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document representing a materialized customer from CDC events.
 *
 * Uses String ID (UUID formatted) for compatibility with PostgreSQL source.
 * Includes CDC metadata for audit trail and idempotent processing.
 */
@Document(collection = "customers")
@CompoundIndex(name = "idx_status_updated", def = "{'status': 1, 'updatedAt': -1}")
data class CustomerDocument(
    @Id
    val id: String,  // UUID as string for cross-database compatibility

    @Indexed(unique = true)
    val email: String,

    @Indexed
    val status: String,

    val updatedAt: Instant,

    val cdcMetadata: CdcMetadata
) {
    companion object {
        /**
         * Factory method to create a new CustomerDocument from CDC event data.
         */
        fun fromCdcEvent(
            id: String,
            email: String,
            status: String,
            updatedAt: Instant,
            sourceTimestamp: Long,
            operation: CdcOperation,
            kafkaOffset: Long,
            kafkaPartition: Int
        ): CustomerDocument = CustomerDocument(
            id = id,
            email = email,
            status = status,
            updatedAt = updatedAt,
            cdcMetadata = CdcMetadata(
                sourceTimestamp = sourceTimestamp,
                operation = operation,
                kafkaOffset = kafkaOffset,
                kafkaPartition = kafkaPartition
            )
        )
    }

    /**
     * Check if this document is newer than another based on source timestamp.
     */
    fun isNewerThan(other: CustomerDocument): Boolean =
        this.cdcMetadata.sourceTimestamp > other.cdcMetadata.sourceTimestamp
}
```

### CustomerMongoRepository.kt

```kotlin
package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface CustomerMongoRepository : ReactiveMongoRepository<CustomerDocument, String> {

    fun findByEmail(email: String): Mono<CustomerDocument>

    fun findByStatus(status: String): Flux<CustomerDocument>

    fun findByStatusOrderByUpdatedAtDesc(status: String): Flux<CustomerDocument>

    fun existsByEmail(email: String): Mono<Boolean>
}
```

## Architecture

```mermaid
flowchart TB
    subgraph APP["Spring Boot Application"]
        subgraph CONFIG["Configuration"]
            MC[MongoConfig]
            PROPS[application.yml]
        end

        subgraph DOMAIN["Domain Layer"]
            DOC[CustomerDocument]
            META[CdcMetadata]
        end

        subgraph REPO["Repository Layer"]
            MONGOREPO[CustomerMongoRepository]
            TEMPLATE[ReactiveMongoTemplate]
        end
    end

    subgraph MONGO["MongoDB"]
        DB[(cdc_materialized)]
        COLL[customers collection]
    end

    MC --> TEMPLATE
    PROPS --> MC
    DOC --> MONGOREPO
    META --> DOC
    MONGOREPO --> TEMPLATE
    TEMPLATE --> DB
    DB --> COLL
```

## Commands to Run

```bash
# Ensure MongoDB is running
docker compose up -d mongodb

# Build the application
./gradlew clean build

# Run tests
./gradlew test --tests "*MongoRepository*"
./gradlew test --tests "*CustomerDocument*"

# Start the application
./gradlew bootRun

# Verify MongoDB connection in logs
# Look for: "Opened connection to localhost:27017"

# Check application health
curl http://localhost:8080/actuator/health

# Insert a test document via application
# (requires consumer to be running and processing events)
```

## Acceptance Criteria

The following acceptance criteria should be implemented as Cucumber acceptance tests:

```gherkin
Feature: MongoDB Spring Data Configuration
  As a developer
  I want Spring Boot configured with reactive MongoDB
  So that I can persist CDC events to MongoDB

  Scenario: Application starts with MongoDB connection
    Given the application.yml contains MongoDB configuration
    When the application starts
    Then MongoDB connection pool should be initialized
    And the application health check should show MongoDB as UP

  Scenario: ReactiveMongoRepository is available
    Given the application context is loaded
    When I autowire CustomerMongoRepository
    Then the repository bean should be available
    And it should be a reactive repository

  Scenario: Document can be saved and retrieved
    Given MongoDB is running
    And the application is started
    When I save a CustomerDocument with id "test-123"
    Then the document should be persisted in MongoDB
    And I should be able to retrieve it by id

  Scenario: CdcMetadata is properly embedded
    Given MongoDB is running
    When I save a CustomerDocument with CDC metadata
    Then the document should contain cdcMetadata field
    And cdcMetadata should have sourceTimestamp
    And cdcMetadata should have operation
    And cdcMetadata should have processedAt

  Scenario: Unique email constraint is enforced
    Given a CustomerDocument with email "unique@test.com" exists
    When I try to save another document with the same email
    Then a duplicate key exception should be thrown

  Scenario: Documents can be queried by status
    Given multiple CustomerDocuments with different statuses exist
    When I query for documents with status "active"
    Then only documents with status "active" should be returned
    And they should be ordered by updatedAt descending
```

## Estimated Complexity

Medium - Requires understanding of Spring Data MongoDB reactive patterns and proper configuration.

## Notes

- Using `AbstractReactiveMongoConfiguration` for full control over MongoDB client settings
- Removed `_class` field from documents via custom type mapper for cleaner documents
- Connection pool settings tuned for CDC workload (higher min/max connections)
- Transaction manager configured but optional for single-document operations
- Email uniqueness enforced at both application and database level
- Index annotations in document class are for documentation; actual indexes created in init script

## Implementation Notes

### Changes from Original Plan

1. **Test Dependencies**: Changed from `de.flapdoodle.embed.mongo.spring3x` to Testcontainers due to Spring Boot 4.x compatibility issues with embedded MongoDB
2. **MongoConfig**: Added `@ConditionalOnProperty` for test flexibility
3. **Package Structure**: Spring Boot 4.x uses reorganized packages (e.g., `org.springframework.boot.data.mongodb.autoconfigure` instead of `org.springframework.boot.autoconfigure.data.mongo`)

### Files Implemented

| File | Location |
|------|----------|
| MongoConfig.kt | `src/main/kotlin/.../config/` |
| CdcMetadata.kt | `src/main/kotlin/.../document/` |
| CustomerDocument.kt | `src/main/kotlin/.../document/` |
| CustomerMongoRepository.kt | `src/main/kotlin/.../repository/` |
| CdcMetadataTest.kt | `src/test/kotlin/.../document/` |
| CustomerDocumentTest.kt | `src/test/kotlin/.../document/` |
| mongodb_spring_configuration.feature | `src/acceptanceTest/resources/features/` |
| MongoDbConfigurationSteps.kt | `src/acceptanceTest/kotlin/.../steps/` |
| RunMongoDbTest.kt | `src/acceptanceTest/kotlin/...` |

### Running Tests

```bash
# Unit tests
./gradlew test --tests "*CustomerDocument*" --tests "*CdcMetadata*"

# Acceptance tests (requires MongoDB running)
docker compose up -d mongodb
./gradlew mongoDbTest
```
