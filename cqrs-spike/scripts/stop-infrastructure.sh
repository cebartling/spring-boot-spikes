#!/bin/bash
# scripts/stop-infrastructure.sh
# Stops CQRS infrastructure services

set -e

echo "========================================="
echo "Stopping CQRS Infrastructure"
echo "========================================="

# Determine docker compose command
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Stop all services
$DOCKER_COMPOSE down

# Optional: remove volumes (data loss!)
if [ "$1" == "--clean" ]; then
    echo "WARNING: Removing volumes (data will be lost)"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        $DOCKER_COMPOSE down -v
        echo "Volumes removed"
    fi
fi

echo "Infrastructure stopped"
