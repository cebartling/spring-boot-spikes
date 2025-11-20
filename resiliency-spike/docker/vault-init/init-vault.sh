#!/bin/sh
# Vault Initialization Script for Resiliency Spike
# This script sets up Vault with application secrets for local development

set -e

echo "Configuring Vault for resiliency-spike application..."

# Enable KV v2 secrets engine at 'secret' path (already enabled in dev mode, but making it explicit)
vault secrets enable -path=secret kv-v2 2>/dev/null || echo "KV v2 secrets engine already enabled"

# Create database secrets
echo "Creating database secrets..."
vault kv put secret/resiliency-spike/database \
  username=resiliency_user \
  password=resiliency_password \
  host=postgres \
  port=5432 \
  database=resiliency_spike

# Create R2DBC connection secrets
echo "Creating R2DBC configuration..."
vault kv put secret/resiliency-spike/r2dbc \
  url="r2dbc:postgresql://postgres:5432/resiliency_spike" \
  username=resiliency_user \
  password=resiliency_password \
  initial-size=10 \
  max-size=20

# Create Pulsar configuration secrets
echo "Creating Pulsar configuration..."
vault kv put secret/resiliency-spike/pulsar \
  service-url="pulsar://pulsar:6650" \
  admin-url="http://pulsar:8080"

# Create application-level secrets
echo "Creating application secrets..."
vault kv put secret/resiliency-spike/application \
  name="resiliency-spike" \
  environment="development"

# Create a policy for the application
echo "Creating application policy..."
vault policy write resiliency-spike-policy - <<EOF
# Allow read access to application secrets
path "secret/data/resiliency-spike/*" {
  capabilities = ["read", "list"]
}

# Allow read access to database secrets
path "secret/data/resiliency-spike/database" {
  capabilities = ["read"]
}

# Allow read access to r2dbc secrets
path "secret/data/resiliency-spike/r2dbc" {
  capabilities = ["read"]
}

# Allow read access to pulsar secrets
path "secret/data/resiliency-spike/pulsar" {
  capabilities = ["read"]
}
EOF

# Create an application token with the policy
echo "Creating application token..."
vault token create -policy=resiliency-spike-policy -ttl=720h -format=json > /tmp/app-token.json || true

# Display the token (for local development only!)
if [ -f /tmp/app-token.json ]; then
  APP_TOKEN=$(cat /tmp/app-token.json | grep -o '"client_token":"[^"]*"' | cut -d'"' -f4)
  echo ""
  echo "============================================"
  echo "Vault Configuration Complete!"
  echo "============================================"
  echo "Root Token: dev-root-token"
  echo "App Token: $APP_TOKEN"
  echo ""
  echo "Use App Token in application.properties:"
  echo "spring.cloud.vault.token=$APP_TOKEN"
  echo "============================================"
  echo ""
fi

# Verify secrets were created
echo "Verifying secrets..."
vault kv get secret/resiliency-spike/database
vault kv get secret/resiliency-spike/r2dbc
vault kv get secret/resiliency-spike/pulsar
vault kv get secret/resiliency-spike/application

echo ""
echo "Vault initialization completed successfully!"
