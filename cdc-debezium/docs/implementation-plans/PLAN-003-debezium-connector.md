# PLAN-003: Debezium Connector Configuration

## Objective

Configure Kafka Connect with the Debezium PostgreSQL connector to capture CDC events and stream them to Kafka topics.

## Dependencies

- PLAN-001: Docker Compose infrastructure (Kafka running)
- PLAN-002: PostgreSQL schema (customer table and publication)

## Changes

### Files to Create

| File | Purpose |
|------|---------|
| `docker/debezium/connector-config.json` | Debezium PostgreSQL connector configuration |

### Docker Compose Addition

Add Kafka Connect service with Debezium:

```yaml
kafka-connect:
  image: debezium/connect:2.5
  depends_on:
    - kafka
    - postgres
  ports:
    - "8083:8083"
  environment:
    BOOTSTRAP_SERVERS: kafka:9092
    GROUP_ID: cdc-connect-cluster
    CONFIG_STORAGE_TOPIC: cdc-connect-configs
    OFFSET_STORAGE_TOPIC: cdc-connect-offsets
    STATUS_STORAGE_TOPIC: cdc-connect-status
    KEY_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    VALUE_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    KEY_CONVERTER_SCHEMAS_ENABLE: "false"
    VALUE_CONVERTER_SCHEMAS_ENABLE: "false"
```

### Connector Configuration (connector-config.json)

```json
{
  "name": "postgres-cdc-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "postgres",
    "topic.prefix": "cdc",
    "table.include.list": "public.customer",
    "publication.name": "cdc_publication",
    "slot.name": "cdc_slot",
    "plugin.name": "pgoutput",
    "publication.autocreate.mode": "filtered",

    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "rewrite",
    "transforms.unwrap.add.fields": "op,source.ts_ms",

    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",

    "snapshot.mode": "initial",
    "decimal.handling.mode": "string",
    "time.precision.mode": "adaptive_time_microseconds"
  }
}
```

### Key Configuration Decisions

| Setting | Value | Rationale |
|---------|-------|-----------|
| `topic.prefix` | `cdc` | Topic naming: `cdc.public.customer` |
| `plugin.name` | `pgoutput` | Native PostgreSQL logical decoding |
| `publication.name` | `cdc_publication` | Uses publication from PLAN-002 |
| `slot.name` | `cdc_slot` | Stable replication slot name |
| `transforms.unwrap.delete.handling.mode` | `rewrite` | Adds `__deleted` field to deletes |
| `transforms.unwrap.drop.tombstones` | `false` | Preserves Kafka tombstones |
| `snapshot.mode` | `initial` | Captures existing rows on first run |
| `schemas.enable` | `false` | JSON without schema wrapper |

## Commands to Run

```bash
# Start Kafka Connect
docker compose up -d kafka-connect

# Wait for Kafka Connect to be ready (check REST API)
curl -s http://localhost:8083/ | jq .

# List available connector plugins
curl -s http://localhost:8083/connector-plugins | jq '.[].class'

# Deploy the Debezium connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium/connector-config.json

# Check connector status
curl -s http://localhost:8083/connectors/postgres-cdc-connector/status | jq .

# Verify topic was created
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list | grep cdc

# Consume messages from CDC topic (see snapshot data)
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic cdc.public.customer \
  --from-beginning \
  --max-messages 5

# Test: Insert a new customer and verify CDC event
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'new@example.com', 'active');"

# Consume the new message
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic cdc.public.customer \
  --from-beginning \
  --timeout-ms 5000

# Verify replication slot exists
docker compose exec postgres psql -U postgres -c \
  "SELECT slot_name, plugin, active FROM pg_replication_slots;"
```

## Acceptance Criteria

1. [ ] Kafka Connect starts and REST API responds on port 8083
2. [ ] Debezium PostgreSQL connector plugin is available
3. [ ] Connector deploys without errors (`RUNNING` state)
4. [ ] Topic `cdc.public.customer` is created
5. [ ] Initial snapshot messages appear in topic (seed data from PLAN-002)
6. [ ] New INSERT produces CDC event in topic
7. [ ] UPDATE produces CDC event with new values
8. [ ] DELETE produces CDC event with `__deleted: true` field
9. [ ] Kafka tombstone (null value) follows delete event
10. [ ] Replication slot `cdc_slot` exists in PostgreSQL

## Expected Message Format

### INSERT/UPDATE (after unwrap transform)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "alice@example.com",
  "status": "active",
  "updated_at": "2024-01-15T10:30:00.000000Z",
  "__op": "c",
  "__source_ts_ms": 1705312200000
}
```

### DELETE (rewritten)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "alice@example.com",
  "status": "active",
  "updated_at": "2024-01-15T10:30:00.000000Z",
  "__deleted": "true",
  "__op": "d",
  "__source_ts_ms": 1705312200000
}
```

## Estimated Complexity

Medium - Debezium configuration has many options; SMT configuration requires careful attention.

## Notes

- The `ExtractNewRecordState` SMT simplifies downstream consumption by flattening the envelope
- `publication.autocreate.mode=filtered` uses the existing publication from PLAN-002
- Connector can be deleted and recreated: `curl -X DELETE http://localhost:8083/connectors/postgres-cdc-connector`
- If snapshot fails, delete the replication slot: `SELECT pg_drop_replication_slot('cdc_slot');`
