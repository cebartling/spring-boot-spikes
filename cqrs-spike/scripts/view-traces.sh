#!/bin/bash
# View traces in Grafana
# Usage: ./scripts/view-traces.sh <trace-id>

TRACE_ID="$1"

if [ -z "$TRACE_ID" ]; then
    echo "Usage: ./scripts/view-traces.sh <trace-id>"
    echo ""
    echo "To find trace IDs, check your application logs for entries like:"
    echo "  [trace_id=abc123,span_id=xyz789]"
    exit 1
fi

# Check if Grafana is running
if ! curl -sf http://localhost:3000/api/health > /dev/null 2>&1; then
    echo "Error: Grafana is not running. Start the observability platform first."
    echo "Run: make obs-start"
    exit 1
fi

echo "Opening trace in Grafana..."
echo ""

# Construct Grafana URL for trace view
GRAFANA_URL="http://localhost:3000/explore?left=%7B%22queries%22:%5B%7B%22datasource%22:%7B%22type%22:%22tempo%22,%22uid%22:%22tempo%22%7D,%22query%22:%22$TRACE_ID%22,%22queryType%22:%22traceId%22%7D%5D%7D"

echo "Trace URL: $GRAFANA_URL"
echo ""

# Try to open in browser (macOS)
if command -v open &> /dev/null; then
    open "$GRAFANA_URL"
elif command -v xdg-open &> /dev/null; then
    xdg-open "$GRAFANA_URL"
else
    echo "Please open the URL above in your browser."
fi
