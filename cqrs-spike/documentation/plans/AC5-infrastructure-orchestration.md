# Implementation Plan: AC5 - Infrastructure Orchestration

**Feature:** [Local Development Services Infrastructure](../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC5 - Infrastructure Orchestration

## Overview

Orchestrate all infrastructure services using Docker Compose to ensure proper startup order, service health validation, inter-service networking, and simple single-command management of the entire development environment.

## Prerequisites

- Docker Desktop or Docker Engine installed
- Docker Compose v2.x
- All service configurations completed (Vault, PostgreSQL)

## Technical Implementation

### 1. Complete Docker Compose Configuration

```yaml
# docker-compose.yml
version: '3.9'

services:
  # Secrets Management
  vault:
    image: hashicorp/vault:1.15
    container_name: cqrs-vault
    hostname: vault
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_ROOT_TOKEN:-dev-root-token}
      VAULT_DEV_LISTEN_ADDRESS: "0.0.0.0:8200"
      VAULT_ADDR: "http://0.0.0.0:8200"
    cap_add:
      - IPC_LOCK
    volumes:
      - vault-data:/vault/file
      - ./infrastructure/vault/config:/vault/config:ro
      - ./infrastructure/vault/scripts:/vault/scripts:ro
      - ./infrastructure/vault/policies:/vault/policies:ro
    command: server -dev -dev-root-token-id=${VAULT_ROOT_TOKEN:-dev-root-token}
    healthcheck:
      test: ["CMD", "vault", "status"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 10s
    networks:
      - cqrs-network
    restart: unless-stopped
    labels:
      com.example.service: "vault"
      com.example.description: "Secrets management service"

  # Database
  postgres:
    image: postgres:16-alpine
    container_name: cqrs-postgres
    hostname: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-cqrs_db}
      POSTGRES_USER: ${POSTGRES_USER:-cqrs_user}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-local_dev_password}
      PGDATA: /var/lib/postgresql/data/pgdata
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init:/docker-entrypoint-initdb.d:ro
      - ./infrastructure/postgres/config/postgresql.conf:/etc/postgresql/postgresql.conf:ro
    command: postgres -c config_file=/etc/postgresql/postgresql.conf
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-cqrs_user} -d ${POSTGRES_DB:-cqrs_db}"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 15s
    networks:
      - cqrs-network
    depends_on:
      vault:
        condition: service_healthy
    restart: unless-stopped
    labels:
      com.example.service: "postgres"
      com.example.description: "PostgreSQL database"

  # Vault Initialization (runs once, exits)
  vault-init:
    image: hashicorp/vault:1.15
    container_name: cqrs-vault-init
    environment:
      VAULT_ADDR: "http://vault:8200"
      VAULT_TOKEN: ${VAULT_ROOT_TOKEN:-dev-root-token}
    volumes:
      - ./infrastructure/vault/scripts:/scripts:ro
    command: sh /scripts/init-secrets.sh
    networks:
      - cqrs-network
    depends_on:
      vault:
        condition: service_healthy
      postgres:
        condition: service_healthy
    restart: "no"
    labels:
      com.example.service: "vault-init"
      com.example.description: "Vault initialization"

  # Spring Boot Application
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: cqrs-app
    hostname: app
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}
      VAULT_URI: http://vault:8200
      VAULT_TOKEN: ${VAULT_ROOT_TOKEN:-dev-root-token}
      JAVA_TOOL_OPTIONS: >-
        -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
        -Xms512m
        -Xmx1024m
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
    volumes:
      - ./target:/app/target
      - ~/.m2:/root/.m2:ro
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 60s
    networks:
      - cqrs-network
    depends_on:
      vault-init:
        condition: service_completed_successfully
      postgres:
        condition: service_healthy
    restart: unless-stopped
    labels:
      com.example.service: "app"
      com.example.description: "Spring Boot CQRS application"

volumes:
  vault-data:
    name: cqrs-vault-data
    driver: local

  postgres-data:
    name: cqrs-postgres-data
    driver: local

networks:
  cqrs-network:
    name: cqrs-network
    driver: bridge
    ipam:
      driver: default
      config:
        - subnet: 172.28.0.0/16
```

### 2. Environment Configuration

**.env file:**
```bash
# .env
# Infrastructure environment variables

# Application
SPRING_PROFILES_ACTIVE=local

# Vault
VAULT_ROOT_TOKEN=dev-root-token

# PostgreSQL
POSTGRES_DB=cqrs_db
POSTGRES_USER=cqrs_user
POSTGRES_PASSWORD=local_dev_password

# Docker
COMPOSE_PROJECT_NAME=cqrs-spike
```

**.env.example (for documentation):**
```bash
# .env.example
# Copy this file to .env and adjust values as needed

# Application
SPRING_PROFILES_ACTIVE=local

# Vault
VAULT_ROOT_TOKEN=dev-root-token

# PostgreSQL
POSTGRES_DB=cqrs_db
POSTGRES_USER=cqrs_user
POSTGRES_PASSWORD=local_dev_password

# Docker
COMPOSE_PROJECT_NAME=cqrs-spike
```

**Add to .gitignore:**
```
.env
```

### 3. Startup Scripts

**Main startup script:**
```bash
#!/bin/bash
# scripts/start-infrastructure.sh

set -e

echo "========================================="
echo "Starting CQRS Infrastructure"
echo "========================================="

# Check prerequisites
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "Error: Docker Compose is not installed"
    exit 1
fi

# Check if .env exists
if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
    echo "Please review and update .env file if needed"
fi

# Create necessary directories
echo "Creating infrastructure directories..."
mkdir -p infrastructure/vault/{config,scripts,policies}
mkdir -p infrastructure/postgres/{config,init,data,backups}

# Pull latest images
echo "Pulling latest Docker images..."
docker-compose pull --quiet

# Start infrastructure services
echo "Starting infrastructure services..."
docker-compose up -d vault postgres

# Wait for services to be healthy
echo "Waiting for services to be ready..."
timeout 60 bash -c 'until docker-compose ps | grep -q "healthy.*vault"; do sleep 2; done' || {
    echo "Error: Vault failed to start"
    docker-compose logs vault
    exit 1
}

timeout 60 bash -c 'until docker-compose ps | grep -q "healthy.*postgres"; do sleep 2; done' || {
    echo "Error: PostgreSQL failed to start"
    docker-compose logs postgres
    exit 1
}

# Initialize Vault
echo "Initializing Vault with secrets..."
docker-compose up vault-init

# Check service health
echo ""
echo "========================================="
echo "Service Status"
echo "========================================="
docker-compose ps

echo ""
echo "========================================="
echo "Infrastructure Ready!"
echo "========================================="
echo "Vault UI:       http://localhost:8200/ui"
echo "Vault Token:    $(grep VAULT_ROOT_TOKEN .env | cut -d '=' -f2)"
echo "PostgreSQL:     localhost:5432"
echo "Database:       $(grep POSTGRES_DB .env | cut -d '=' -f2)"
echo "User:           $(grep POSTGRES_USER .env | cut -d '=' -f2)"
echo ""
echo "To start the application:"
echo "  docker-compose up -d app"
echo ""
echo "To view logs:"
echo "  docker-compose logs -f"
echo "========================================="
```

**Application startup script:**
```bash
#!/bin/bash
# scripts/start-app.sh

set -e

echo "========================================="
echo "Starting CQRS Application"
echo "========================================="

# Ensure infrastructure is running
if ! docker-compose ps | grep -q "healthy.*vault"; then
    echo "Error: Infrastructure not running. Run ./scripts/start-infrastructure.sh first"
    exit 1
fi

# Build application
echo "Building application..."
./gradlew clean build -x test

# Start application
echo "Starting application container..."
docker-compose up -d app

# Wait for application to be healthy
echo "Waiting for application to be ready..."
timeout 120 bash -c 'until docker-compose ps | grep -q "healthy.*app"; do sleep 5; done' || {
    echo "Error: Application failed to start"
    docker-compose logs app
    exit 1
}

echo ""
echo "========================================="
echo "Application Ready!"
echo "========================================="
echo "Application URL:  http://localhost:8080"
echo "Debug Port:       5005"
echo "Health Check:     http://localhost:8080/actuator/health"
echo ""
echo "To view logs:"
echo "  docker-compose logs -f app"
echo "========================================="
```

**Shutdown script:**
```bash
#!/bin/bash
# scripts/stop-infrastructure.sh

set -e

echo "========================================="
echo "Stopping CQRS Infrastructure"
echo "========================================="

# Stop all services
docker-compose down

# Optional: remove volumes (data loss!)
if [ "$1" == "--clean" ]; then
    echo "WARNING: Removing volumes (data will be lost)"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down -v
        echo "Volumes removed"
    fi
fi

echo "Infrastructure stopped"
```

**Make scripts executable:**
```bash
chmod +x scripts/*.sh
```

### 4. Health Check Validation

**Health check script:**
```bash
#!/bin/bash
# scripts/health-check.sh

set -e

echo "========================================="
echo "Infrastructure Health Check"
echo "========================================="

check_service() {
    local service=$1
    local url=$2

    echo -n "Checking $service... "
    if curl -sf "$url" > /dev/null 2>&1; then
        echo "✓ Healthy"
        return 0
    else
        echo "✗ Unhealthy"
        return 1
    fi
}

all_healthy=true

# Check Vault
if ! check_service "Vault" "http://localhost:8200/v1/sys/health"; then
    all_healthy=false
fi

# Check PostgreSQL
if ! docker exec cqrs-postgres pg_isready -U cqrs_user -d cqrs_db > /dev/null 2>&1; then
    echo "PostgreSQL... ✗ Unhealthy"
    all_healthy=false
else
    echo "PostgreSQL... ✓ Healthy"
fi

# Check Application (if running)
if docker-compose ps | grep -q "app"; then
    if ! check_service "Application" "http://localhost:8080/actuator/health"; then
        all_healthy=false
    fi
fi

echo "========================================="
if [ "$all_healthy" = true ]; then
    echo "All services healthy ✓"
    exit 0
else
    echo "Some services unhealthy ✗"
    exit 1
fi
```

### 5. Networking Configuration

**Custom bridge network with explicit subnet:**
```yaml
networks:
  cqrs-network:
    name: cqrs-network
    driver: bridge
    ipam:
      driver: default
      config:
        - subnet: 172.28.0.0/16
          gateway: 172.28.0.1
    driver_opts:
      com.docker.network.bridge.name: br-cqrs
      com.docker.network.bridge.enable_icc: "true"
      com.docker.network.bridge.enable_ip_masquerade: "true"
```

**Service discovery testing:**
```bash
#!/bin/bash
# scripts/test-networking.sh

echo "Testing inter-service connectivity..."

# From app to vault
docker exec cqrs-app wget -q -O- http://vault:8200/v1/sys/health
echo "App -> Vault: ✓"

# From app to postgres
docker exec cqrs-app nc -zv postgres 5432
echo "App -> PostgreSQL: ✓"

echo "Network connectivity verified ✓"
```

### 6. Logging and Monitoring

**Centralized logging:**
```yaml
# Add to each service in docker-compose.yml
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
        labels: "service,description"
```

**Log viewing script:**
```bash
#!/bin/bash
# scripts/view-logs.sh

if [ -z "$1" ]; then
    echo "Usage: ./scripts/view-logs.sh [service|all]"
    echo "Services: vault, postgres, app"
    exit 1
fi

if [ "$1" == "all" ]; then
    docker-compose logs -f
else
    docker-compose logs -f "$1"
fi
```

### 7. Development Workflow Commands

**Makefile for convenience:**
```makefile
# Makefile

.PHONY: help start stop restart clean logs health build

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

start: ## Start infrastructure services
	@./scripts/start-infrastructure.sh

start-app: ## Start application
	@./scripts/start-app.sh

stop: ## Stop all services
	@./scripts/stop-infrastructure.sh

restart: stop start ## Restart all services

clean: ## Stop services and remove volumes
	@./scripts/stop-infrastructure.sh --clean

logs: ## View all logs
	@docker-compose logs -f

logs-vault: ## View Vault logs
	@docker-compose logs -f vault

logs-postgres: ## View PostgreSQL logs
	@docker-compose logs -f postgres

logs-app: ## View application logs
	@docker-compose logs -f app

health: ## Check service health
	@./scripts/health-check.sh

ps: ## Show service status
	@docker-compose ps

build: ## Build application
	@./gradlew clean build -x test

rebuild: ## Rebuild and restart application
	@./gradlew clean build -x test
	@docker-compose up -d --build app

shell-vault: ## Open shell in Vault container
	@docker exec -it cqrs-vault sh

shell-postgres: ## Open psql in PostgreSQL container
	@docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db

shell-app: ## Open shell in application container
	@docker exec -it cqrs-app sh
```

## Testing Strategy

### 1. Dependency Order Tests

```bash
#!/bin/bash
# scripts/test-startup-order.sh

echo "Testing service startup order..."

# Start with clean slate
docker-compose down -v

# Start vault only
docker-compose up -d vault
sleep 10

# Verify postgres doesn't start until vault is healthy
if docker-compose ps postgres | grep -q "Up"; then
    echo "ERROR: PostgreSQL started before Vault was healthy"
    exit 1
fi

# Vault should be healthy
if ! docker-compose ps vault | grep -q "healthy"; then
    echo "ERROR: Vault not healthy"
    exit 1
fi

echo "✓ Startup order validated"
```

### 2. Health Check Tests

```bash
#!/bin/bash
# scripts/test-health-checks.sh

echo "Testing health checks..."

# Start services
docker-compose up -d vault postgres

# Wait and check health
sleep 30

vault_health=$(docker inspect --format='{{.State.Health.Status}}' cqrs-vault)
postgres_health=$(docker inspect --format='{{.State.Health.Status}}' cqrs-postgres)

if [ "$vault_health" != "healthy" ]; then
    echo "ERROR: Vault health check failed"
    exit 1
fi

if [ "$postgres_health" != "healthy" ]; then
    echo "ERROR: PostgreSQL health check failed"
    exit 1
fi

echo "✓ Health checks validated"
```

### 3. Network Connectivity Tests

```bash
#!/bin/bash
# scripts/test-network.sh

echo "Testing network connectivity..."

# Ensure services are running
docker-compose up -d vault postgres

# Wait for healthy
sleep 20

# Test vault -> postgres
if ! docker exec cqrs-vault nc -zv postgres 5432 2>&1 | grep -q "succeeded"; then
    echo "ERROR: Cannot connect from Vault to PostgreSQL"
    exit 1
fi

echo "✓ Network connectivity validated"
```

## Rollout Steps

1. **Create scripts directory**
   ```bash
   mkdir -p scripts
   ```

2. **Create all shell scripts**
   - start-infrastructure.sh
   - start-app.sh
   - stop-infrastructure.sh
   - health-check.sh
   - view-logs.sh

3. **Make scripts executable**
   ```bash
   chmod +x scripts/*.sh
   ```

4. **Create .env.example**
   - Document all environment variables
   - Provide sensible defaults

5. **Create Makefile**
   - Add all convenience commands
   - Test each command

6. **Update docker-compose.yml**
   - Add health checks to all services
   - Configure dependencies
   - Set up logging

7. **Test infrastructure startup**
   ```bash
   ./scripts/start-infrastructure.sh
   ```

8. **Validate service dependencies**
   - Check startup order
   - Verify health checks
   - Test network connectivity

9. **Test complete lifecycle**
   - Start infrastructure
   - Start application
   - Stop everything
   - Verify cleanup

10. **Create documentation**
    - Document all commands
    - Provide troubleshooting guide
    - Include examples

## Verification Checklist

- [ ] All services start in correct dependency order
- [ ] Vault starts first and becomes healthy
- [ ] PostgreSQL waits for Vault
- [ ] vault-init runs after both are healthy
- [ ] Application waits for vault-init completion
- [ ] Health checks pass for all services
- [ ] All services networked correctly
- [ ] Services can communicate using hostnames
- [ ] Single command starts all infrastructure
- [ ] Single command stops all infrastructure
- [ ] Scripts are idempotent
- [ ] Logs are accessible and structured
- [ ] Environment variables loaded from .env
- [ ] Makefile commands work correctly

## Troubleshooting Guide

### Issue: Services start in wrong order
**Solution:**
- Check `depends_on` conditions in docker-compose.yml
- Verify health checks are configured
- Review service logs for startup issues

### Issue: Health checks failing
**Solution:**
- Increase `start_period` in health check config
- Check service logs: `docker-compose logs <service>`
- Verify health check command is correct
- Test health endpoint manually

### Issue: Network connectivity problems
**Solution:**
- Verify all services on same network
- Check service names in connection strings
- Test with: `docker exec <container> nc -zv <service> <port>`
- Review network configuration

### Issue: Environment variables not loaded
**Solution:**
- Verify .env file exists
- Check .env file format (no spaces around =)
- Restart Docker Compose
- Use `docker-compose config` to verify

### Issue: Startup scripts fail
**Solution:**
- Check script permissions: `chmod +x scripts/*.sh`
- Review script output for errors
- Verify prerequisites (Docker, Docker Compose)
- Check available disk space and memory

## Performance Optimization

**Resource limits:**
```yaml
# Add to each service
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M
```

**Startup optimization:**
```yaml
# Parallel startup where possible
# Only use depends_on where necessary
# Use start_period to allow slow starts
```

## Dependencies

- **Blocks:** AC6 (Development Experience), AC7 (Data Seeding and Reset)
- **Blocked By:** AC1 (Secrets Management), AC3 (Relational Database)
- **Related:** AC8 (Documentation)
