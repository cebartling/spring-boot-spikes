#!/bin/bash
# Database query helper
# Usage: ./scripts/db-query.sh <query>

if [ -z "$1" ]; then
    echo "Usage: ./scripts/db-query.sh <query>"
    echo ""
    echo "Examples:"
    echo "  ./scripts/db-query.sh \"SELECT * FROM event_store.event_stream LIMIT 10\""
    echo "  ./scripts/db-query.sh \"SELECT version FROM flyway_schema_history\""
    echo "  ./scripts/db-query.sh \"\\dt event_store.*\""
    echo ""
    echo "Common queries:"
    echo "  List schemas:        \"\\dn\""
    echo "  List tables:         \"\\dt\""
    echo "  List event tables:   \"\\dt event_store.*\""
    echo "  List read tables:    \"\\dt read_model.*\""
    echo "  List command tables: \"\\dt command_model.*\""
    echo "  Table structure:     \"\\d+ table_name\""
    exit 1
fi

# Check if container is running
if ! docker ps | grep -q "cqrs-postgres"; then
    echo "Error: PostgreSQL container is not running"
    echo ""
    echo "Start the infrastructure with: make start"
    exit 1
fi

# Execute query
docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db -c "$1"
