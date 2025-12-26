#!/bin/bash
# scripts/startup.sh
#
# Start all CDC services including infrastructure and k6 load testing stack.
#
# Usage:
#   ./scripts/startup.sh              # Start all services
#   ./scripts/startup.sh --no-k6      # Start only main CDC stack
#   ./scripts/startup.sh --with-chaos # Start with chaos engineering tools

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
START_K6=true
START_CHAOS=false
DEPLOY_CONNECTOR=true

for arg in "$@"; do
    case $arg in
        --no-k6)
            START_K6=false
            ;;
        --with-chaos)
            START_CHAOS=true
            ;;
        --no-connector)
            DEPLOY_CONNECTOR=false
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Start all CDC services and infrastructure."
            echo ""
            echo "Options:"
            echo "  --no-k6         Skip starting k6 load testing stack"
            echo "  --with-chaos    Also start chaos engineering tools (Pumba, Toxiproxy)"
            echo "  --no-connector  Skip deploying Debezium connector"
            echo "  --help          Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                      # Start all services with k6"
            echo "  $0 --no-k6              # Start only main CDC infrastructure"
            echo "  $0 --with-chaos         # Start all services including chaos tools"
            echo "  $0 --no-k6 --with-chaos # Start CDC infrastructure with chaos tools"
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

wait_for_healthy() {
    local service=$1
    local container=$2
    local max_wait=${3:-120}
    local elapsed=0

    log "Waiting for $service to be healthy..."
    while [ $elapsed -lt $max_wait ]; do
        local status=$(docker inspect "$container" --format='{{.State.Health.Status}}' 2>/dev/null || echo "not_found")
        if [ "$status" = "healthy" ]; then
            log "$service is healthy!"
            return 0
        elif [ "$status" = "not_found" ]; then
            warn "$service container not found, waiting..."
        fi
        sleep 5
        elapsed=$((elapsed + 5))
        echo -n "."
    done
    echo ""
    error "$service did not become healthy within ${max_wait}s"
    return 1
}

wait_for_kafka_connect() {
    local max_wait=${1:-120}
    local elapsed=0

    log "Waiting for Kafka Connect to be ready..."
    while [ $elapsed -lt $max_wait ]; do
        if curl -sf http://localhost:8083/connectors > /dev/null 2>&1; then
            log "Kafka Connect is ready!"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
        echo -n "."
    done
    echo ""
    error "Kafka Connect did not become ready within ${max_wait}s"
    return 1
}

deploy_connector() {
    log "Checking for existing Debezium connector..."
    if curl -sf http://localhost:8083/connectors/postgres-cdc-connector > /dev/null 2>&1; then
        log "Connector already exists, skipping deployment"
        return 0
    fi

    log "Deploying Debezium PostgreSQL connector..."
    if curl -sf -X POST -H "Content-Type: application/json" \
        --data @"$PROJECT_DIR/docker/debezium/connector-config.json" \
        http://localhost:8083/connectors > /dev/null 2>&1; then
        log "Connector deployed successfully"

        # Wait for connector to be running
        sleep 5
        local status=$(curl -sf http://localhost:8083/connectors/postgres-cdc-connector/status 2>/dev/null | grep -o '"state":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ "$status" = "RUNNING" ]; then
            log "Connector is running"
        else
            warn "Connector status: $status"
        fi
    else
        error "Failed to deploy connector"
        return 1
    fi
}

cd "$PROJECT_DIR"

section "Starting CDC Infrastructure"

# Start main CDC stack
log "Starting main CDC services..."
docker compose up -d

# Wait for critical services to be healthy
log "Waiting for services to be healthy..."
wait_for_healthy "PostgreSQL" "cdc-postgres" 60
wait_for_healthy "Kafka" "cdc-kafka" 90
wait_for_healthy "MongoDB" "cdc-mongodb" 60
wait_for_healthy "Prometheus" "cdc-prometheus" 60
wait_for_healthy "Grafana" "cdc-grafana" 90

# Wait for Kafka Connect and deploy connector
if [ "$DEPLOY_CONNECTOR" = true ]; then
    wait_for_kafka_connect 120
    deploy_connector
fi

# Start k6 stack if requested
if [ "$START_K6" = true ]; then
    section "Starting k6 Load Testing Stack"

    log "Starting k6 services..."
    docker compose -f k6/docker-compose.k6.yml up -d

    # Wait for CDC consumer to be healthy
    wait_for_healthy "CDC Consumer" "cdc-consumer-k6" 120

    log "k6 stack is ready"
fi

# Start chaos engineering tools if requested
if [ "$START_CHAOS" = true ]; then
    section "Starting Chaos Engineering Tools"

    log "Starting Pumba and Toxiproxy..."
    docker compose -f chaos/docker-compose.chaos.yml --profile chaos up -d

    log "Chaos engineering tools are ready"
fi

section "Startup Summary"

# Show running containers
log "Running CDC containers:"
echo ""
docker ps --filter "name=cdc-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | head -20

# Show service URLs
echo ""
log "Service URLs:"
echo "  - Kafka UI:       http://localhost:8081"
echo "  - Kafka Connect:  http://localhost:8083"
echo "  - Grafana:        http://localhost:3000 (admin/admin)"
echo "  - Prometheus:     http://localhost:9090"
echo "  - Jaeger:         http://localhost:16686"
if [ "$START_K6" = true ]; then
    echo "  - CDC Consumer:   http://localhost:8082/actuator/health"
fi
if [ "$START_CHAOS" = true ]; then
    echo "  - Toxiproxy API:  http://localhost:8474"
fi

# Show connector status
if [ "$DEPLOY_CONNECTOR" = true ]; then
    echo ""
    log "Debezium Connector Status:"
    curl -sf http://localhost:8083/connectors/postgres-cdc-connector/status 2>/dev/null | \
        python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"  State: {d['connector']['state']}\"); print(f\"  Tasks: {len(d.get('tasks', []))} running\")" 2>/dev/null || \
        echo "  Unable to fetch status"
fi

# Show quick start commands
echo ""
log "Quick Start Commands:"
echo "  # Run smoke test"
echo "  docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/smoke-test.js"
echo ""
echo "  # Run acceptance tests"
echo "  ./gradlew acceptanceTest"
echo ""
echo "  # View logs"
echo "  docker compose logs -f cdc-consumer-k6"
echo ""
echo "  # Stop all services"
echo "  ./scripts/shutdown.sh"

echo ""
log "Startup complete!"
