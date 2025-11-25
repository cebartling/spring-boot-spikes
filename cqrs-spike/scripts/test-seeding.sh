#!/bin/bash
# scripts/test-seeding.sh
# Tests database seeding functionality

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SEED_SCRIPTS="$PROJECT_DIR/infrastructure/postgres/seed-data/scripts"

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

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

test_passed=0
test_failed=0

run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_min="$3"

    echo -n "Testing: $test_name... "

    if result=$(eval "$test_command" 2>/dev/null); then
        count=$(echo "$result" | tr -d ' ')
        if [ "$count" -ge "$expected_min" ]; then
            print_success "passed ($count records)"
            ((test_passed++))
            return 0
        else
            print_error "failed (expected >= $expected_min, got $count)"
            ((test_failed++))
            return 1
        fi
    else
        print_error "failed (command error)"
        ((test_failed++))
        return 1
    fi
}

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
    echo "Please start infrastructure first: make start"
    exit 1
fi

print_header "Testing Database Seeding"
echo ""
echo "Container: $CONTAINER_NAME"
echo ""

# Test 1: Reset data only
echo "Step 1: Testing data reset..."
if "$SEED_SCRIPTS/reset-data-only.sh" --force > /dev/null 2>&1; then
    print_success "Data reset successful"
else
    print_error "Data reset failed"
    exit 1
fi

# Verify data is empty
EVENT_COUNT=$(docker exec "$CONTAINER_NAME" psql -U cqrs_user -d cqrs_db -t -c "SELECT COUNT(*) FROM event_store.domain_event" 2>/dev/null | tr -d ' ')
if [ "$EVENT_COUNT" -eq 0 ]; then
    print_success "Database is empty after reset"
else
    print_error "Database not empty after reset ($EVENT_COUNT events found)"
fi

echo ""

# Test 2: Seed minimal scenario
print_header "Testing Minimal Scenario"
if "$SEED_SCRIPTS/seed.sh" minimal > /dev/null 2>&1; then
    print_success "Minimal scenario seeded"
else
    print_error "Minimal scenario seeding failed"
    exit 1
fi

run_test "Event streams exist" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM event_store.event_stream'" \
    3

run_test "Domain events exist" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM event_store.domain_event'" \
    4

run_test "Order summaries exist" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM read_model.order_summary'" \
    2

echo ""

# Test 3: Reset and seed standard scenario
print_header "Testing Standard Scenario"
"$SEED_SCRIPTS/reset-data-only.sh" --force > /dev/null 2>&1
if "$SEED_SCRIPTS/seed.sh" standard > /dev/null 2>&1; then
    print_success "Standard scenario seeded"
else
    print_error "Standard scenario seeding failed"
    exit 1
fi

run_test "More event streams in standard" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM event_store.event_stream'" \
    7

run_test "Users table populated" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM command_model.users'" \
    4

run_test "Product catalog populated" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM read_model.product_catalog'" \
    2

echo ""

# Test 4: Reset and seed full scenario
print_header "Testing Full Scenario"
"$SEED_SCRIPTS/reset-data-only.sh" --force > /dev/null 2>&1
if "$SEED_SCRIPTS/seed.sh" full > /dev/null 2>&1; then
    print_success "Full scenario seeded"
else
    print_error "Full scenario seeding failed"
    exit 1
fi

run_test "Comprehensive event streams" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM event_store.event_stream'" \
    10

run_test "Comprehensive events" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM event_store.domain_event'" \
    20

run_test "Analytics tables populated" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM read_model.daily_sales_summary'" \
    10

run_test "Customer LTV populated" \
    "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM read_model.customer_lifetime_value'" \
    3

echo ""

# Test 5: Performance scenario (optional, can be slow)
if [ "$1" == "--include-perf" ]; then
    print_header "Testing Performance Scenario"
    "$SEED_SCRIPTS/reset-data-only.sh" --force > /dev/null 2>&1
    echo "This may take a while..."

    if timeout 120 "$SEED_SCRIPTS/seed.sh" performance > /dev/null 2>&1; then
        print_success "Performance scenario seeded"
    else
        print_error "Performance scenario seeding failed or timed out"
    fi

    run_test "Large event dataset" \
        "docker exec $CONTAINER_NAME psql -U cqrs_user -d cqrs_db -t -c 'SELECT COUNT(*) FROM event_store.domain_event WHERE user_id = '\\''perf-test-user'\\'''" \
        10000
fi

echo ""
print_header "Test Summary"

echo ""
echo -e "Tests passed: ${GREEN}$test_passed${NC}"
echo -e "Tests failed: ${RED}$test_failed${NC}"
echo ""

# Reset to minimal for development use
echo "Resetting to minimal scenario for development..."
"$SEED_SCRIPTS/reset-data-only.sh" --force > /dev/null 2>&1
"$SEED_SCRIPTS/seed.sh" minimal > /dev/null 2>&1
print_success "Database reset to minimal scenario"

echo ""
if [ "$test_failed" -eq 0 ]; then
    print_success "All seeding tests passed!"
    exit 0
else
    print_error "Some tests failed"
    exit 1
fi
