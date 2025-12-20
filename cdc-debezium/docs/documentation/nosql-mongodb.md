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

## Connection from Spring Boot

To connect the Spring Boot application to MongoDB, add the following dependencies and configuration:

### Dependencies (build.gradle.kts)

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
```

### Configuration (application.yml)

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://cdc_app:cdc_app_password@localhost:27017/cdc_materialized?authSource=cdc_materialized
```

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
