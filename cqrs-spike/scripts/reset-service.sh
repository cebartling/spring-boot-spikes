#!/bin/bash
# Service reset helper
# Usage: ./scripts/reset-service.sh <service>

SERVICE="$1"

# Detect docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

if [ -z "$SERVICE" ]; then
    echo "Usage: ./scripts/reset-service.sh <service>"
    echo ""
    echo "Services:"
    echo "  vault       - Reset Vault (restart and re-initialize)"
    echo "  postgres    - Reset PostgreSQL (WARNING: deletes all data)"
    echo "  all         - Restart all services"
    echo ""
    echo "Examples:"
    echo "  ./scripts/reset-service.sh vault"
    echo "  ./scripts/reset-service.sh postgres"
    echo "  ./scripts/reset-service.sh all"
    exit 1
fi

case "$SERVICE" in
  vault)
    echo "Resetting Vault..."
    $DOCKER_COMPOSE restart vault
    echo "Waiting for Vault to be ready..."
    sleep 10
    echo "Re-initializing Vault..."
    $DOCKER_COMPOSE up vault-init
    echo "✓ Vault reset complete"
    ;;

  postgres)
    echo "========================================="
    echo "WARNING: This will delete ALL database data!"
    echo "========================================="
    echo "This includes:"
    echo "  - All event streams"
    echo "  - All read models"
    echo "  - All command models"
    echo "  - Migration history"
    echo ""
    read -p "Are you sure you want to continue? (yes/NO) " -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        echo "Cancelled."
        exit 0
    fi

    echo "Stopping PostgreSQL..."
    $DOCKER_COMPOSE stop postgres

    echo "Removing data volume..."
    docker volume rm cqrs-postgres-data 2>/dev/null || true

    echo "Starting PostgreSQL..."
    $DOCKER_COMPOSE up -d postgres

    echo "Waiting for PostgreSQL to be ready..."
    sleep 5

    echo "✓ PostgreSQL reset complete"
    echo ""
    echo "Note: Run migrations to recreate schemas:"
    echo "  ./gradlew flywayMigrate"
    ;;

  app|application)
    echo "Restarting application..."
    $DOCKER_COMPOSE restart app
    echo "✓ Application restarted"
    ;;

  all)
    echo "Restarting all services..."
    $DOCKER_COMPOSE restart
    echo "✓ All services restarted"
    ;;

  *)
    echo "Error: Unknown service '$SERVICE'"
    echo ""
    echo "Valid services: vault, postgres, app, all"
    exit 1
    ;;
esac
