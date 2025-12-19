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
    image: quay.io/debezium/postgres:latest
    # Pre-configured for logical replication (wal_level=logical)
    # Includes pgoutput plugin for Debezium
    # Expose port 5432
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: postgres

  kafka:
    image: apache/kafka:3.7.0
    # KRaft mode configuration (no ZooKeeper)
    # Single-node cluster
    # Expose ports 9092 (internal), 29092 (external)
```

### PostgreSQL Configuration

The `quay.io/debezium/postgres:latest` image comes pre-configured with:
- `wal_level = logical`
- `max_replication_slots = 10`
- `max_wal_senders = 10`

No custom configuration files are required.

## Commands to Run

```bash
# Start infrastructure
docker compose up -d postgres kafka

# Verify PostgreSQL is ready
docker compose exec postgres pg_isready

# Verify Kafka is ready (KRaft mode)
docker compose exec kafka /opt/kafka/bin/kafka-metadata.sh \
  --snapshot /tmp/kraft-combined-logs/__cluster_metadata-0/00000000000000000000.log \
  --command "print"

# Check PostgreSQL logical replication is enabled
docker compose exec postgres psql -U postgres -c "SHOW wal_level;"
# Expected: logical

# Test Kafka by creating and listing a topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --topic test-topic --partitions 1 --replication-factor 1

docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

## Acceptance Criteria

1. [ ] `docker compose up -d postgres kafka` starts both services without errors
2. [ ] PostgreSQL accepts connections on port 5432
3. [ ] `SHOW wal_level;` returns `logical`
4. [ ] Kafka broker is reachable on port 9092 (internal) and 29092 (external)
5. [ ] Can create and list Kafka topics
6. [ ] Services restart cleanly after `docker compose down && docker compose up -d`

## Estimated Complexity

Low - Standard Docker Compose setup with well-documented configurations.

## Notes

- Use official Apache Kafka image with built-in KRaft support
- Use Debezium PostgreSQL image (`quay.io/debezium/postgres:latest`) which comes pre-configured for CDC with logical replication enabled and pgoutput plugin installed
- Keep volumes for data persistence during development
