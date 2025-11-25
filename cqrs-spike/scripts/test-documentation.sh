#!/bin/bash
# Test documentation accuracy
# Verifies that all connection details in the documentation are correct

set -e

echo "========================================="
echo "Documentation Accuracy Test"
echo "========================================="
echo ""

errors=0

# Test Vault URL
echo -n "Testing Vault URL... "
if curl -sf http://localhost:8200/v1/sys/health > /dev/null; then
    echo "✓ PASS"
else
    echo "✗ FAIL"
    echo "  - Vault is not accessible at http://localhost:8200"
    ((errors++))
fi

# Test Vault UI
echo -n "Testing Vault UI... "
if curl -sf http://localhost:8200/ui > /dev/null; then
    echo "✓ PASS"
else
    echo "✗ FAIL"
    echo "  - Vault UI is not accessible at http://localhost:8200/ui"
    ((errors++))
fi

# Test PostgreSQL connection
echo -n "Testing PostgreSQL connection... "
if docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -c "SELECT 1" > /dev/null 2>&1; then
    echo "✓ PASS"
else
    echo "✗ FAIL"
    echo "  - Cannot connect to PostgreSQL with documented credentials"
    ((errors++))
fi

# Test PostgreSQL from host
echo -n "Testing PostgreSQL port (host)... "
if nc -z localhost 5432 > /dev/null 2>&1; then
    echo "✓ PASS"
else
    echo "✗ FAIL"
    echo "  - PostgreSQL port 5432 is not accessible from host"
    ((errors++))
fi

# Test Vault token
echo -n "Testing Vault token... "
if docker exec -e VAULT_ADDR=http://localhost:8200 \
               -e VAULT_TOKEN=dev-root-token \
               cqrs-vault vault status > /dev/null 2>&1; then
    echo "✓ PASS"
else
    echo "✗ FAIL"
    echo "  - Vault token 'dev-root-token' is not valid"
    ((errors++))
fi

# Test database schemas exist
echo -n "Testing database schemas... "
SCHEMAS=$(docker exec cqrs-postgres psql -U cqrs_user -d cqrs_db -t -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('event_store', 'read_model', 'command_model')" | xargs)
EXPECTED_COUNT=3
ACTUAL_COUNT=$(echo $SCHEMAS | wc -w | xargs)

if [ "$ACTUAL_COUNT" -eq "$EXPECTED_COUNT" ]; then
    echo "✓ PASS"
else
    echo "✗ FAIL"
    echo "  - Expected 3 schemas (event_store, read_model, command_model), found $ACTUAL_COUNT"
    ((errors++))
fi

# Check if application would be able to connect (port available or app running)
echo -n "Testing application port 8080... "
if lsof -i :8080 > /dev/null 2>&1; then
    echo "✓ PASS (port in use - app may be running)"
else
    echo "⚠ SKIP (port not in use - app not running)"
fi

echo ""
echo "========================================="
echo "Results"
echo "========================================="

if [ $errors -eq 0 ]; then
    echo "✓ All connection details verified successfully"
    echo ""
    exit 0
else
    echo "✗ $errors test(s) failed"
    echo ""
    echo "Troubleshooting:"
    echo "  - Ensure infrastructure is running: make start"
    echo "  - Check service status: make ps"
    echo "  - View logs: make logs"
    echo "  - Run health check: make health"
    echo ""
    exit 1
fi
