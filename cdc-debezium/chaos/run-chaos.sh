#!/bin/bash
# chaos/run-chaos.sh
# Chaos engineering test runner for CDC pipeline

set -e

SCENARIO="${1:-mongodb-failure}"
CHAOS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$CHAOS_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[CHAOS]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
info() { echo -e "${BLUE}[INFO]${NC} $1"; }

# Check prerequisites
check_prerequisites() {
    # Check Docker socket
    if [ ! -S /var/run/docker.sock ]; then
        error "Docker socket not found at /var/run/docker.sock"
        exit 1
    fi

    # Check network exists
    if ! docker network inspect cdc-debezium_cdc-network >/dev/null 2>&1; then
        error "CDC network not found. Start the CDC stack first with: docker compose up -d"
        exit 1
    fi
}

# Wait for container to be healthy
wait_for_healthy() {
    local container=$1
    local max_wait=${2:-60}
    local elapsed=0

    log "Waiting for $container to be healthy (max ${max_wait}s)..."
    while [ $elapsed -lt $max_wait ]; do
        local status=$(docker inspect "$container" --format='{{.State.Health.Status}}' 2>/dev/null || echo "not_found")
        if [ "$status" = "healthy" ]; then
            log "$container is healthy!"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done

    warn "$container did not become healthy within ${max_wait}s"
    return 1
}

# Find the running consumer container
find_consumer_container() {
    if docker ps --format '{{.Names}}' | grep -q "cdc-consumer-k6"; then
        echo "cdc-consumer-k6"
    elif docker ps --format '{{.Names}}' | grep -q "cdc-consumer"; then
        echo "cdc-consumer"
    else
        echo ""
    fi
}

case "$SCENARIO" in
    "kafka-partition")
        log "=== Kafka Network Partition ==="
        log "Injecting 100% packet loss to cdc-kafka for 30 seconds..."

        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
            gaiaadm/pumba netem \
            --duration 30s \
            --tc-image gaiadocker/iproute2 \
            loss --percent 100 \
            cdc-kafka

        log "Network partition completed"
        ;;

    "mongodb-failure")
        log "=== MongoDB Crash and Recovery ==="
        check_prerequisites

        # Kill MongoDB
        log "Killing MongoDB container..."
        docker kill cdc-mongodb 2>/dev/null || warn "MongoDB container not running"

        # Wait during outage
        log "MongoDB down for 60 seconds (events will accumulate in Kafka)..."
        sleep 60

        # Restart MongoDB
        log "Restarting MongoDB..."
        cd "$PROJECT_DIR" && docker compose up -d mongodb

        # Wait for healthy
        wait_for_healthy cdc-mongodb 60
        log "MongoDB crash/recovery scenario completed!"
        ;;

    "consumer-restart")
        log "=== Consumer Crash and Recovery ==="
        check_prerequisites

        # Determine which consumer is running
        consumer_container=$(find_consumer_container)
        if [ -z "$consumer_container" ]; then
            error "No consumer container found running"
            exit 1
        fi

        # Kill consumer
        log "Killing consumer container ($consumer_container)..."
        docker kill "$consumer_container" 2>/dev/null || warn "Consumer container not running"

        # Wait
        log "Consumer down for 30 seconds (events will accumulate in Kafka)..."
        sleep 30

        # Restart consumer
        log "Restarting consumer..."
        if [ "$consumer_container" = "cdc-consumer-k6" ]; then
            cd "$PROJECT_DIR" && docker compose -f k6/docker-compose.k6.yml up -d cdc-consumer
        else
            cd "$PROJECT_DIR" && docker compose up -d cdc-consumer
        fi

        # Wait for healthy
        wait_for_healthy "$consumer_container" 90
        log "Consumer crash/recovery scenario completed!"
        ;;

    "network-delay")
        log "=== Network Latency Injection ==="
        check_prerequisites

        log "Injecting 200ms delay (50ms jitter) to MongoDB for 120 seconds..."

        # Add delay to MongoDB in background
        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
            gaiaadm/pumba netem \
            --duration 120s \
            --tc-image gaiadocker/iproute2 \
            delay --time 200 --jitter 50 \
            cdc-mongodb &
        MONGO_PID=$!

        log "Injecting 200ms delay (50ms jitter) to Kafka for 120 seconds..."

        # Add delay to Kafka in background
        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
            gaiaadm/pumba netem \
            --duration 120s \
            --tc-image gaiadocker/iproute2 \
            delay --time 200 --jitter 50 \
            cdc-kafka &
        KAFKA_PID=$!

        log "Waiting for network delay injection to complete..."
        wait $MONGO_PID $KAFKA_PID 2>/dev/null || true

        log "Network delay scenario completed!"
        ;;

    "cpu-stress")
        log "=== CPU Stress on Consumer ==="
        check_prerequisites

        # Determine which consumer is running
        consumer_container=$(find_consumer_container)
        if [ -z "$consumer_container" ]; then
            error "No consumer container found running"
            exit 1
        fi

        log "Applying 80% CPU stress to $consumer_container for 60 seconds..."

        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
            gaiaadm/pumba stress \
            --duration 60s \
            --stressors "cpu -l 80" \
            "$consumer_container"

        log "CPU stress scenario completed!"
        ;;

    "memory-stress")
        log "=== Memory Stress on Consumer ==="
        check_prerequisites

        # Determine which consumer is running
        consumer_container=$(find_consumer_container)
        if [ -z "$consumer_container" ]; then
            error "No consumer container found running"
            exit 1
        fi

        log "Applying memory stress (256MB) to $consumer_container for 60 seconds..."

        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
            gaiaadm/pumba stress \
            --duration 60s \
            --stressors "vm 1 --vm-bytes 256M" \
            "$consumer_container"

        log "Memory stress scenario completed!"
        ;;

    "all")
        log "=== Running All Chaos Scenarios Sequentially ==="
        check_prerequisites

        for scenario in kafka-partition mongodb-failure consumer-restart network-delay; do
            info "=== Starting scenario: $scenario ==="
            "$0" "$scenario"
            log "Cooling down for 60 seconds before next scenario..."
            sleep 60
        done

        log "All chaos scenarios completed!"
        ;;

    "help"|"-h"|"--help")
        echo "CDC Chaos Engineering Test Runner"
        echo ""
        echo "Usage: $0 <scenario>"
        echo ""
        echo "Available scenarios:"
        echo "  kafka-partition   - Network partition to Kafka (30s packet loss)"
        echo "  mongodb-failure   - MongoDB crash and recovery (60s outage)"
        echo "  consumer-restart  - Consumer crash and recovery (30s outage)"
        echo "  network-delay     - Network latency injection (200ms for 120s)"
        echo "  cpu-stress        - CPU pressure on consumer (80% for 60s)"
        echo "  memory-stress     - Memory pressure on consumer (256MB for 60s)"
        echo "  all               - Run all scenarios sequentially"
        echo ""
        echo "Prerequisites:"
        echo "  - Main CDC stack running: docker compose up -d"
        echo "  - k6 stack running (optional): docker compose -f k6/docker-compose.k6.yml up -d"
        echo ""
        echo "Example:"
        echo "  # Terminal 1: Run chaos resilience k6 test"
        echo "  docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/chaos-resilience-test.js"
        echo ""
        echo "  # Terminal 2: Inject chaos during test (at ~3 minute mark)"
        echo "  $0 mongodb-failure"
        exit 0
        ;;

    *)
        error "Unknown scenario: $SCENARIO"
        echo ""
        echo "Run '$0 help' for available scenarios"
        exit 1
        ;;
esac

log "Chaos scenario '$SCENARIO' completed!"
