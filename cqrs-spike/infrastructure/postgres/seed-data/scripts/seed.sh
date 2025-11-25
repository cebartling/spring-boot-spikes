#!/bin/bash
# infrastructure/postgres/seed-data/scripts/seed.sh
# Seeds the database with test data for a specified scenario

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIO="${1:-minimal}"
SEED_DIR="$SCRIPT_DIR/../scenarios/$SCENARIO"

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

# Check if scenario exists
if [ ! -d "$SEED_DIR" ]; then
    print_error "Scenario '$SCENARIO' not found"
    echo ""
    echo "Available scenarios:"
    ls -1 "$SCRIPT_DIR/../scenarios/"
    exit 1
fi

print_header "Seeding Database: $SCENARIO scenario"

# Determine container name (try common variations)
CONTAINER_NAME=""
for name in "cqrs-postgres" "cqrs-spike-postgres-1" "postgres"; do
    if docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
        CONTAINER_NAME="$name"
        break
    fi
done

if [ -z "$CONTAINER_NAME" ]; then
    print_error "PostgreSQL container not found"
    echo "Please ensure the PostgreSQL container is running"
    echo "Run: docker-compose up -d postgres"
    exit 1
fi

print_success "Found PostgreSQL container: $CONTAINER_NAME"

# Check if PostgreSQL is accessible
if ! docker exec "$CONTAINER_NAME" pg_isready -U cqrs_user -d cqrs_db > /dev/null 2>&1; then
    print_error "PostgreSQL is not accessible"
    exit 1
fi

print_success "PostgreSQL is ready"
echo ""

# Execute seed scripts in order
for script in "$SEED_DIR"/*.sql; do
    if [ -f "$script" ]; then
        script_name=$(basename "$script")
        echo -e "Executing: ${YELLOW}$script_name${NC}"

        if docker exec -i "$CONTAINER_NAME" psql -U cqrs_user -d cqrs_db < "$script" 2>&1; then
            print_success "$script_name completed"
        else
            print_error "$script_name failed"
            exit 1
        fi
        echo ""
    fi
done

print_header "Seeding Complete!"

# Show summary
echo ""
echo "Data Summary:"
docker exec "$CONTAINER_NAME" psql -U cqrs_user -d cqrs_db -t -c "
SELECT 'Event Streams: ' || COUNT(*) FROM event_store.event_stream
UNION ALL
SELECT 'Domain Events: ' || COUNT(*) FROM event_store.domain_event;
" 2>/dev/null || true

# Check for read_model tables
docker exec "$CONTAINER_NAME" psql -U cqrs_user -d cqrs_db -t -c "
SELECT 'Order Summaries: ' || COALESCE(COUNT(*), 0)
FROM read_model.order_summary;
" 2>/dev/null || echo "Order Summaries: table not present"

echo ""
print_success "Database seeded with '$SCENARIO' scenario"
