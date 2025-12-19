# PLAN-010: Failure and Recovery Testing

## Objective

Validate the CDC pipeline's resilience by testing failure scenarios and verifying correct recovery behavior with observable evidence in logs, metrics, and traces.

## Dependencies

- All previous plans (PLAN-001 through PLAN-009)
- Full stack running and operational

## Test Scenarios

### Scenario 1: Kafka Connect Restart with Database Changes During Downtime

**Purpose:** Verify that CDC events generated while Kafka Connect is down are captured and delivered when it restarts.

**Steps:**
```bash
# 1. Verify initial state
docker compose exec postgres psql -U postgres -c "SELECT COUNT(*) FROM customer;"
docker compose exec postgres psql -U postgres -c "SELECT COUNT(*) FROM customer_materialized;"

# 2. Stop Kafka Connect
docker compose stop kafka-connect

# 3. Make changes while Connect is down
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES
   (gen_random_uuid(), 'downtime-1@example.com', 'active'),
   (gen_random_uuid(), 'downtime-2@example.com', 'active');"

docker compose exec postgres psql -U postgres -c \
  "UPDATE customer SET status = 'inactive' WHERE email LIKE 'alice%';"

# 4. Restart Kafka Connect
docker compose start kafka-connect

# 5. Wait for connector to resume
sleep 30

# 6. Check connector status
curl -s http://localhost:8083/connectors/postgres-cdc-connector/status | jq .

# 7. Verify changes propagated to materialized table
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email LIKE 'downtime%';"

# 8. Verify counts match
docker compose exec postgres psql -U postgres -c "SELECT COUNT(*) FROM customer;"
docker compose exec postgres psql -U postgres -c "SELECT COUNT(*) FROM customer_materialized;"
```

**Expected Results:**
- [ ] Connector resumes from last position (using replication slot)
- [ ] All changes made during downtime are delivered
- [ ] `customer` and `customer_materialized` counts match
- [ ] No data loss or duplication

---

### Scenario 2: Consumer Restart with Backlog of CDC Events

**Purpose:** Verify that the consumer correctly processes pending events after restart and resumes from committed offset.

**Steps:**
```bash
# 1. Stop the Spring Boot consumer (Ctrl+C or kill the process)
# If running via Gradle, use Ctrl+C

# 2. Generate CDC events while consumer is down
for i in {1..20}; do
  docker compose exec postgres psql -U postgres -c \
    "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'backlog-$i@example.com', 'active');"
done

# 3. Check topic lag
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group

# 4. Restart the consumer
./gradlew bootRun

# 5. Watch logs for backlog processing
# Look for rapid succession of "CDC event processed successfully" logs

# 6. Verify all events processed
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group
# LAG should be 0

# 7. Verify data in materialized table
docker compose exec postgres psql -U postgres -c \
  "SELECT COUNT(*) FROM customer_materialized WHERE email LIKE 'backlog%';"
# Should be 20
```

**Expected Results:**
- [ ] Consumer shows lag > 0 when started
- [ ] All backlogged messages are processed
- [ ] Final lag is 0
- [ ] All 20 records appear in `customer_materialized`
- [ ] No duplicates (idempotent processing)

---

### Scenario 3: Kafka Restart and Recovery

**Purpose:** Verify that Kafka recovers and CDC resumes after a Kafka broker restart.

**Steps:**
```bash
# 1. Record current state
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group

# 2. Stop Kafka
docker compose stop kafka

# 3. Verify Connect and Consumer detect Kafka unavailability
# Check logs for connection errors

# 4. Try to insert data (will queue in WAL)
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'kafka-down@example.com', 'active');"

# 5. Restart Kafka
docker compose start kafka

# 6. Wait for recovery
sleep 30

# 7. Check Kafka is healthy
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# 8. Check connector recovered
curl -s http://localhost:8083/connectors/postgres-cdc-connector/status | jq .

# 9. Verify the queued change was captured
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email = 'kafka-down@example.com';"
```

**Expected Results:**
- [ ] Kafka Connect reconnects after Kafka restarts
- [ ] Consumer reconnects after Kafka restarts
- [ ] CDC events generated during downtime are eventually delivered
- [ ] No data loss

---

### Scenario 4: Forced Consumer Processing Error

**Purpose:** Verify error handling and observability when processing fails.

**Steps:**
```bash
# Option A: Inject malformed message directly to Kafka
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic cdc.public.customer \
  --property "parse.key=true" \
  --property "key.separator=:" <<< "bad-key:{invalid json"

# Option B: Temporarily break database connection
docker compose stop postgres

# Make the consumer try to process (it will fail on DB operations)
# Watch error logs

# Restart postgres
docker compose start postgres

# 1. Check consumer logs for error messages
# Look for "Error processing CDC event" with stack trace

# 2. Check Prometheus for error metrics
open http://localhost:9090
# Query: cdc_messages_errors_total

# 3. Check Jaeger for error spans
open http://localhost:16686
# Filter by: error=true or status=ERROR

# 4. Verify consumer continued processing after error
# Insert a valid record and verify it's processed
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES (gen_random_uuid(), 'after-error@example.com', 'active');"

docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email = 'after-error@example.com';"
```

**Expected Results:**
- [ ] Error is logged with stack trace
- [ ] Error includes trace_id for correlation
- [ ] `cdc_messages_errors_total` metric increments
- [ ] Error span appears in Jaeger with exception recorded
- [ ] Consumer continues processing subsequent messages
- [ ] No crash or hang

---

### Scenario 5: Schema Evolution (Add Nullable Column)

**Purpose:** Verify CDC continues working after adding a new column to the source table.

**Steps:**
```bash
# 1. Add a new nullable column
docker compose exec postgres psql -U postgres -c \
  "ALTER TABLE customer ADD COLUMN phone TEXT;"

# 2. Insert a record with the new column
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status, phone) VALUES
   (gen_random_uuid(), 'new-schema@example.com', 'active', '+1-555-0100');"

# 3. Insert a record without the new column
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES
   (gen_random_uuid(), 'old-schema@example.com', 'active');"

# 4. Check consumer logs for both events
# Should process without errors (FAIL_ON_UNKNOWN_PROPERTIES=false)

# 5. Verify data in materialized table
# Note: materialized table doesn't have phone column, so it's ignored
docker compose exec postgres psql -U postgres -c \
  "SELECT * FROM customer_materialized WHERE email LIKE '%schema@example.com';"
```

**Expected Results:**
- [ ] Debezium captures the new column value
- [ ] Consumer ignores the new field (doesn't crash)
- [ ] Existing functionality continues working
- [ ] No connector restart required

---

## Observability Verification Checklist

For each scenario, verify:

### Logs
- [ ] Appropriate log level (INFO for success, ERROR for failures)
- [ ] `trace_id` present in all logs
- [ ] Kafka context (topic, partition, offset) in logs
- [ ] Processing outcome clearly stated

### Metrics (Prometheus)
```promql
# Check these queries after each scenario:

# Messages processed (should increase)
sum(cdc_messages_processed_total)

# Error rate (should be low except Scenario 4)
sum(cdc_messages_errors_total) / sum(cdc_messages_processed_total)

# Processing latency P95
histogram_quantile(0.95, rate(cdc_processing_latency_bucket[5m]))

# Consumer lag (should return to 0)
kafka_consumer_records_lag_max
```

### Traces (Jaeger)
- [ ] Spans present for all processed messages
- [ ] Error spans have exception recorded
- [ ] Span attributes match log context
- [ ] Latency visible in span duration

---

## Test Results Template

```markdown
## Test Run: [DATE]

### Scenario 1: Kafka Connect Restart
- Status: PASS/FAIL
- Notes:
- Evidence: [link to logs/screenshots]

### Scenario 2: Consumer Restart
- Status: PASS/FAIL
- Notes:
- Evidence:

### Scenario 3: Kafka Restart
- Status: PASS/FAIL
- Notes:
- Evidence:

### Scenario 4: Processing Error
- Status: PASS/FAIL
- Notes:
- Evidence:

### Scenario 5: Schema Evolution
- Status: PASS/FAIL
- Notes:
- Evidence:
```

---

## Estimated Complexity

Medium - Tests are manual but systematic; requires careful observation of multiple systems.

## Notes

- Run tests in order; some build on state from previous tests
- Clean slate: `docker compose down -v && docker compose up -d` to reset
- Keep terminal windows open for each service's logs during testing
- Screenshot Jaeger traces and Prometheus graphs as evidence
- Consider automating these as integration tests in the future
