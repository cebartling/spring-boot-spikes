#!/bin/sh
# SigNoz initialization script for saga-pattern-spike
# This script validates the telemetry pipeline and provides dashboard import instructions

set -e

SIGNOZ_URL="http://signoz:8080"
OTEL_COLLECTOR_URL="http://otel-collector:4318"
OTEL_COLLECTOR_HEALTH_URL="http://otel-collector:13133"
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

# Function to send test telemetry
send_test_telemetry() {
    local trace_id=$(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 32 | head -n 1)
    local span_id=$(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 16 | head -n 1)
    local start_time_sec=$(date +%s)
    local start_time_nano="${start_time_sec}000000000"
    local end_time_sec=$((start_time_sec + 1))
    local end_time_nano="${end_time_sec}000000000"

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
                        \"startTimeUnixNano\": \"$start_time_nano\",
                        \"endTimeUnixNano\": \"$end_time_nano\",
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
    local timestamp_sec=$(date +%s)
    local timestamp_nano="${timestamp_sec}000000000"

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
                                \"startTimeUnixNano\": \"$timestamp_nano\",
                                \"timeUnixNano\": \"$timestamp_nano\",
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
wait_for_service "${OTEL_COLLECTOR_HEALTH_URL}" "OTel Collector"

echo ""
echo "Step 2: Validating telemetry pipeline..."
send_test_telemetry
send_test_metrics

echo ""
echo "Step 3: Dashboard information..."
echo "  Pre-built dashboards are available at:"
echo "    ${SCRIPT_DIR}/dashboards/"
echo ""
echo "  To import the Saga Pattern dashboard manually:"
echo "    1. Open SigNoz UI: http://localhost:3301"
echo "    2. Navigate to Dashboards"
echo "    3. Click 'New Dashboard' > 'Import JSON'"
echo "    4. Upload: docker/signoz/dashboards/saga-pattern.json"

echo ""
echo "=============================================="
echo "SigNoz Initialization Complete!"
echo "=============================================="
echo ""
echo "Telemetry Pipeline: VALIDATED"
echo "  - Test span sent to OTel Collector"
echo "  - Test metrics sent to OTel Collector"
echo ""
echo "Access SigNoz UI: http://localhost:3301"
echo ""
echo "Verification:"
echo "  1. Open http://localhost:3301/traces"
echo "  2. Filter by service: signoz-init-validation"
echo "  3. You should see a 'bootstrap-validation-span' trace"
echo ""
echo "=============================================="
