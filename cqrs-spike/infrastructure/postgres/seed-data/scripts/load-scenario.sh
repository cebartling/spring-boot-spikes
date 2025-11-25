#!/bin/bash
# infrastructure/postgres/seed-data/scripts/load-scenario.sh
# Loads a specific scenario (reset + seed in one command)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}=========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

SCENARIO="$1"

# Interactive scenario selection if not provided
if [ -z "$SCENARIO" ]; then
    print_header "Load Data Scenario"
    echo ""
    echo "Available scenarios:"
    echo ""

    i=1
    scenarios=()
    for dir in "$SCRIPT_DIR/../scenarios"/*/; do
        if [ -d "$dir" ]; then
            scenario_name=$(basename "$dir")
            scenarios+=("$scenario_name")

            # Count SQL files
            sql_count=$(ls -1 "$dir"*.sql 2>/dev/null | wc -l | tr -d ' ')

            # Show description based on scenario name
            case "$scenario_name" in
                minimal)
                    desc="Basic event streams and read models (fastest)"
                    ;;
                standard)
                    desc="Standard development data with users"
                    ;;
                full)
                    desc="Comprehensive data with analytics"
                    ;;
                performance)
                    desc="Large dataset for load testing (10k+ events)"
                    ;;
                *)
                    desc="Custom scenario"
                    ;;
            esac

            echo "  $i) $scenario_name - $desc ($sql_count scripts)"
            ((i++))
        fi
    done

    echo ""
    read -p "Select scenario (1-${#scenarios[@]}) or name: " selection

    # Check if numeric selection
    if [[ "$selection" =~ ^[0-9]+$ ]] && [ "$selection" -ge 1 ] && [ "$selection" -le "${#scenarios[@]}" ]; then
        SCENARIO="${scenarios[$((selection-1))]}"
    else
        SCENARIO="$selection"
    fi
fi

# Validate scenario exists
if [ ! -d "$SCRIPT_DIR/../scenarios/$SCENARIO" ]; then
    echo -e "${RED}Error: Scenario '$SCENARIO' not found${NC}"
    exit 1
fi

print_header "Loading Scenario: $SCENARIO"

echo ""
print_warning "This will reset all data and load the $SCENARIO scenario"
echo ""

# Check for force flag
FORCE=false
if [ "$2" == "--force" ] || [ "$2" == "-f" ]; then
    FORCE=true
fi

if [ "$FORCE" != true ]; then
    read -p "Continue? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        echo "Cancelled"
        exit 0
    fi
fi

echo ""

# Reset data first
echo "Step 1: Resetting data..."
"$SCRIPT_DIR/reset-data-only.sh" --force

echo ""

# Load scenario
echo "Step 2: Loading $SCENARIO scenario..."
"$SCRIPT_DIR/seed.sh" "$SCENARIO"

echo ""
print_header "Scenario Load Complete"

print_success "Scenario '$SCENARIO' loaded successfully!"
