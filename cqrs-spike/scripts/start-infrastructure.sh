#!/bin/bash
# scripts/start-infrastructure.sh
# Starts CQRS infrastructure services including observability platform

set -e

echo "========================================="
echo "Starting CQRS Infrastructure"
echo "========================================="

# Check prerequisites
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "Error: Docker Compose is not installed"
    exit 1
fi

# Determine docker compose command
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Check if .env exists
if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
    echo "Please review and update .env file if needed"
fi

# Create necessary directories
echo "Creating infrastructure directories..."
mkdir -p infrastructure/vault/{config,scripts,policies,data}
mkdir -p infrastructure/postgres/{config,init,data,backups}

# Pull latest images
echo "Pulling latest Docker images..."
$DOCKER_COMPOSE pull --quiet

# Start infrastructure services
echo "Starting infrastructure services..."
$DOCKER_COMPOSE up -d

# Function to wait for a service to be healthy
wait_for_service() {
    local service_name=$1
    local is_critical=${2:-false}
    local max_attempts=30
    local counter=0
    
    until $DOCKER_COMPOSE ps "$service_name" | grep -q '(healthy)' || [ $counter -eq $max_attempts ]; do
        sleep 2
        counter=$((counter + 1))
    done
    
    if ! $DOCKER_COMPOSE ps "$service_name" | grep -q '(healthy)'; then
        if [ "$is_critical" = "true" ]; then
            echo "Error: $service_name failed to start"
            $DOCKER_COMPOSE logs "$service_name"
            exit 1
        else
            echo "Warning: $service_name may not be fully ready yet"
        fi
    else
        echo "$service_name is healthy ✓"
    fi
}

# Wait for services to be healthy
echo "Waiting for services to be ready..."

# Wait for critical services
wait_for_service "vault" true
wait_for_service "postgres" true

# Wait for observability services (non-critical)
wait_for_service "prometheus"
wait_for_service "loki"
wait_for_service "grafana"

# Note: Tempo uses distroless image without health check capability
echo "Tempo is running (no health check available) ✓"

# Initialize Vault
echo "Initializing Vault with secrets..."
$DOCKER_COMPOSE up vault-init

# Check service health
echo ""
echo "========================================="
echo "Service Status"
echo "========================================="
$DOCKER_COMPOSE ps

echo ""
echo "========================================="
echo "Infrastructure Ready!"
echo "========================================="
echo ""
echo "Core Services:"
echo "  Vault UI:       http://localhost:8200/ui"
echo "  Vault Token:    $(grep VAULT_ROOT_TOKEN .env 2>/dev/null | cut -d '=' -f2 || echo 'dev-root-token')"
echo "  PostgreSQL:     localhost:5432"
echo "  Database:       $(grep POSTGRES_DB .env 2>/dev/null | cut -d '=' -f2 || echo 'cqrs_db')"
echo "  User:           $(grep POSTGRES_USER .env 2>/dev/null | cut -d '=' -f2 || echo 'cqrs_user')"
echo ""
echo "Observability Platform:"
echo "  Grafana:        http://localhost:3000 (anonymous access enabled)"
echo "  Prometheus:     http://localhost:9090"
echo "  Loki:           http://localhost:3100"
echo "  Tempo:          http://localhost:3200"
echo ""
echo "To view logs:"
echo "  $DOCKER_COMPOSE logs -f"
echo ""
echo "To stop infrastructure:"
echo "  ./scripts/stop-infrastructure.sh"
echo "========================================="
