#!/bin/bash
# scripts/shutdown.sh
#
# Shutdown all CDC services and optionally clean up the environment.
#
# Usage:
#   ./scripts/shutdown.sh           # Stop all services, keep data
#   ./scripts/shutdown.sh --clean   # Stop all services and remove volumes
#   ./scripts/shutdown.sh --purge   # Stop, remove volumes, and prune images

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
CLEAN_VOLUMES=false
PURGE_IMAGES=false

for arg in "$@"; do
    case $arg in
        --clean)
            CLEAN_VOLUMES=true
            ;;
        --purge)
            CLEAN_VOLUMES=true
            PURGE_IMAGES=true
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Shutdown all CDC services and clean up the environment."
            echo ""
            echo "Options:"
            echo "  --clean    Stop services and remove all Docker volumes (data loss)"
            echo "  --purge    Stop services, remove volumes, and prune unused images"
            echo "  --help     Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0              # Stop all services, preserve data"
            echo "  $0 --clean      # Stop and remove all data volumes"
            echo "  $0 --purge      # Full cleanup including unused images"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

cd "$PROJECT_DIR"

section "Stopping CDC Services"

# Stop k6 services first (if running)
log "Stopping k6 load testing services..."
if docker compose -f k6/docker-compose.k6.yml ps --quiet 2>/dev/null | grep -q .; then
    docker compose -f k6/docker-compose.k6.yml down --remove-orphans
    log "k6 services stopped"
else
    log "k6 services not running"
fi

# Stop main CDC stack
log "Stopping main CDC infrastructure..."
if docker compose ps --quiet 2>/dev/null | grep -q .; then
    if [ "$CLEAN_VOLUMES" = true ]; then
        warn "Removing services AND volumes (data will be lost)..."
        docker compose down --volumes --remove-orphans
    else
        docker compose down --remove-orphans
    fi
    log "CDC infrastructure stopped"
else
    log "CDC infrastructure not running"
fi

# Clean up any orphaned containers
log "Cleaning up orphaned containers..."
orphans=$(docker ps -a --filter "name=cdc-" --filter "status=exited" -q 2>/dev/null || true)
if [ -n "$orphans" ]; then
    echo "$orphans" | xargs docker rm -f 2>/dev/null || true
    log "Orphaned containers removed"
else
    log "No orphaned containers found"
fi

# Optionally prune images
if [ "$PURGE_IMAGES" = true ]; then
    section "Pruning Docker Resources"

    warn "Removing unused Docker images..."
    docker image prune -f

    warn "Removing unused Docker networks..."
    docker network prune -f

    warn "Removing unused Docker volumes..."
    docker volume prune -f

    log "Docker resources pruned"
fi

section "Cleanup Summary"

# Show remaining containers (if any)
remaining=$(docker ps --filter "name=cdc-" -q 2>/dev/null || true)
if [ -n "$remaining" ]; then
    warn "Some CDC containers are still running:"
    docker ps --filter "name=cdc-" --format "table {{.Names}}\t{{.Status}}"
else
    log "All CDC containers stopped"
fi

# Show volume status
if [ "$CLEAN_VOLUMES" = true ]; then
    log "All data volumes removed"
else
    echo ""
    log "Data volumes preserved. To remove them, run:"
    echo "    $0 --clean"
fi

# Show disk usage
echo ""
log "Docker disk usage:"
docker system df 2>/dev/null || true

echo ""
log "Shutdown complete!"
