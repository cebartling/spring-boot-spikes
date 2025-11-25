#!/bin/bash
#
# generate-docs.sh - Generate documentation structure
#
# Creates the documentation directory structure and placeholder files
# for the CQRS Spike project.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCS_DIR="$PROJECT_ROOT/docs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create directory structure
create_directories() {
    echo_info "Creating documentation directory structure..."

    mkdir -p "$DOCS_DIR/getting-started"
    mkdir -p "$DOCS_DIR/guides"
    mkdir -p "$DOCS_DIR/architecture"
    mkdir -p "$DOCS_DIR/troubleshooting"
    mkdir -p "$DOCS_DIR/reference"

    echo_info "Directory structure created"
}

# Check if documentation exists
check_documentation() {
    local missing=0

    echo_info "Checking documentation completeness..."

    # Getting Started
    local getting_started=(
        "prerequisites.md"
        "first-time-setup.md"
        "quick-start.md"
        "verification.md"
    )

    for file in "${getting_started[@]}"; do
        if [[ ! -f "$DOCS_DIR/getting-started/$file" ]]; then
            echo_warn "Missing: getting-started/$file"
            ((missing++))
        fi
    done

    # Guides
    local guides=(
        "daily-workflow.md"
        "secrets-management.md"
        "database-operations.md"
        "seeding-data.md"
        "debugging.md"
    )

    for file in "${guides[@]}"; do
        if [[ ! -f "$DOCS_DIR/guides/$file" ]]; then
            echo_warn "Missing: guides/$file"
            ((missing++))
        fi
    done

    # Architecture
    local architecture=(
        "overview.md"
        "infrastructure-components.md"
        "networking.md"
        "security.md"
    )

    for file in "${architecture[@]}"; do
        if [[ ! -f "$DOCS_DIR/architecture/$file" ]]; then
            echo_warn "Missing: architecture/$file"
            ((missing++))
        fi
    done

    # Troubleshooting
    local troubleshooting=(
        "common-issues.md"
        "vault-issues.md"
        "database-issues.md"
        "docker-issues.md"
    )

    for file in "${troubleshooting[@]}"; do
        if [[ ! -f "$DOCS_DIR/troubleshooting/$file" ]]; then
            echo_warn "Missing: troubleshooting/$file"
            ((missing++))
        fi
    done

    # Reference
    local reference=(
        "commands.md"
        "environment-variables.md"
        "ports-and-urls.md"
        "scripts.md"
    )

    for file in "${reference[@]}"; do
        if [[ ! -f "$DOCS_DIR/reference/$file" ]]; then
            echo_warn "Missing: reference/$file"
            ((missing++))
        fi
    done

    if [[ $missing -eq 0 ]]; then
        echo_info "All documentation files present!"
        return 0
    else
        echo_warn "$missing documentation files missing"
        return 1
    fi
}

# Generate table of contents
generate_toc() {
    echo_info "Generating table of contents..."

    cat > "$DOCS_DIR/TOC.md" << 'EOF'
# Documentation Table of Contents

## Getting Started
- [Prerequisites](getting-started/prerequisites.md)
- [First-Time Setup](getting-started/first-time-setup.md)
- [Quick Start](getting-started/quick-start.md)
- [Verification](getting-started/verification.md)

## User Guides
- [Daily Workflow](guides/daily-workflow.md)
- [Secrets Management](guides/secrets-management.md)
- [Database Operations](guides/database-operations.md)
- [Data Seeding](guides/seeding-data.md)
- [Debugging](guides/debugging.md)

## Architecture
- [Overview](architecture/overview.md)
- [Infrastructure Components](architecture/infrastructure-components.md)
- [Networking](architecture/networking.md)
- [Security](architecture/security.md)

## Troubleshooting
- [Common Issues](troubleshooting/common-issues.md)
- [Vault Issues](troubleshooting/vault-issues.md)
- [Database Issues](troubleshooting/database-issues.md)
- [Docker Issues](troubleshooting/docker-issues.md)

## Reference
- [Commands](reference/commands.md)
- [Environment Variables](reference/environment-variables.md)
- [Ports and URLs](reference/ports-and-urls.md)
- [Scripts](reference/scripts.md)
EOF

    echo_info "Table of contents generated at docs/TOC.md"
}

# Count documentation statistics
show_stats() {
    echo ""
    echo "=== Documentation Statistics ==="

    local total_files=$(find "$DOCS_DIR" -name "*.md" -type f | wc -l | tr -d ' ')
    local total_lines=$(find "$DOCS_DIR" -name "*.md" -type f -exec cat {} \; | wc -l | tr -d ' ')
    local total_words=$(find "$DOCS_DIR" -name "*.md" -type f -exec cat {} \; | wc -w | tr -d ' ')

    echo "Total Markdown files: $total_files"
    echo "Total lines: $total_lines"
    echo "Total words: $total_words"
    echo ""

    echo "Files by directory:"
    for dir in getting-started guides architecture troubleshooting reference; do
        if [[ -d "$DOCS_DIR/$dir" ]]; then
            local count=$(find "$DOCS_DIR/$dir" -name "*.md" -type f | wc -l | tr -d ' ')
            echo "  $dir/: $count files"
        fi
    done
}

# Validate links
validate_links() {
    echo_info "Validating internal links..."

    local broken=0

    while IFS= read -r file; do
        # Extract markdown links
        while IFS= read -r link; do
            # Skip external links and anchors
            if [[ "$link" =~ ^http ]] || [[ "$link" =~ ^# ]] || [[ -z "$link" ]]; then
                continue
            fi

            # Get directory of current file
            local dir=$(dirname "$file")
            local target="$dir/$link"

            # Normalize path
            target=$(cd "$dir" 2>/dev/null && realpath -m "$link" 2>/dev/null || echo "$target")

            if [[ ! -f "$target" ]]; then
                echo_warn "Broken link in $file: $link"
                ((broken++))
            fi
        done < <(grep -oE '\[.*\]\(([^)]+)\)' "$file" 2>/dev/null | grep -oE '\([^)]+\)' | tr -d '()')
    done < <(find "$DOCS_DIR" -name "*.md" -type f)

    if [[ $broken -eq 0 ]]; then
        echo_info "All internal links valid!"
    else
        echo_warn "$broken broken links found"
    fi
}

# Main execution
main() {
    echo "=== CQRS Spike Documentation Generator ==="
    echo ""

    case "${1:-check}" in
        create)
            create_directories
            ;;
        check)
            check_documentation
            ;;
        toc)
            generate_toc
            ;;
        stats)
            show_stats
            ;;
        validate)
            validate_links
            ;;
        all)
            create_directories
            check_documentation
            generate_toc
            show_stats
            validate_links
            ;;
        *)
            echo "Usage: $0 {create|check|toc|stats|validate|all}"
            echo ""
            echo "Commands:"
            echo "  create   - Create directory structure"
            echo "  check    - Check documentation completeness"
            echo "  toc      - Generate table of contents"
            echo "  stats    - Show documentation statistics"
            echo "  validate - Validate internal links"
            echo "  all      - Run all commands"
            exit 1
            ;;
    esac
}

main "$@"
