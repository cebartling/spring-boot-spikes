#!/bin/bash
# Vault secret helper
# Usage: ./scripts/vault-get.sh <path>

if [ -z "$1" ]; then
    echo "Usage: ./scripts/vault-get.sh <path>"
    echo ""
    echo "Examples:"
    echo "  ./scripts/vault-get.sh secret/cqrs-spike/database"
    echo "  ./scripts/vault-get.sh secret/cqrs-spike/api-keys"
    echo "  ./scripts/vault-get.sh secret/cqrs-spike/encryption"
    echo ""
    echo "List all secrets:"
    echo "  ./scripts/vault-get.sh list"
    exit 1
fi

# Check if container is running
if ! docker ps | grep -q "cqrs-vault"; then
    echo "Error: Vault container is not running"
    echo ""
    echo "Start the infrastructure with: make start"
    exit 1
fi

# Special case: list all secrets
if [ "$1" = "list" ]; then
    echo "Listing all secrets under secret/cqrs-spike/:"
    echo ""
    docker exec -e VAULT_ADDR=http://localhost:8200 \
                -e VAULT_TOKEN=dev-root-token \
                cqrs-vault vault kv list secret/cqrs-spike/
    exit 0
fi

# Get secret
docker exec -e VAULT_ADDR=http://localhost:8200 \
            -e VAULT_TOKEN=dev-root-token \
            cqrs-vault vault kv get "$1"
