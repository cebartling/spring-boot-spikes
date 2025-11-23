#!/bin/sh

set -e

echo "Waiting for Vault to be ready..."
until vault status > /dev/null 2>&1; do
  echo "Vault is not ready yet, waiting..."
  sleep 2
done

echo "Vault is ready! Initializing secrets..."

# Enable KV v2 secrets engine
echo "Enabling KV v2 secrets engine..."
vault secrets enable -version=2 -path=secret kv 2>/dev/null || echo "KV secrets engine already enabled"

# Store database credentials
echo "Storing database credentials..."
vault kv put secret/cqrs-spike/database \
  username="cqrs_user" \
  password="local_dev_password" \
  url="jdbc:postgresql://postgres:5432/cqrs_db" \
  driver-class-name="org.postgresql.Driver"

# Store API keys (examples)
echo "Storing API keys..."
vault kv put secret/cqrs-spike/api-keys \
  external-service-key="dev-key-12345" \
  analytics-key="dev-analytics-key"

# Store encryption keys
echo "Storing encryption keys..."
vault kv put secret/cqrs-spike/encryption \
  jwt-secret="dev-jwt-secret-change-in-production" \
  aes-key="dev-aes-key-32-bytes-long-here"

# Store service credentials
echo "Storing service credentials..."
vault kv put secret/cqrs-spike/services \
  smtp-username="user@example.com" \
  smtp-password="password" \
  redis-password="redis-pass"

echo "Vault initialization complete!"
echo "Available secret paths:"
echo "  - secret/cqrs-spike/database"
echo "  - secret/cqrs-spike/api-keys"
echo "  - secret/cqrs-spike/encryption"
echo "  - secret/cqrs-spike/services"
