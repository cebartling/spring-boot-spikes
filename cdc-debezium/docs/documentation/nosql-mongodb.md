# MongoDB Infrastructure

MongoDB serves as the target materialized store for CDC events in this project. The Spring Boot application consumes CDC events from Kafka and materializes the state into MongoDB collections.

## Architecture

```mermaid
flowchart LR
    PG[(PostgreSQL<br/>Source)] --> KAFKA[Kafka]
    KAFKA --> SB[Spring Boot<br/>Consumer]
    SB --> MONGO[(MongoDB<br/>Target)]
```

## Service Configuration

| Property | Value |
|----------|-------|
| Image | `mongo:8.2.2` |
| Container | `cdc-mongodb` |
| Port | `27017` |
| Database | `cdc_materialized` |
| Admin User | `admin` / `admin` |
| App User | `cdc_app` / `cdc_app_password` |

## Collections

The following collections are created during initialization:

| Collection | Purpose |
|------------|---------|
| `customers` | Materialized customer data from CDC events |

### Customer Collection Schema

The `customers` collection has JSON schema validation requiring:

```javascript
{
  _id: String,           // UUID as string
  email: String,         // Unique email address
  status: String,        // Customer status
  updatedAt: Date,       // Last update timestamp
  cdcMetadata: {
    sourceTimestamp: Long,   // Original CDC event timestamp
    operation: String,       // 'INSERT', 'UPDATE', or 'DELETE'
    kafkaOffset: Long,       // Kafka message offset
    kafkaPartition: Int,     // Kafka partition number
    processedAt: Date        // When the event was processed
  }
}
```

### Indexes

The `customers` collection includes the following indexes for query optimization:

| Index | Type | Purpose |
|-------|------|---------|
| `email` | Unique | Fast lookups by email, enforce uniqueness |
| `cdcMetadata.sourceTimestamp` | Descending | Query events by source time |
| `cdcMetadata.processedAt` | Descending | Query events by processing time |
| `status` | Ascending | Filter by customer status |

## Commands

### Start MongoDB

```bash
# Start MongoDB service
docker compose up -d mongodb

# Wait for healthy status
docker compose ps mongodb
```

### Connect to MongoDB

```bash
# Connect as admin
docker compose exec mongodb mongosh -u admin -p admin

# Connect as application user
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized
```

### Useful Queries

```bash
# List collections
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.getCollectionNames()"

# View indexes
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.customers.getIndexes()"

# Count documents
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.customers.countDocuments()"

# Find all customers
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.customers.find().pretty()"

# Find by email
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.customers.findOne({email: 'test@example.com'})"

# Query recent CDC events
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.customers.find().sort({'cdcMetadata.processedAt': -1}).limit(10).pretty()"
```

### Reset MongoDB Data

```bash
# Stop and remove container with volume
docker compose down mongodb
docker volume rm cdc-debezium_mongodb_data

# Restart (will reinitialize)
docker compose up -d mongodb
```

## Initialization Script

The initialization script (`docker/mongodb/init/01-init.js`) runs automatically on first container start and:

1. Creates the `cdc_app` user with `readWrite` permissions
2. Creates the `customers` collection with JSON schema validation
3. Creates indexes for query optimization

## Spring Boot Integration

The Spring Boot application is configured with reactive MongoDB access for persisting CDC events.

### Dependencies (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mongodb:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}
```

### Configuration (application.yml)

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://cdc_app:cdc_app_password@localhost:27017/cdc_materialized?authSource=cdc_materialized
      database: cdc_materialized
      auto-index-creation: false  # Indexes created in init script

logging:
  level:
    org.springframework.data.mongodb: DEBUG
    org.mongodb.driver: INFO
```

### MongoConfig

Custom MongoDB configuration with connection pool tuning:

```kotlin
@Configuration
@ConditionalOnProperty(name = ["app.mongodb.custom-config.enabled"], havingValue = "true", matchIfMissing = true)
@EnableReactiveMongoRepositories(basePackages = ["com.pintailconsultingllc.cdcdebezium.repository"])
class MongoConfig : AbstractReactiveMongoConfiguration() {
    // Custom connection pool: max 20, min 5 connections
    // Socket timeout: 10s connect, 30s read
    // Removes _class field from documents
}
```

**File:** `src/main/kotlin/com/pintailconsultingllc/cdcdebezium/config/MongoConfig.kt`

### Domain Model

#### CdcMetadata (Embedded Document)

```kotlin
data class CdcMetadata(
    val sourceTimestamp: Long,
    val operation: CdcOperation,  // INSERT, UPDATE, DELETE
    val kafkaOffset: Long,
    val kafkaPartition: Int,
    val processedAt: Instant = Instant.now()
)
```

**File:** `src/main/kotlin/com/pintailconsultingllc/cdcdebezium/document/CdcMetadata.kt`

#### CustomerDocument

```kotlin
@Document(collection = "customers")
@CompoundIndex(name = "idx_status_updated", def = "{'status': 1, 'updatedAt': -1}")
data class CustomerDocument(
    @Id val id: String,
    @Indexed(unique = true) val email: String,
    @Indexed val status: String,
    val updatedAt: Instant,
    val cdcMetadata: CdcMetadata
) {
    companion object {
        fun fromCdcEvent(...): CustomerDocument  // Factory method
    }

    fun isNewerThan(other: CustomerDocument): Boolean  // Timestamp comparison
}
```

**File:** `src/main/kotlin/com/pintailconsultingllc/cdcdebezium/document/CustomerDocument.kt`

### Repository

```kotlin
@Repository
interface CustomerMongoRepository : ReactiveMongoRepository<CustomerDocument, String> {
    fun findByEmail(email: String): Mono<CustomerDocument>
    fun findByStatus(status: String): Flux<CustomerDocument>
    fun findByStatusOrderByUpdatedAtDesc(status: String): Flux<CustomerDocument>
    fun existsByEmail(email: String): Mono<Boolean>
}
```

**File:** `src/main/kotlin/com/pintailconsultingllc/cdcdebezium/repository/CustomerMongoRepository.kt`

## Testing

### Unit Tests

```bash
# Run document model unit tests
./gradlew test --tests "*CustomerDocument*"
./gradlew test --tests "*CdcMetadata*"
```

### Acceptance Tests

MongoDB acceptance tests are available for verifying the Spring Data configuration:

```bash
# Start MongoDB first
docker compose up -d mongodb

# Run MongoDB acceptance tests
./gradlew mongoDbTest
```

**Feature file:** `src/acceptanceTest/resources/features/mongodb_spring_configuration.feature`

Scenarios covered:
- MongoDB accessibility check
- ReactiveMongoRepository bean availability
- Document save and retrieve operations
- CdcMetadata embedding verification
- Query by status functionality

**Step definitions:** `src/acceptanceTest/kotlin/com/pintailconsultingllc/cdcdebezium/steps/MongoDbConfigurationSteps.kt`

## Troubleshooting

### Container Won't Start

```bash
# Check container logs
docker compose logs mongodb

# Verify volume permissions
docker volume inspect cdc-debezium_mongodb_data
```

### Authentication Failed

```bash
# Verify user exists
docker compose exec mongodb mongosh -u admin -p admin --eval \
  "db.getSiblingDB('cdc_materialized').getUsers()"
```

### Initialization Script Not Running

The init script only runs on first container start with an empty data volume:

```bash
# Remove volume to force reinitialization
docker compose down mongodb
docker volume rm cdc-debezium_mongodb_data
docker compose up -d mongodb
```

### Schema Validation Errors

If inserts fail with validation errors, check the document structure:

```bash
# View collection validator
docker compose exec mongodb mongosh \
  -u cdc_app -p cdc_app_password \
  --authenticationDatabase cdc_materialized \
  cdc_materialized --eval "db.getCollectionInfos({name: 'customers'})[0].options.validator"
```
