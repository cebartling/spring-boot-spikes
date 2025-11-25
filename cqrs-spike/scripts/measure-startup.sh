#!/bin/bash
# Measure infrastructure startup time
# Target: < 60 seconds

set -e

# Detect docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo "========================================="
echo "Infrastructure Startup Time Measurement"
echo "========================================="
echo ""

# Clean start
echo "Stopping existing services..."
$DOCKER_COMPOSE down -v > /dev/null 2>&1 || true

echo "Starting infrastructure..."
echo ""

START_TIME=$(date +%s)

# Start infrastructure
./scripts/start-infrastructure.sh

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "========================================="
echo "Results"
echo "========================================="
echo "Infrastructure startup time: ${DURATION}s"
echo ""

# Check against target
TARGET=60
if [ $DURATION -lt $TARGET ]; then
    echo "✓ SUCCESS: Meets requirement (< ${TARGET}s)"
    echo ""
    exit 0
else
    echo "✗ WARNING: Exceeds requirement (>= ${TARGET}s)"
    echo ""
    echo "Optimization suggestions:"
    echo "  - Increase Docker memory allocation (Preferences > Resources)"
    echo "  - Use SSD for Docker storage"
    echo "  - Enable Docker BuildKit: export DOCKER_BUILDKIT=1"
    echo "  - Reduce health check intervals in docker-compose.yml"
    echo ""
    exit 1
fi
