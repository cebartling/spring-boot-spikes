#!/bin/bash
# Check health of observability services
# Usage: ./scripts/obs-health.sh

echo "========================================="
echo "Observability Platform Health Check"
echo "========================================="
echo ""

HEALTHY=true

# Tempo
echo -n "Tempo:      "
if curl -sf http://localhost:3200/status > /dev/null 2>&1; then
    echo "✓ Healthy"
else
    echo "✗ Not responding"
    HEALTHY=false
fi

# Loki
echo -n "Loki:       "
if curl -sf http://localhost:3100/ready > /dev/null 2>&1; then
    echo "✓ Healthy"
else
    echo "✗ Not responding"
    HEALTHY=false
fi

# Prometheus
echo -n "Prometheus: "
if curl -sf http://localhost:9090/-/healthy > /dev/null 2>&1; then
    echo "✓ Healthy"
else
    echo "✗ Not responding"
    HEALTHY=false
fi

# Grafana
echo -n "Grafana:    "
if curl -sf http://localhost:3000/api/health > /dev/null 2>&1; then
    echo "✓ Healthy"
else
    echo "✗ Not responding"
    HEALTHY=false
fi

echo ""

# Check Prometheus targets
echo "Prometheus Targets:"
TARGETS=$(curl -s http://localhost:9090/api/v1/targets 2>/dev/null)
if [ -n "$TARGETS" ]; then
    echo "$TARGETS" | jq -r '.data.activeTargets[] | "  \(.labels.job): \(.health)"' 2>/dev/null || echo "  Unable to parse targets"
else
    echo "  Unable to fetch targets"
fi

echo ""
echo "========================================="

if [ "$HEALTHY" = true ]; then
    echo "All services healthy!"
    exit 0
else
    echo "Some services are not healthy."
    exit 1
fi
