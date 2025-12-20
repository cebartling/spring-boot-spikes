# Troubleshooting Guide

This guide covers common issues and their solutions when running the CDC pipeline.

## Kafka Connect Issues

### Connector Not Starting

```bash
# Check connector logs
docker compose logs kafka-connect

# Verify PostgreSQL replication settings
docker compose exec postgres psql -U postgres -c "SHOW wal_level;"
# Should return: logical
```

**Common causes:**
- PostgreSQL not configured for logical replication
- Kafka not ready when Connect starts
- Invalid connector configuration

### Connector in FAILED State

```bash
# Check connector status
curl -s http://localhost:8083/connectors/postgres-cdc-connector/status | jq

# Get detailed task status
curl -s http://localhost:8083/connectors/postgres-cdc-connector/tasks/0/status | jq

# Restart the connector
curl -X POST http://localhost:8083/connectors/postgres-cdc-connector/restart

# If restart fails, delete and recreate
curl -X DELETE http://localhost:8083/connectors/postgres-cdc-connector
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium/connector-config.json
```

## CDC Event Issues

### No CDC Events

```bash
# Verify connector is running
curl -s http://localhost:8083/connectors/postgres-cdc-connector/status | jq '.connector.state'
# Should return: "RUNNING"

# Check for replication slot
docker compose exec postgres psql -U postgres -c "SELECT * FROM pg_replication_slots;"

# Verify topic was created
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list | grep cdc

# Check if messages are in the topic
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc.public.customer \
  --from-beginning \
  --max-messages 5
```

### Consumer Not Receiving Messages

```bash
# Check if topic exists
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check consumer group lag
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group

# Reset consumer offset if needed (use with caution)
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group cdc-consumer-group \
  --topic cdc.public.customer \
  --reset-offsets --to-earliest --execute
```

## Observability Issues

### No Traces in Jaeger

```bash
# Verify OTel Collector is running
docker compose ps otel-collector

# Check OTel Collector logs
docker compose logs otel-collector

# Verify OTLP endpoint is receiving data
curl -s http://localhost:8888/metrics | grep otel

# Test OTLP connectivity
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  -d '{"resourceSpans":[]}'
```

**Common causes:**
- OTel Collector not running
- Application not configured with correct OTLP endpoint
- Jaeger not receiving data from collector

### No Metrics in Prometheus

```bash
# Verify Prometheus targets are UP
open http://localhost:9090/targets

# Check OTel Collector Prometheus exporter
curl -s http://localhost:8889/metrics | head -50

# Verify application is exporting metrics
curl -s http://localhost:8080/actuator/prometheus 2>/dev/null || echo "App not running"

# Check Prometheus configuration
docker compose exec prometheus cat /etc/prometheus/prometheus.yml
```

### Logs Missing Trace IDs

Ensure the application is:
1. Using the OpenTelemetry Logback MDC integration
2. Creating spans before logging
3. Using `span.makeCurrent()` to set the context

Check `src/main/resources/logback-spring.xml` configuration.

## Infrastructure Issues

### Kafka Not Starting

```bash
# Check Kafka logs
docker compose logs kafka

# Verify Kafka is in KRaft mode
docker compose exec kafka cat /var/lib/kafka/data/__cluster_metadata-0/partition.metadata

# Check disk space
docker system df
```

### PostgreSQL Connection Issues

```bash
# Test PostgreSQL connectivity
docker compose exec postgres psql -U postgres -c "SELECT 1;"

# Check PostgreSQL logs
docker compose logs postgres

# Verify logical replication is enabled
docker compose exec postgres psql -U postgres -c "SHOW wal_level;"
docker compose exec postgres psql -U postgres -c "SHOW max_replication_slots;"
```

### Services Not Healthy

```bash
# Check all service health
docker compose ps

# Restart unhealthy services
docker compose restart <service-name>

# Full reset (warning: destroys data)
docker compose down -v
docker compose up -d
```

## Performance Issues

### High Consumer Lag

```bash
# Check consumer lag
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group

# Monitor lag over time
watch -n 5 'docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group cdc-consumer-group 2>/dev/null | grep cdc'
```

**Solutions:**
- Increase consumer concurrency (if partitions allow)
- Check for slow database operations
- Review processing logic for bottlenecks

### Slow Trace Processing

Check Jaeger for long-running spans:
1. Open http://localhost:16686
2. Search for service `cdc-consumer`
3. Sort by duration to find slow traces
4. Examine span details for bottlenecks

## Useful Diagnostic Commands

```bash
# Full system status
docker compose ps

# Service logs (last 100 lines)
docker compose logs --tail=100 <service>

# Follow logs in real-time
docker compose logs -f <service>

# Resource usage
docker stats

# Network connectivity between containers
docker compose exec kafka ping postgres
docker compose exec kafka-connect curl -s http://kafka:9092

# Kafka topic details
docker compose exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic cdc.public.customer
```
