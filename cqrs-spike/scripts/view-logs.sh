#!/bin/bash
# scripts/view-logs.sh
# Views logs for infrastructure services

# Determine docker compose command
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

if [ -z "$1" ]; then
    echo "Usage: ./scripts/view-logs.sh [service|all]"
    echo "Services: vault, postgres, vault-init, all"
    exit 1
fi

if [ "$1" == "all" ]; then
    $DOCKER_COMPOSE logs -f
else
    $DOCKER_COMPOSE logs -f "$1"
fi
