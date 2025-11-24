#!/bin/bash

# Flyway Migration Creation Script
# Creates a new versioned migration file with proper naming convention
#
# Usage: ./scripts/create-migration.sh <description>
# Example: ./scripts/create-migration.sh add_user_table

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if description is provided
if [ -z "$1" ]; then
  echo -e "${RED}Error: Migration description required${NC}"
  echo ""
  echo "Usage: $0 <description>"
  echo "Example: $0 add_user_table"
  echo ""
  exit 1
fi

DESCRIPTION=$1
MIGRATION_DIR="src/main/resources/db/migration"

# Validate migration directory exists
if [ ! -d "$MIGRATION_DIR" ]; then
  echo -e "${RED}Error: Migration directory not found: $MIGRATION_DIR${NC}"
  exit 1
fi

# Count existing versioned migrations
VERSION_COUNT=$(find "$MIGRATION_DIR" -name "V*.sql" 2>/dev/null | wc -l | xargs)
NEXT_VERSION=$((VERSION_COUNT + 1))

# Create filename
FILENAME="V${NEXT_VERSION}__${DESCRIPTION}.sql"
FILEPATH="${MIGRATION_DIR}/${FILENAME}"

# Check if file already exists
if [ -f "$FILEPATH" ]; then
  echo -e "${RED}Error: Migration file already exists: $FILEPATH${NC}"
  exit 1
fi

# Determine default schema based on description
DEFAULT_SCHEMA="command_model"
if [[ "$DESCRIPTION" =~ event.*store ]] || [[ "$DESCRIPTION" =~ event.*stream ]]; then
  DEFAULT_SCHEMA="event_store"
elif [[ "$DESCRIPTION" =~ read.*model ]] || [[ "$DESCRIPTION" =~ view ]] || [[ "$DESCRIPTION" =~ query ]]; then
  DEFAULT_SCHEMA="read_model"
fi

# Create migration file with template
cat > "$FILEPATH" <<EOF
-- Migration: ${DESCRIPTION}
-- Version: ${NEXT_VERSION}
-- Created: $(date +"%Y-%m-%d %H:%M:%S")

SET search_path TO ${DEFAULT_SCHEMA};

-- TODO: Add your migration SQL here

-- Example: Create a table
-- CREATE TABLE my_table (
--     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
--     name VARCHAR(255) NOT NULL,
--     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
-- );

-- Example: Add an index
-- CREATE INDEX idx_my_table_name ON my_table(name);

-- Example: Add a comment
-- COMMENT ON TABLE my_table IS 'Description of the table';
EOF

echo -e "${GREEN}✓ Created migration: $FILEPATH${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Edit the file and add your migration SQL"
echo "2. Test the migration locally"
echo "3. Commit the migration file to version control"
echo ""
echo -e "${YELLOW}Tips:${NC}"
echo "- Use descriptive names (e.g., add_user_table, create_order_index)"
echo "- Keep migrations small and focused"
echo "- Never modify a migration after it has been committed"
echo "- Use snake_case for description (underscores, not spaces)"
echo ""
