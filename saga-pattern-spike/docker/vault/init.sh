#!/bin/sh
# Vault initialization script for saga-pattern-spike
# This script configures Vault with the necessary secrets engines and policies

set -e

echo "Waiting for Vault to be ready..."
until vault status 2>/dev/null; do
  echo "Vault not ready yet, waiting..."
  sleep 2
done

echo "Vault is ready. Starting initialization..."

# Check if already initialized by looking for our policy
if vault policy read sagapattern-policy 2>/dev/null; then
  echo "Vault already initialized, skipping..."
  exit 0
fi

# Enable KV secrets engine v2 at 'secret' path
echo "Enabling KV secrets engine v2..."
vault secrets enable -path=secret kv-v2 || echo "KV secrets engine may already be enabled"

# Enable database secrets engine
echo "Enabling database secrets engine..."
vault secrets enable database || echo "Database secrets engine may already be enabled"

# Wait for PostgreSQL to be fully ready
echo "Waiting for PostgreSQL connection..."
sleep 5

# Configure PostgreSQL connection for database secrets engine
echo "Configuring PostgreSQL connection..."
vault write database/config/postgresql \
  plugin_name=postgresql-database-plugin \
  allowed_roles="sagapattern-readwrite,sagapattern-readonly" \
  connection_url="postgresql://{{username}}:{{password}}@saga-postgres:5432/saga_db?sslmode=disable" \
  username="saga_user" \
  password="saga_password"

# Create readwrite role for dynamic credentials
echo "Creating sagapattern-readwrite role..."
vault write database/roles/sagapattern-readwrite \
  db_name=postgresql \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
  revocation_statements="DROP ROLE IF EXISTS \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Create readonly role for dynamic credentials
echo "Creating sagapattern-readonly role..."
vault write database/roles/sagapattern-readonly \
  db_name=postgresql \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
  revocation_statements="DROP ROLE IF EXISTS \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Write static secrets to KV store
echo "Writing application secrets to KV store..."
vault kv put secret/sagapattern/application \
  api.encryption-key="dev-encryption-key-32-bytes-long"

# Write dev profile secrets
vault kv put secret/sagapattern/dev \
  external.api-key="dev-api-key-for-testing"

# Write prod profile secrets (placeholder)
vault kv put secret/sagapattern/prod \
  external.api-key="prod-api-key-placeholder"

# Enable AppRole auth method
echo "Enabling AppRole authentication..."
vault auth enable approle || echo "AppRole auth may already be enabled"

# Create policy for the application
echo "Creating sagapattern-policy..."
vault policy write sagapattern-policy - <<EOF
# Allow reading KV secrets
path "secret/data/sagapattern/*" {
  capabilities = ["read", "list"]
}

path "secret/metadata/sagapattern/*" {
  capabilities = ["read", "list"]
}

# Allow reading database credentials
path "database/creds/sagapattern-readwrite" {
  capabilities = ["read"]
}

path "database/creds/sagapattern-readonly" {
  capabilities = ["read"]
}

# Allow token self-renewal
path "auth/token/renew-self" {
  capabilities = ["update"]
}
EOF

# Create AppRole for the application
echo "Creating sagapattern AppRole..."
vault write auth/approle/role/sagapattern \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0 \
  token_policies="sagapattern-policy"

# Get and display the role-id for reference
ROLE_ID=$(vault read -field=role_id auth/approle/role/sagapattern/role-id)
echo ""
echo "=============================================="
echo "Vault initialization complete!"
echo "=============================================="
echo ""
echo "Dev Root Token: dev-root-token"
echo "AppRole Role ID: $ROLE_ID"
echo ""
echo "To generate a Secret ID:"
echo "  vault write -f auth/approle/role/sagapattern/secret-id"
echo ""
echo "To test database credentials:"
echo "  vault read database/creds/sagapattern-readwrite"
echo ""
echo "To read KV secrets:"
echo "  vault kv get secret/sagapattern/application"
echo "=============================================="
