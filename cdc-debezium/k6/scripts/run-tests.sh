#!/bin/bash
# k6/scripts/run-tests.sh

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$K6_DIR")"
RESULTS_DIR="${RESULTS_DIR:-$K6_DIR/results}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Default scenarios
SCENARIOS="${1:-baseline}"

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Create results directory
mkdir -p "$RESULTS_DIR"

# Change to project directory for docker compose commands
cd "$PROJECT_DIR"

# Start infrastructure if not running
if ! docker compose ps postgres 2>/dev/null | grep -q "healthy"; then
    log "Starting CDC infrastructure..."
    docker compose up -d
    log "Waiting for infrastructure to be healthy..."
    sleep 30
fi

# Build k6 if needed
log "Building k6 image..."
docker compose -f k6/docker-compose.k6.yml build k6

# Run health check
log "Running health check..."
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/health-check.js

# Run specified scenarios
IFS=',' read -ra SCENARIO_LIST <<< "$SCENARIOS"

for scenario in "${SCENARIO_LIST[@]}"; do
    log "Running $scenario test..."

    script_name="${scenario}-test.js"
    if [ "$scenario" == "e2e-latency" ]; then
        script_name="e2e-latency-test.js"
    elif [ "$scenario" == "mixed-workload" ]; then
        script_name="mixed-workload-test.js"
    fi

    docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
        --out json=/results/${scenario}-${TIMESTAMP}.json \
        --out experimental-prometheus-rw \
        /scripts/${script_name} || {
            warn "Test $scenario completed with warnings"
        }

    log "Test $scenario completed. Results: $RESULTS_DIR/${scenario}-${TIMESTAMP}.json"
done

# Generate summary
log "Generating summary..."
echo ""
echo "========================================"
echo "         TEST RESULTS SUMMARY"
echo "========================================"

for result in "$RESULTS_DIR"/*-${TIMESTAMP}.json; do
    if [ -f "$result" ]; then
        scenario=$(basename "$result" -${TIMESTAMP}.json)
        echo ""
        echo "--- $scenario ---"
        jq -r '
            "Iterations: \(.root_group.iterations // "N/A")",
            "Duration: \(.state.testRunDurationMs // 0 | . / 1000 | tostring + "s")",
            "VUs: \(.vus_max // "N/A")"
        ' "$result" 2>/dev/null || echo "Unable to parse results"

        # Check for failures
        fails=$(jq -r '.root_group.checks | to_entries | map(select(.value.fails > 0)) | length' "$result" 2>/dev/null || echo "0")
        if [ "$fails" -gt 0 ]; then
            error "  Checks failed: $fails"
        else
            log "  All checks passed"
        fi
    fi
done

echo ""
echo "========================================"
log "All tests completed. Results in $RESULTS_DIR"
