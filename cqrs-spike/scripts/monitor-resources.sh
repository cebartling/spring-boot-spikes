#!/bin/bash
# Resource consumption monitoring script
# Target: Total memory < 2GB

# Detect docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo "========================================="
echo "Resource Consumption Monitoring"
echo "========================================="
echo ""

# Check if containers are running
if ! $DOCKER_COMPOSE ps | grep -q "Up"; then
    echo "⚠ No containers are currently running"
    echo ""
    echo "Start services with: make start"
    exit 0
fi

# Container resource usage
echo "Container Resource Usage:"
echo ""
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}" \
  $(docker ps --filter "name=cqrs-" --format "{{.Names}}") 2>/dev/null

echo ""
echo "========================================="
echo "Summary"
echo "========================================="

# Calculate total memory usage (in MB)
TOTAL_MEM=$(docker stats --no-stream --format "{{.MemUsage}}" \
  $(docker ps --filter "name=cqrs-" --format "{{.Names}}") 2>/dev/null | \
  awk '{split($1, a, /[A-Za-z]/); sum += a[1]} END {printf "%.0f", sum}')

if [ -n "$TOTAL_MEM" ] && [ "$TOTAL_MEM" != "0" ]; then
    echo "Total Memory Usage: ${TOTAL_MEM}MB"

    # Check against 2GB target
    TARGET_MB=2048
    if [ "$TOTAL_MEM" -lt "$TARGET_MB" ]; then
        echo "Status: ✓ Under target (< ${TARGET_MB}MB)"
    else
        echo "Status: ⚠ Exceeds target (>= ${TARGET_MB}MB)"
        echo ""
        echo "Optimization suggestions:"
        echo "  - Apply resource limits: see docker-compose.override.yml"
        echo "  - Reduce JVM heap: JAVA_OPTS in Dockerfile"
        echo "  - Reduce connection pool sizes"
    fi
else
    echo "Unable to calculate total memory usage"
fi

echo ""
echo "========================================="
echo "Volume Usage"
echo "========================================="
docker volume ls --filter "name=cqrs-" --format "table {{.Name}}\t{{.Driver}}"

echo ""
echo "========================================="
echo "Disk Usage"
echo "========================================="
docker system df

echo ""
