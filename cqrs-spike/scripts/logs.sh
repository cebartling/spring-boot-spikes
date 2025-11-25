#!/bin/bash
# Log viewing script with filtering
# Usage: ./scripts/logs.sh [service] [options]

SERVICE="${1:-all}"
FOLLOW="${2:--f}"

# Detect docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

case "$SERVICE" in
  all)
    $DOCKER_COMPOSE logs $FOLLOW
    ;;
  vault)
    $DOCKER_COMPOSE logs $FOLLOW vault
    ;;
  postgres|db)
    $DOCKER_COMPOSE logs $FOLLOW postgres
    ;;
  app|application)
    $DOCKER_COMPOSE logs $FOLLOW app
    ;;
  errors)
    $DOCKER_COMPOSE logs $FOLLOW | grep -i "error\|exception\|fatal"
    ;;
  warnings)
    $DOCKER_COMPOSE logs $FOLLOW | grep -i "warn\|warning"
    ;;
  tail)
    $DOCKER_COMPOSE logs --tail=100 app
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
