#!/bin/bash
# Stop Observability Platform
# Usage: ./scripts/stop-observability.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "Stopping Observability Platform"
echo "========================================="

cd "$PROJECT_DIR"

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Stop observability services
$DOCKER_COMPOSE stop tempo loki promtail prometheus grafana

echo ""
echo "Observability services stopped."
echo ""

# Check if --clean flag is passed
if [ "$1" = "--clean" ]; then
    echo "Removing volumes..."
    docker volume rm cqrs-tempo-data cqrs-loki-data cqrs-prometheus-data cqrs-grafana-data 2>/dev/null || true
    echo "Volumes removed."
fi
