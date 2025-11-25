#!/bin/bash
# infrastructure/postgres/seed-data/scripts/reset-data-only.sh
# Resets data only (preserves schema)

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

print_header "Database Data Reset (Schema Preserved)"

echo ""
print_warning "WARNING: This will delete ALL data but preserve schema!"
echo ""

# Check for force flag
FORCE=false
if [ "$1" == "--force" ] || [ "$1" == "-f" ]; then
    FORCE=true
fi

if [ "$FORCE" != true ]; then
    read -p "Are you sure? (yes/no): " confirm
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
echo "Truncating all tables..."

# Truncate all tables in reverse dependency order
docker exec "$CONTAINER_NAME" psql -U cqrs_user -d cqrs_db <<'EOF'
-- Disable triggers temporarily
SET session_replication_role = replica;

-- Truncate event store (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'event_store' AND table_name = 'domain_event') THEN
        TRUNCATE TABLE event_store.domain_event CASCADE;
        RAISE NOTICE 'Truncated event_store.domain_event';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'event_store' AND table_name = 'event_stream') THEN
        TRUNCATE TABLE event_store.event_stream CASCADE;
        RAISE NOTICE 'Truncated event_store.event_stream';
    END IF;
END $$;

-- Truncate read models (dynamic - all tables in schema)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'read_model') LOOP
        EXECUTE 'TRUNCATE TABLE read_model.' || quote_ident(r.tablename) || ' CASCADE';
        RAISE NOTICE 'Truncated read_model.%', r.tablename;
    END LOOP;
END $$;

-- Truncate command model (dynamic - all tables in schema)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'command_model') LOOP
        EXECUTE 'TRUNCATE TABLE command_model.' || quote_ident(r.tablename) || ' CASCADE';
        RAISE NOTICE 'Truncated command_model.%', r.tablename;
    END LOOP;
END $$;

-- Re-enable triggers
SET session_replication_role = DEFAULT;

SELECT 'All data truncated successfully' as status;
EOF

print_header "Data Reset Complete"

echo ""
print_success "All data has been cleared"
echo ""
echo "Seed new data with:"
echo "  ./infrastructure/postgres/seed-data/scripts/seed.sh [scenario]"
echo ""
echo "Available scenarios:"
ls -1 "$SCRIPT_DIR/../scenarios/"
