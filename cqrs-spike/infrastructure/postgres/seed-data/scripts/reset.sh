#!/bin/bash
# infrastructure/postgres/seed-data/scripts/reset.sh
# Resets the database by dropping and recreating it

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}=========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_header "Database Reset"

echo ""
print_warning "WARNING: This will delete ALL data and drop the database!"
echo ""

# Check for force flag
FORCE=false
if [ "$1" == "--force" ] || [ "$1" == "-f" ]; then
    FORCE=true
fi

if [ "$FORCE" != true ]; then
    read -p "Are you sure you want to continue? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        echo "Reset cancelled"
        exit 0
    fi
fi

# Determine container name
CONTAINER_NAME=""
for name in "cqrs-postgres" "cqrs-spike-postgres-1" "postgres"; do
    if docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
        CONTAINER_NAME="$name"
        break
    fi
done

if [ -z "$CONTAINER_NAME" ]; then
    print_error "PostgreSQL container not found"
    exit 1
fi

print_success "Found PostgreSQL container: $CONTAINER_NAME"

echo ""
echo "Dropping and recreating database..."

# Terminate existing connections and drop database
docker exec "$CONTAINER_NAME" psql -U postgres -c "
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'cqrs_db'
  AND pid <> pg_backend_pid();
" 2>/dev/null || true

docker exec "$CONTAINER_NAME" psql -U postgres -c "DROP DATABASE IF EXISTS cqrs_db;"
print_success "Database dropped"

docker exec "$CONTAINER_NAME" psql -U postgres -c "CREATE DATABASE cqrs_db OWNER cqrs_user;"
print_success "Database recreated"

print_header "Database Reset Complete"

echo ""
echo "Next steps:"
echo "  1. Restart the application to re-run Flyway migrations:"
echo "     docker-compose restart app"
echo ""
echo "  2. Or seed data directly (if migrations are in init scripts):"
echo "     ./infrastructure/postgres/seed-data/scripts/seed.sh [scenario]"
echo ""
echo "Available scenarios:"
ls -1 "$SCRIPT_DIR/../scenarios/"
