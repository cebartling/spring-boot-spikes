#!/bin/bash
# scripts/start-infrastructure.sh
# Starts CQRS infrastructure services (Vault and PostgreSQL)

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
$DOCKER_COMPOSE pull --quiet vault postgres

# Start infrastructure services
echo "Starting infrastructure services..."
$DOCKER_COMPOSE up -d vault postgres

# Wait for services to be healthy
echo "Waiting for services to be ready..."

# Wait for Vault (max 60 seconds)
COUNTER=0
until $DOCKER_COMPOSE ps vault | grep -q '(healthy)' || [ $COUNTER -eq 30 ]; do
    sleep 2
    COUNTER=$((COUNTER + 1))
done

if ! $DOCKER_COMPOSE ps vault | grep -q '(healthy)'; then
    echo "Error: Vault failed to start"
    $DOCKER_COMPOSE logs vault
    exit 1
fi
echo "Vault is healthy ✓"

# Wait for PostgreSQL (max 60 seconds)
COUNTER=0
until $DOCKER_COMPOSE ps postgres | grep -q '(healthy)' || [ $COUNTER -eq 30 ]; do
    sleep 2
    COUNTER=$((COUNTER + 1))
done

if ! $DOCKER_COMPOSE ps postgres | grep -q '(healthy)'; then
    echo "Error: PostgreSQL failed to start"
    $DOCKER_COMPOSE logs postgres
    exit 1
fi
echo "PostgreSQL is healthy ✓"

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
echo "Vault UI:       http://localhost:8200/ui"
echo "Vault Token:    $(grep VAULT_ROOT_TOKEN .env 2>/dev/null | cut -d '=' -f2 || echo 'dev-root-token')"
echo "PostgreSQL:     localhost:5432"
echo "Database:       $(grep POSTGRES_DB .env 2>/dev/null | cut -d '=' -f2 || echo 'cqrs_db')"
echo "User:           $(grep POSTGRES_USER .env 2>/dev/null | cut -d '=' -f2 || echo 'cqrs_user')"
echo ""
echo "To view logs:"
echo "  $DOCKER_COMPOSE logs -f"
echo ""
echo "To stop infrastructure:"
echo "  ./scripts/stop-infrastructure.sh"
echo "========================================="
