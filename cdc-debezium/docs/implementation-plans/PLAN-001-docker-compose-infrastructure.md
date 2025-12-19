# PLAN-001: Docker Compose Base Infrastructure

## Objective

Set up the foundational Docker Compose infrastructure with Kafka (KRaft mode) and PostgreSQL with logical replication enabled.

## Dependencies

- None (first plan)

## Changes

### Files to Create

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Main orchestration file |

### docker-compose.yml

Create with the following services:

```yaml
services:
  postgres:
    image: quay.io/debezium/postgres:16
    # Pre-configured for logical replication (wal_level=logical)
    # Includes pgoutput plugin for Debezium
    # Expose port 5432
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: postgres

  kafka:
    image: confluentinc/cp-kafka:latest
    # KRaft mode configuration (no ZooKeeper)
    # Single-node cluster
    # Expose ports 9092 (internal), 29092 (external)
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093,PLAINTEXT_HOST://0.0.0.0:29092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    depends_on:
      - kafka
    ports:
      - "8081:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      DYNAMIC_CONFIG_ENABLED: "true"
```

### PostgreSQL Configuration

The `quay.io/debezium/postgres:16` image comes pre-configured with:
- `wal_level = logical`
- `max_replication_slots = 10`
- `max_wal_senders = 10`

No custom configuration files are required.

## Commands to Run

```bash
# Start infrastructure
docker compose up -d postgres kafka kafka-ui

# Verify PostgreSQL is ready
docker compose exec postgres pg_isready

# Verify Kafka is ready (KRaft mode)
docker compose exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Check PostgreSQL logical replication is enabled
docker compose exec postgres psql -U postgres -c "SHOW wal_level;"
# Expected: logical

# Test Kafka by creating and listing a topic
docker compose exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --topic test-topic --partitions 1 --replication-factor 1

docker compose exec kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Open Kafka UI in browser
open http://localhost:8081
```

## Acceptance Criteria

1. [x] `docker compose up -d postgres kafka kafka-ui` starts all services without errors
2. [x] PostgreSQL accepts connections on port 5432
3. [x] `SHOW wal_level;` returns `logical`
4. [x] Kafka broker is reachable on port 9092 (internal) and 29092 (external)
5. [x] Can create and list Kafka topics
6. [x] Kafka UI is accessible at http://localhost:8081
7. [x] Kafka UI shows the Kafka cluster and topics
8. [x] Services restart cleanly after `docker compose down && docker compose up -d`
9. [x] Ensure services have health checks configured (if applicable)

## Estimated Complexity

Low - Standard Docker Compose setup with well-documented configurations.

## Notes

- Use Confluent Platform Kafka image (`confluentinc/cp-kafka:latest`) with KRaft mode (no ZooKeeper required)
- Use Debezium PostgreSQL image (`quay.io/debezium/postgres:16`) which comes pre-configured for CDC with logical replication enabled and pgoutput plugin installed
- Use Kafka UI (`provectuslabs/kafka-ui:latest`) for visual topic/message inspection and consumer group monitoring
- Keep volumes for data persistence during development
