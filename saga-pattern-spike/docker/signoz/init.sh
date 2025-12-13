#!/bin/sh
# SigNoz initialization script for saga-pattern-spike
# This script configures SigNoz with default dashboards and validates the telemetry pipeline

set -e

SIGNOZ_URL="http://signoz:8080"
OTEL_COLLECTOR_URL="http://otel-collector:4318"
SCRIPT_DIR="/signoz/scripts"

echo "=============================================="
echo "SigNoz Observability Platform Bootstrap"
echo "=============================================="
echo ""

# Function to wait for a service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_retries=30
    local retry_count=0

    echo "Waiting for $service_name to be ready..."
    while [ $retry_count -lt $max_retries ]; do
        if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "200"; then
            echo "$service_name is ready!"
            return 0
        fi
        retry_count=$((retry_count + 1))
        echo "  Attempt $retry_count/$max_retries - $service_name not ready yet..."
        sleep 2
    done
    echo "ERROR: $service_name failed to become ready after $max_retries attempts"
    return 1
}

# Function to check if dashboard already exists
dashboard_exists() {
    local title=$1
    local response=$(curl -s "${SIGNOZ_URL}/api/v1/dashboards")
    if echo "$response" | grep -q "\"title\":\"$title\""; then
        return 0
    fi
    return 1
}

# Function to import a dashboard
import_dashboard() {
    local file=$1
    local title=$(cat "$file" | grep -o '"title":"[^"]*"' | head -1 | cut -d'"' -f4)

    if dashboard_exists "$title"; then
        echo "  Dashboard '$title' already exists, skipping..."
        return 0
    fi

    echo "  Importing dashboard: $title"
    local response=$(curl -s -X POST "${SIGNOZ_URL}/api/v1/dashboards" \
        -H "Content-Type: application/json" \
        -d @"$file")

    if echo "$response" | grep -q "error"; then
        echo "  WARNING: Failed to import dashboard '$title': $response"
        return 1
    fi
    echo "  Dashboard '$title' imported successfully"
    return 0
}

# Function to send test telemetry
send_test_telemetry() {
    local trace_id=$(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 32 | head -n 1)
    local span_id=$(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 16 | head -n 1)
    local timestamp=$(date +%s)000000000

    echo "Sending test span to validate telemetry pipeline..."
    echo "  Trace ID: $trace_id"

    local response=$(curl -s -X POST "${OTEL_COLLECTOR_URL}/v1/traces" \
        -H "Content-Type: application/json" \
        -d "{
            \"resourceSpans\": [{
                \"resource\": {
                    \"attributes\": [{
                        \"key\": \"service.name\",
                        \"value\": {\"stringValue\": \"signoz-init-validation\"}
                    }, {
                        \"key\": \"service.namespace\",
                        \"value\": {\"stringValue\": \"saga-spike\"}
                    }]
                },
                \"scopeSpans\": [{
                    \"scope\": {
                        \"name\": \"bootstrap-validation\"
                    },
                    \"spans\": [{
                        \"traceId\": \"$trace_id\",
                        \"spanId\": \"$span_id\",
                        \"name\": \"bootstrap-validation-span\",
                        \"kind\": 1,
                        \"startTimeUnixNano\": \"$timestamp\",
                        \"endTimeUnixNano\": \"${timestamp}1000000\",
                        \"attributes\": [{
                            \"key\": \"validation.type\",
                            \"value\": {\"stringValue\": \"bootstrap\"}
                        }],
                        \"status\": {
                            \"code\": 1
                        }
                    }]
                }]
            }]
        }")

    if [ -z "$response" ] || echo "$response" | grep -q "partialSuccess"; then
        echo "  Test span sent successfully"
        return 0
    fi
    echo "  WARNING: Test span may have failed: $response"
    return 1
}

# Function to send test metrics
send_test_metrics() {
    local timestamp=$(date +%s)000000000

    echo "Sending test metrics to validate metrics pipeline..."

    local response=$(curl -s -X POST "${OTEL_COLLECTOR_URL}/v1/metrics" \
        -H "Content-Type: application/json" \
        -d "{
            \"resourceMetrics\": [{
                \"resource\": {
                    \"attributes\": [{
                        \"key\": \"service.name\",
                        \"value\": {\"stringValue\": \"signoz-init-validation\"}
                    }]
                },
                \"scopeMetrics\": [{
                    \"scope\": {
                        \"name\": \"bootstrap-validation\"
                    },
                    \"metrics\": [{
                        \"name\": \"bootstrap.validation.counter\",
                        \"description\": \"Bootstrap validation counter\",
                        \"sum\": {
                            \"dataPoints\": [{
                                \"asInt\": \"1\",
                                \"startTimeUnixNano\": \"$timestamp\",
                                \"timeUnixNano\": \"$timestamp\",
                                \"attributes\": [{
                                    \"key\": \"validation.type\",
                                    \"value\": {\"stringValue\": \"bootstrap\"}
                                }]
                            }],
                            \"aggregationTemporality\": 2,
                            \"isMonotonic\": true
                        }
                    }]
                }]
            }]
        }")

    if [ -z "$response" ] || echo "$response" | grep -q "partialSuccess"; then
        echo "  Test metrics sent successfully"
        return 0
    fi
    echo "  WARNING: Test metrics may have failed: $response"
    return 1
}

# Main initialization sequence
echo "Step 1: Waiting for services..."
wait_for_service "${SIGNOZ_URL}/api/v1/health" "SigNoz"
wait_for_service "${OTEL_COLLECTOR_URL}/v1/traces" "OTel Collector" || true

echo ""
echo "Step 2: Importing dashboards..."
DASHBOARD_DIR="${SCRIPT_DIR}/dashboards"
if [ -d "$DASHBOARD_DIR" ]; then
    dashboard_count=0
    for dashboard_file in "$DASHBOARD_DIR"/*.json; do
        if [ -f "$dashboard_file" ]; then
            import_dashboard "$dashboard_file" && dashboard_count=$((dashboard_count + 1))
        fi
    done
    echo "  Processed $dashboard_count dashboard(s)"
else
    echo "  No dashboards directory found, skipping..."
fi

echo ""
echo "Step 3: Validating telemetry pipeline..."
send_test_telemetry
send_test_metrics

echo ""
echo "=============================================="
echo "SigNoz Initialization Complete!"
echo "=============================================="
echo ""
echo "Access SigNoz UI: http://localhost:3301"
echo ""
echo "Quick verification commands:"
echo "  # View traces"
echo "  open http://localhost:3301/traces"
echo ""
echo "  # View dashboards"
echo "  open http://localhost:3301/dashboards"
echo ""
echo "  # Check OTel Collector health"
echo "  curl http://localhost:13133/"
echo ""
echo "=============================================="
