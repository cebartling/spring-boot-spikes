#!/bin/bash
# Start Observability Platform
# Usage: ./scripts/start-observability.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "Starting Observability Platform"
echo "========================================="

cd "$PROJECT_DIR"

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Start observability services
echo "Starting observability services..."
$DOCKER_COMPOSE up -d tempo loki promtail prometheus grafana

# Wait for services to be ready
echo ""
echo "Waiting for services to start..."
sleep 10

# Check service health
echo ""
echo "Checking service health..."

# Tempo
if curl -sf http://localhost:3200/status > /dev/null 2>&1; then
    echo "  ✓ Tempo is ready (http://localhost:3200)"
else
    echo "  ✗ Tempo failed to start"
fi

# Loki
if curl -sf http://localhost:3100/ready > /dev/null 2>&1; then
    echo "  ✓ Loki is ready (http://localhost:3100)"
else
    echo "  ✗ Loki failed to start"
fi

# Prometheus
if curl -sf http://localhost:9090/-/healthy > /dev/null 2>&1; then
    echo "  ✓ Prometheus is ready (http://localhost:9090)"
else
    echo "  ✗ Prometheus failed to start"
fi

# Grafana
if curl -sf http://localhost:3000/api/health > /dev/null 2>&1; then
    echo "  ✓ Grafana is ready (http://localhost:3000)"
else
    echo "  ✗ Grafana failed to start"
fi

echo ""
echo "========================================="
echo "Observability Platform Ready!"
echo "========================================="
echo ""
echo "Service URLs:"
echo "  Grafana:    http://localhost:3000"
echo "  Prometheus: http://localhost:9090"
echo "  Tempo:      http://localhost:3200"
echo "  Loki:       http://localhost:3100"
echo ""
echo "========================================="
