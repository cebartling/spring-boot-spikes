#!/bin/bash
# Performance testing script
# Tests startup time and resource consumption against targets

set -e

echo "========================================="
echo "Performance Testing"
echo "========================================="
echo ""

errors=0

# Test 1: Startup Time
echo "Test 1: Infrastructure Startup Time"
echo "-----------------------------------"
if ./scripts/measure-startup.sh; then
    echo ""
else
    echo ""
    ((errors++))
fi

# Test 2: Resource Consumption
echo ""
echo "Test 2: Resource Consumption"
echo "-----------------------------------"

# Check if containers are running
if docker ps --filter "name=cqrs-" --format "{{.Names}}" | grep -q .; then
    # Get total memory usage
    TOTAL_MEM=$(docker stats --no-stream --format "{{.MemUsage}}" \
      $(docker ps --filter "name=cqrs-" --format "{{.Names}}") 2>/dev/null | \
      awk '{split($1, a, /[A-Za-z]/); sum += a[1]} END {printf "%.0f", sum}')

    if [ -n "$TOTAL_MEM" ] && [ "$TOTAL_MEM" != "0" ]; then
        echo "Current memory usage: ${TOTAL_MEM}MB"

        TARGET_MB=2048
        if [ "$TOTAL_MEM" -lt "$TARGET_MB" ]; then
            echo "✓ PASS: Memory usage under target (< ${TARGET_MB}MB)"
        else
            echo "✗ FAIL: Memory usage exceeds target (>= ${TARGET_MB}MB)"
            ((errors++))
        fi
    else
        echo "⚠ WARNING: Unable to measure memory usage"
    fi

    echo ""
    echo "Detailed resource usage:"
    ./scripts/monitor-resources.sh
else
    echo "⚠ SKIP: No containers running"
    echo ""
    echo "Start infrastructure with: make start"
fi

echo ""
echo "========================================="
echo "Results"
echo "========================================="

if [ $errors -eq 0 ]; then
    echo "✓ All performance tests passed"
    echo ""
    exit 0
else
    echo "✗ $errors performance test(s) failed"
    echo ""
    echo "Recommendations:"
    echo "  - Review resource limits in docker-compose.yml"
    echo "  - Optimize JVM settings in Dockerfile"
    echo "  - Increase Docker Desktop resources"
    echo "  - Consider using docker-compose.override.yml for resource constraints"
    echo ""
    exit 1
fi
