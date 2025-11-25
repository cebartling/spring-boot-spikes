# Debugging Guide

This guide covers debugging techniques and tools for the CQRS Spike application.

## Overview

Debugging options available:

| Method | Use Case | Port |
|--------|----------|------|
| Remote Debug | IDE debugging | 5005 |
| Log Analysis | Runtime behavior | N/A |
| Database Inspection | Data issues | 5432 |
| Vault Inspection | Secret issues | 8200 |

## Remote Debugging

### Starting with Debug Port

```bash
# Start with debug enabled
./gradlew bootRun --debug-jvm
```

This opens port 5005 for remote debugging connections.

### IDE Configuration

#### IntelliJ IDEA

1. **Create Remote JVM Debug Configuration:**
   - Run > Edit Configurations
   - Add New > Remote JVM Debug
   - Name: "CQRS Remote Debug"
   - Host: localhost
   - Port: 5005
   - Use module classpath: cqrs-spike

2. **Start debugging:**
   - Start application with `--debug-jvm`
   - Click Debug button for your configuration
   - Set breakpoints and debug

#### Visual Studio Code

1. **Create launch configuration** in `.vscode/launch.json`:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Attach to CQRS App",
            "request": "attach",
            "hostName": "localhost",
            "port": 5005
        }
    ]
}
```

2. **Start debugging:**
   - Start application with `--debug-jvm`
   - Press F5 or click Debug
   - Set breakpoints and debug

### Debugging Tips

**Effective Breakpoints:**
- Set in service layer for business logic
- Set in repository layer for data access
- Use conditional breakpoints for specific scenarios

```kotlin
// Good breakpoint locations
@Service
class ProductService(private val repo: ProductRepository) {
    fun findById(id: UUID): Mono<Product> {
        // Breakpoint here to inspect incoming ID
        return repo.findById(id)
            // Breakpoint here to inspect result
            .doOnNext { product -> logger.debug("Found: $product") }
    }
}
```

**Conditional Breakpoints:**
- Right-click breakpoint in IDE
- Add condition: `id.toString() == "specific-uuid"`

**Logpoints:**
- Non-breaking debug output
- Right-click breakpoint > Log Message
- Add: `"Finding product: ${id}"`

## Log Analysis

### Application Logs

```bash
# View application logs
./scripts/logs.sh app

# Follow logs in real-time
./scripts/logs.sh app -f

# Filter errors only
./scripts/logs.sh errors

# Filter warnings
./scripts/logs.sh warnings

# Last 100 lines
./scripts/logs.sh tail
```

### Multi-Service Dashboard

```bash
# Open tmux dashboard (requires tmux)
./scripts/logs-dashboard.sh
```

This shows:
- Application logs
- Vault logs
- PostgreSQL logs

### Log Levels

Configure in `application-local.yml`:

```yaml
logging:
  level:
    root: INFO
    com.example.cqrs: DEBUG
    org.springframework.r2dbc: DEBUG
    io.r2dbc.postgresql: DEBUG
```

Restart application for changes to take effect.

### Structured Log Analysis

```bash
# Parse JSON logs with jq
./scripts/logs.sh app | jq 'select(.level == "ERROR")'

# Extract specific fields
./scripts/logs.sh app | jq '{timestamp, level, message}'
```

## Database Debugging

### Inspecting Data

```bash
# Open psql shell
make shell-postgres

# View recent events
SELECT * FROM event_store.domain_event
ORDER BY occurred_at DESC
LIMIT 10;

# Check event data (JSONB)
SELECT event_id, event_type, event_data
FROM event_store.domain_event
WHERE event_data->>'productId' = 'your-product-id';
```

### Query Performance

```bash
# Analyze slow queries
./scripts/db-query.sh "EXPLAIN ANALYZE SELECT * FROM event_store.domain_event WHERE stream_id = 'test'"
```

### Connection Monitoring

```sql
-- Active connections
SELECT pid, usename, application_name, client_addr, state, query
FROM pg_stat_activity
WHERE datname = 'cqrs_db';

-- Connection counts
SELECT state, count(*)
FROM pg_stat_activity
WHERE datname = 'cqrs_db'
GROUP BY state;
```

### Lock Investigation

```sql
-- Find locks
SELECT
    pg_locks.pid,
    pg_locks.mode,
    pg_class.relname as table_name
FROM pg_locks
JOIN pg_class ON pg_locks.relation = pg_class.oid
WHERE pg_locks.granted = true;
```

## Vault Debugging

### Checking Vault Status

```bash
# Health check
curl http://localhost:8200/v1/sys/health

# Detailed status
curl -H "X-Vault-Token: dev-root-token" \
     http://localhost:8200/v1/sys/seal-status
```

### Verifying Secrets

```bash
# Get database secrets
./scripts/vault-get.sh secret/cqrs-spike/database

# List all secrets
./scripts/vault-get.sh list

# In Vault shell
make shell-vault
vault kv get secret/cqrs-spike/database
```

### Vault Logs

```bash
make logs-vault
```

## Reactive Debugging

### StepVerifier for Testing

```kotlin
@Test
fun `debug reactive flow`() {
    val result = productService.findById(testId)

    StepVerifier.create(result)
        .expectSubscription()
        .thenConsumeWhile({ true }) { item ->
            println("Received: $item")  // Debug output
        }
        .verifyComplete()
}
```

### Adding Debug Operators

```kotlin
fun findById(id: UUID): Mono<Product> {
    return repo.findById(id)
        .log()  // Logs all signals
        .doOnSubscribe { logger.debug("Subscribed for $id") }
        .doOnNext { logger.debug("Found: $it") }
        .doOnError { logger.error("Error: ${it.message}") }
        .doFinally { logger.debug("Completed") }
}
```

### Debugging Operators

| Operator | Purpose |
|----------|---------|
| `.log()` | Log all reactive signals |
| `.doOnNext()` | Inspect each emitted item |
| `.doOnError()` | Inspect errors |
| `.doOnSubscribe()` | Inspect subscription |
| `.doFinally()` | Cleanup/logging on completion |
| `.checkpoint("name")` | Add checkpoint for stack traces |

## Network Debugging

### Testing Connectivity

```bash
# From host to services
curl http://localhost:8200/v1/sys/health
curl http://localhost:8080/actuator/health

# Between containers
docker exec cqrs-app curl http://vault:8200/v1/sys/health
docker exec cqrs-app nc -zv postgres 5432
```

### Inspecting Docker Network

```bash
# List networks
docker network ls

# Inspect CQRS network
docker network inspect cqrs-network

# Check container IPs
docker inspect cqrs-postgres --format '{{.NetworkSettings.Networks.cqrs-network.IPAddress}}'
```

## Performance Debugging

### Resource Monitoring

```bash
# Container resource usage
./scripts/monitor-resources.sh

# Docker stats
docker stats --no-stream
```

### Startup Time

```bash
./scripts/measure-startup.sh
```

### JVM Debugging

```bash
# Add JVM options for debugging
JAVA_OPTS="-XX:+PrintGCDetails -XX:+PrintGCDateStamps" ./gradlew bootRun
```

## Common Debug Scenarios

### Application Won't Start

```bash
# Check dependencies
make health

# View startup logs
./gradlew bootRun 2>&1 | tee startup.log

# Check for port conflicts
lsof -i :8080
```

### Requests Timing Out

```bash
# Check service health
make health

# View logs for errors
./scripts/logs.sh errors

# Check database connections
./scripts/db-query.sh "SELECT count(*) FROM pg_stat_activity"
```

### Data Not Persisting

```bash
# Check transaction commits
./scripts/logs.sh app | grep -i "commit\|rollback"

# Verify data in database
./scripts/db-query.sh "SELECT * FROM event_store.domain_event ORDER BY occurred_at DESC LIMIT 5"
```

### Secret Access Failures

```bash
# Verify Vault is running
curl http://localhost:8200/v1/sys/health

# Check secrets exist
./scripts/vault-get.sh secret/cqrs-spike/database

# View application logs for Vault errors
./scripts/logs.sh app | grep -i vault
```

## Debug Configuration

### Environment Variables

```bash
# Enable debug mode
DEBUG=true ./gradlew bootRun

# Set log level
LOGGING_LEVEL_ROOT=DEBUG ./gradlew bootRun
```

### Application Properties

In `application-local.yml`:

```yaml
# Enable debug endpoints
management:
  endpoints:
    web:
      exposure:
        include: "*"

# Verbose SQL logging
logging:
  level:
    io.r2dbc.postgresql.QUERY: DEBUG
    io.r2dbc.postgresql.PARAM: DEBUG
```

## See Also

- [Daily Workflow](daily-workflow.md) - Development patterns
- [Troubleshooting](../troubleshooting/common-issues.md) - Problem solving
- [Architecture](../architecture/overview.md) - System understanding
