#!/bin/bash
# Query logs from Loki
# Usage: ./scripts/query-logs.sh '{service="app"}' [limit]

QUERY="${1:-{container=~\"cqrs.*\"}}"
LIMIT="${2:-100}"

echo "Querying Loki for: $QUERY (limit: $LIMIT)"
echo ""

# Check if Loki is running
if ! curl -sf http://localhost:3100/ready > /dev/null 2>&1; then
    echo "Error: Loki is not running. Start the observability platform first."
    echo "Run: make obs-start"
    exit 1
fi

# Query Loki
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
    --data-urlencode "query=$QUERY" \
    --data-urlencode "limit=$LIMIT" \
    | jq -r '.data.result[].values[][1]' 2>/dev/null || echo "No results found or invalid query"
