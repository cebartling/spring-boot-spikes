#!/bin/bash
# scripts/health-check.sh
# Checks health status of all infrastructure services

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

# Check Prometheus
if ! check_service "Prometheus" "http://localhost:9090/-/healthy"; then
    all_healthy=false
fi

# Check Loki (use buildinfo endpoint as /ready returns 503 during warmup)
if ! check_service "Loki" "http://localhost:3100/loki/api/v1/status/buildinfo"; then
    all_healthy=false
fi

# Check Grafana
if ! check_service "Grafana" "http://localhost:3000/api/health"; then
    all_healthy=false
fi

# Check Tempo (basic connectivity check - no health endpoint in distroless image)
echo -n "Checking Tempo... "
if curl -sf "http://localhost:3200/ready" > /dev/null 2>&1; then
    echo "✓ Healthy"
else
    echo "⚠ Running (limited health check)"
fi

# Determine docker compose command
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Check container status
echo ""
echo "Container Status:"
$DOCKER_COMPOSE ps

echo ""
echo "========================================="
if [ "$all_healthy" = true ]; then
    echo "All services healthy ✓"
    exit 0
else
    echo "Some services unhealthy ✗"
    exit 1
fi
