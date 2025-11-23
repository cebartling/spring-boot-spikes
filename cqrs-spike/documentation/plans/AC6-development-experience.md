# Implementation Plan: AC6 - Development Experience

**Feature:** [Local Development Services Infrastructure](../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC6 - Development Experience

## Overview

Create comprehensive documentation, configuration, and tooling to provide developers with an exceptional local development experience, including clear connection details, easy access to logs, fast startup times, and reasonable resource consumption.

## Prerequisites

- AC5 (Infrastructure Orchestration) completed
- All services configured and running
- Scripts and Makefile created

## Technical Implementation

### 1. Connection Details Documentation

**Quick Reference Guide (README.md section):**
```markdown
## Service Connection Details

### Local Development URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Vault UI | http://localhost:8200/ui | Token: `dev-root-token` |
| Vault API | http://localhost:8200 | Token: `dev-root-token` |
| PostgreSQL | localhost:5432 | User: `cqrs_user`, Password: `local_dev_password`, DB: `cqrs_db` |
| Application | http://localhost:8080 | N/A |
| Health Check | http://localhost:8080/actuator/health | N/A |
| Debug Port | localhost:5005 | JDWP |

### Internal Service URLs (Docker Network)

| Service | Internal URL | Used By |
|---------|--------------|---------|
| Vault | http://vault:8200 | Application |
| PostgreSQL | postgres:5432 | Application |

### Database Connection Strings

**JDBC:**
```
jdbc:postgresql://localhost:5432/cqrs_db
```

**psql CLI:**
```bash
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db
```

**Docker exec:**
```bash
docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db
```

### Vault Access

**CLI:**
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
vault status
```

**UI:**
Navigate to http://localhost:8200/ui and login with token: `dev-root-token`
```

### 2. Environment Configuration Files

**IDE-specific configurations:**

**.vscode/settings.json:**
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "spring-boot.ls.checkJVM": false,
  "java.debug.settings.hotCodeReplace": "auto",
  "files.exclude": {
    "**/.git": true,
    "**/target": true,
    "**/.idea": true
  },
  "java.debug.settings.vmArgs": "-Dspring.profiles.active=local",
  "terminal.integrated.env.osx": {
    "VAULT_ADDR": "http://localhost:8200",
    "VAULT_TOKEN": "dev-root-token"
  },
  "terminal.integrated.env.linux": {
    "VAULT_ADDR": "http://localhost:8200",
    "VAULT_TOKEN": "dev-root-token"
  }
}
```

**.vscode/launch.json:**
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug CQRS App",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005,
      "projectName": "cqrs-spike"
    },
    {
      "type": "java",
      "name": "Run CQRS App (Local)",
      "request": "launch",
      "mainClass": "com.example.cqrs.CqrsApplication",
      "projectName": "cqrs-spike",
      "env": {
        "SPRING_PROFILES_ACTIVE": "local",
        "VAULT_URI": "http://localhost:8200",
        "VAULT_TOKEN": "dev-root-token"
      },
      "vmArgs": "-Xmx1024m"
    }
  ]
}
```

**IntelliJ Run Configurations (.run/Local.run.xml):**
```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="CQRS Application (Local)" type="SpringBootApplicationConfigurationType">
    <module name="cqrs-spike" />
    <option name="SPRING_BOOT_MAIN_CLASS" value="com.example.cqrs.CqrsApplication" />
    <option name="ACTIVE_PROFILES" value="local" />
    <option name="ALTERNATIVE_JRE_PATH" />
    <option name="VM_PARAMETERS" value="-Xmx1024m -Dspring.profiles.active=local" />
    <envs>
      <env name="VAULT_URI" value="http://localhost:8200" />
      <env name="VAULT_TOKEN" value="dev-root-token" />
    </envs>
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
```

**application-local.yml (development-specific overrides):**
```yaml
# application-local.yml
spring:
  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true

  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

logging:
  level:
    root: INFO
    com.example.cqrs: DEBUG
    org.springframework.vault: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

### 3. Easy Log Access

**Log viewing script with filtering:**
```bash
#!/bin/bash
# scripts/logs.sh

SERVICE="${1:-all}"
FOLLOW="${2:--f}"

case "$SERVICE" in
  all)
    docker-compose logs $FOLLOW
    ;;
  vault)
    docker-compose logs $FOLLOW vault
    ;;
  postgres|db)
    docker-compose logs $FOLLOW postgres
    ;;
  app|application)
    docker-compose logs $FOLLOW app
    ;;
  errors)
    docker-compose logs $FOLLOW | grep -i "error\|exception\|fatal"
    ;;
  warnings)
    docker-compose logs $FOLLOW | grep -i "warn\|warning"
    ;;
  tail)
    docker-compose logs --tail=100 app
    ;;
  *)
    echo "Usage: ./scripts/logs.sh [service] [options]"
    echo ""
    echo "Services:"
    echo "  all         - All services (default)"
    echo "  vault       - Vault logs"
    echo "  postgres    - PostgreSQL logs"
    echo "  app         - Application logs"
    echo "  errors      - Filter for errors only"
    echo "  warnings    - Filter for warnings only"
    echo "  tail        - Last 100 lines of app logs"
    echo ""
    echo "Options:"
    echo "  -f          - Follow logs (default)"
    echo "  --tail=N    - Show last N lines"
    echo ""
    echo "Examples:"
    echo "  ./scripts/logs.sh app"
    echo "  ./scripts/logs.sh errors"
    echo "  ./scripts/logs.sh app --tail=50"
    exit 1
    ;;
esac
```

**Log streaming dashboard script:**
```bash
#!/bin/bash
# scripts/logs-dashboard.sh

# Requires: tmux

if ! command -v tmux &> /dev/null; then
    echo "tmux is required. Install with: brew install tmux (macOS) or apt install tmux (Linux)"
    exit 1
fi

SESSION="cqrs-logs"

# Create new session
tmux new-session -d -s $SESSION

# Split window into panes
tmux split-window -v -t $SESSION:0
tmux split-window -h -t $SESSION:0

# Arrange panes
tmux select-layout -t $SESSION:0 tiled

# Send commands to panes
tmux send-keys -t $SESSION:0.0 'docker-compose logs -f vault' C-m
tmux send-keys -t $SESSION:0.1 'docker-compose logs -f postgres' C-m
tmux send-keys -t $SESSION:0.2 'docker-compose logs -f app' C-m

# Attach to session
tmux attach-session -t $SESSION
```

### 4. Performance Optimization

**Fast Startup Configuration:**

**Docker layer caching optimization (Dockerfile):**
```dockerfile
# Multi-stage build for faster rebuilds
FROM gradle:8-jdk21-alpine AS build

WORKDIR /app

# Copy only Gradle files first (for dependency caching)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

# Copy source and build
COPY src ./src
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app

USER appuser

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080 5005

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Gradle build optimization (gradle.properties):**
```properties
# gradle.properties
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=true
org.gradle.configureondemand=true
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
```

**Spring Boot startup optimization:**
```yaml
# application.yml
spring:
  main:
    lazy-initialization: false
    register-shutdown-hook: true

  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
```

**Startup time monitoring:**
```bash
#!/bin/bash
# scripts/measure-startup.sh

echo "Measuring infrastructure startup time..."

# Clean start
docker-compose down -v

START_TIME=$(date +%s)

# Start infrastructure
./scripts/start-infrastructure.sh > /dev/null 2>&1

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo "Infrastructure startup time: ${DURATION}s"

if [ $DURATION -lt 60 ]; then
    echo "✓ Meets requirement (< 60s)"
else
    echo "✗ Exceeds requirement (>= 60s)"
    exit 1
fi
```

### 5. Resource Consumption Monitoring

**Resource monitoring script:**
```bash
#!/bin/bash
# scripts/monitor-resources.sh

echo "========================================="
echo "Resource Consumption Monitoring"
echo "========================================="
echo ""

# Docker stats
echo "Container Resource Usage:"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" \
  cqrs-vault cqrs-postgres cqrs-app 2>/dev/null || echo "No containers running"

echo ""
echo "Total Docker Resources:"
docker stats --no-stream --format "CPU: {{.CPUPerc}}, Memory: {{.MemUsage}}" | \
  awk '{cpu+=$2; mem+=$4} END {printf "Total CPU: %.2f%%, Total Memory: %s\n", cpu, mem}' 2>/dev/null

echo ""
echo "Volume Usage:"
docker volume ls --format "table {{.Name}}\t{{.Driver}}\t{{.Mountpoint}}"

echo ""
echo "Disk Usage:"
docker system df
```

**Resource limits configuration:**
```yaml
# docker-compose.override.yml (for resource-constrained environments)
version: '3.9'

services:
  vault:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M
        reservations:
          cpus: '0.25'
          memory: 128M

  postgres:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M

  app:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 1024M
        reservations:
          cpus: '1.0'
          memory: 512M
    environment:
      JAVA_TOOL_OPTIONS: >-
        -Xms256m
        -Xmx768m
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
        -XX:+UseStringDeduplication
```

### 6. Developer Utilities

**Database query helper:**
```bash
#!/bin/bash
# scripts/db-query.sh

if [ -z "$1" ]; then
    echo "Usage: ./scripts/db-query.sh <query>"
    echo "Example: ./scripts/db-query.sh \"SELECT * FROM event_store.event_stream LIMIT 10\""
    exit 1
fi

docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db -c "$1"
```

**Vault secret helper:**
```bash
#!/bin/bash
# scripts/vault-get.sh

if [ -z "$1" ]; then
    echo "Usage: ./scripts/vault-get.sh <path>"
    echo "Example: ./scripts/vault-get.sh secret/cqrs-spike/database"
    exit 1
fi

docker exec -e VAULT_ADDR=http://localhost:8200 \
            -e VAULT_TOKEN=dev-root-token \
            cqrs-vault vault kv get "$1"
```

**Service reset helper:**
```bash
#!/bin/bash
# scripts/reset-service.sh

SERVICE="$1"

if [ -z "$SERVICE" ]; then
    echo "Usage: ./scripts/reset-service.sh <service>"
    echo "Services: vault, postgres, app, all"
    exit 1
fi

case "$SERVICE" in
  vault)
    docker-compose restart vault
    sleep 10
    docker-compose up vault-init
    ;;
  postgres)
    echo "WARNING: This will delete all database data!"
    read -p "Continue? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose stop postgres
        docker volume rm cqrs-postgres-data
        docker-compose up -d postgres
    fi
    ;;
  app)
    docker-compose restart app
    ;;
  all)
    docker-compose restart
    ;;
  *)
    echo "Unknown service: $SERVICE"
    exit 1
    ;;
esac
```

### 7. Documentation Hub

**Developer Portal (docs/README.md):**
```markdown
# CQRS Spike - Developer Documentation

## Quick Start

1. **Prerequisites**
   - Docker Desktop
   - Java 21
   - Maven 3.9+

2. **First Time Setup**
   ```bash
   cp .env.example .env
   make start
   ```

3. **Daily Development**
   ```bash
   make start-app
   ```

## Common Tasks

### Infrastructure Management
- `make start` - Start all infrastructure
- `make stop` - Stop all infrastructure
- `make restart` - Restart everything
- `make clean` - Stop and remove volumes
- `make health` - Check service health
- `make ps` - Show service status

### Viewing Logs
- `make logs` - All logs
- `make logs-vault` - Vault logs
- `make logs-postgres` - Database logs
- `make logs-app` - Application logs
- `./scripts/logs.sh errors` - Filter errors only

### Database Operations
- `make shell-postgres` - Open psql
- `./scripts/db-query.sh "SELECT * FROM ..."` - Run query
- See [Database Guide](database.md)

### Application Development
- `make build` - Build application
- `make rebuild` - Rebuild and restart
- `make shell-app` - Open app shell
- Debug port: 5005

## Service URLs

See [Connection Details](#connection-details) section in main README.

## Troubleshooting

See [Troubleshooting Guide](troubleshooting.md)

## Architecture

See [Architecture Documentation](architecture.md)
```

## Testing Strategy

### 1. Documentation Accuracy Tests

```bash
#!/bin/bash
# scripts/test-documentation.sh

echo "Testing documentation accuracy..."

# Test connection details
errors=0

# Test Vault URL
if ! curl -sf http://localhost:8200/v1/sys/health > /dev/null; then
    echo "✗ Vault URL incorrect or service down"
    ((errors++))
else
    echo "✓ Vault URL correct"
fi

# Test PostgreSQL connection
if ! docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -c "SELECT 1" > /dev/null 2>&1; then
    echo "✗ PostgreSQL credentials incorrect"
    ((errors++))
else
    echo "✓ PostgreSQL credentials correct"
fi

# Test Application URL
if ! curl -sf http://localhost:8080/actuator/health > /dev/null; then
    echo "✗ Application URL incorrect or service down"
    ((errors++))
else
    echo "✓ Application URL correct"
fi

if [ $errors -eq 0 ]; then
    echo "✓ All connection details verified"
    exit 0
else
    echo "✗ $errors connection detail(s) failed"
    exit 1
fi
```

### 2. Performance Tests

```bash
#!/bin/bash
# scripts/test-performance.sh

echo "Testing performance requirements..."

# Test startup time
./scripts/measure-startup.sh

# Test resource consumption
echo ""
echo "Current resource usage:"
./scripts/monitor-resources.sh

# Check if under reasonable limits
TOTAL_MEM=$(docker stats --no-stream --format "{{.MemUsage}}" | awk '{sum+=$1} END {print sum}')

echo ""
if [ "$TOTAL_MEM" -lt 2048 ]; then
    echo "✓ Memory usage reasonable (< 2GB)"
else
    echo "⚠ Memory usage high (>= 2GB)"
fi
```

## Rollout Steps

1. **Create documentation directory structure**
   ```bash
   mkdir -p docs/{guides,architecture,troubleshooting}
   ```

2. **Write connection details documentation**
   - Add to README.md
   - Create quick reference card

3. **Create IDE configuration files**
   - VSCode settings
   - IntelliJ run configurations
   - Create .editorconfig

4. **Write log access scripts**
   - Basic log viewer
   - Log dashboard (optional)
   - Log filtering utilities

5. **Optimize startup performance**
   - Configure Docker layer caching
   - Optimize Maven builds
   - Tune Spring Boot settings

6. **Configure resource limits**
   - Set Docker resource limits
   - Create monitoring scripts
   - Document expected usage

7. **Create developer utilities**
   - Database helpers
   - Vault helpers
   - Service reset scripts

8. **Write comprehensive documentation**
   - Quick start guide
   - Common tasks
   - Troubleshooting guide

9. **Test everything**
   - Verify connection details
   - Measure startup time
   - Monitor resources

10. **Gather feedback**
    - Have team test setup
    - Iterate on documentation
    - Improve scripts

## Verification Checklist

- [ ] Connection details documented and accurate
- [ ] Environment configuration files created for popular IDEs
- [ ] Log viewing is easy and intuitive
- [ ] Logs accessible through multiple methods
- [ ] Infrastructure starts in under 60 seconds
- [ ] Total resource usage under 2GB RAM
- [ ] CPU usage reasonable for development
- [ ] Quick start guide complete
- [ ] Common tasks documented
- [ ] Helper scripts created and tested
- [ ] Troubleshooting guide comprehensive
- [ ] Team has successfully used setup

## Troubleshooting Guide

### Issue: Startup time exceeds 60 seconds
**Solutions:**
- Reduce health check intervals
- Enable Docker BuildKit
- Increase Docker resources
- Use SSD for Docker storage

### Issue: High memory consumption
**Solutions:**
- Apply resource limits in docker-compose.override.yml
- Reduce JVM heap size
- Use smaller base images (Alpine)
- Disable unnecessary services

### Issue: Logs difficult to read
**Solutions:**
- Use structured logging
- Install tmux for log dashboard
- Use `jq` for JSON log parsing
- Configure log levels appropriately

### Issue: Can't connect to services
**Solutions:**
- Verify services are running: `docker-compose ps`
- Check health status: `make health`
- Review connection details in README
- Check firewall settings

## Dependencies

- **Blocks:** AC7 (Data Seeding and Reset), AC8 (Documentation)
- **Blocked By:** AC5 (Infrastructure Orchestration)
- **Related:** All ACs
