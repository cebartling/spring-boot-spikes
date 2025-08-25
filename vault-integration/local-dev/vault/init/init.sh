#!/bin/sh
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"
OUT_DIR="/testing/vault/file"
INIT_JSON="$OUT_DIR/init.json"
ROOT_TOKEN_FILE="$OUT_DIR/root-token.txt"
UNSEAL_KEYS_FILE="$OUT_DIR/unseal-keys.txt"

wait_for_vault() {
  echo "Waiting for Vault to report sealed/initialized state..."
  until curl -fsS "$VAULT_ADDR/v1/sys/health" >/dev/null 2>&1; do
    sleep 1
  done
}

init_if_needed() {
  if curl -fsS "$VAULT_ADDR/v1/sys/health" | grep -q '"initialized":false' 2>/dev/null; then
    echo "Initializing Vault..."
    curl -fsS --request PUT --data '{"secret_shares":1,"secret_threshold":1}' \
      "$VAULT_ADDR/v1/sys/init" > "$INIT_JSON"

    UNSEAL_KEY=$(jq -r '.keys_base64[0]' "$INIT_JSON")
    ROOT_TOKEN=$(jq -r '.root_token' "$INIT_JSON")

    echo "$ROOT_TOKEN" > "$ROOT_TOKEN_FILE"
    echo "$UNSEAL_KEY" > "$UNSEAL_KEYS_FILE"

    echo "Unsealing Vault..."
    curl -fsS --request PUT --data "{\"key\":\"$UNSEAL_KEY\"}" \
      "$VAULT_ADDR/v1/sys/unseal" >/dev/null
  else
    echo "Vault already initialized."
    # If sealed, unseal with existing key
    if curl -fsS "$VAULT_ADDR/v1/sys/seal-status" | grep -q '"sealed":true'; then
      UNSEAL_KEY=$(cat "$UNSEAL_KEYS_FILE")
      echo "Unsealing Vault with existing key..."
      curl -fsS --request PUT --data "{\"key\":\"$UNSEAL_KEY\"}" \
        "$VAULT_ADDR/v1/sys/unseal" >/dev/null
    fi
    ROOT_TOKEN=$(cat "$ROOT_TOKEN_FILE")
  fi
}

login_root() {
  export VAULT_TOKEN="$ROOT_TOKEN"
}

enable_kv_and_seed() {
  echo "Enabling KV v2 at 'secret/' (idempotent)..."
  curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    --data '{"type":"kv","options":{"version":"2"}}' \
    "$VAULT_ADDR/v1/sys/mounts/secret" || true

  echo "Writing example secret at secret/data/spring/application..."
  curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    --data '{"data":{"example.username":"demo","example.password":"s3cr3t"}}' \
    "$VAULT_ADDR/v1/secret/data/spring/application" >/dev/null
}

apply_policy() {
  echo "Applying policy 'spring-app'..."
  curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    --request PUT \
    --data @"//vault/policies/spring-app.hcl" \
    "$VAULT_ADDR/v1/sys/policies/acl/spring-app?pretty=1" >/dev/null || {
      # Fallback to CLI if API payload not accepted
      vault login "$VAULT_TOKEN" >/dev/null
      vault policy write spring-app /vault/policies/spring-app.hcl
    }
}

create_approle() {
  echo "Enabling AppRole auth (idempotent)..."
  curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    "$VAULT_ADDR/v1/sys/auth/approle" \
    --data '{"type":"approle"}' || true

  echo "Creating AppRole 'spring-app' bound to policy 'spring-app'..."
  curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    --data '{"token_ttl":"24h","token_max_ttl":"72h","policies":["spring-app"]}' \
    "$VAULT_ADDR/v1/auth/approle/role/spring-app" >/dev/null

  ROLE_ID=$(curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    "$VAULT_ADDR/v1/auth/approle/role/spring-app/role-id" | jq -r '.data.role_id')

  SECRET_ID=$(curl -fsS \
    --header "X-Vault-Token: $VAULT_TOKEN" \
    --request POST \
    "$VAULT_ADDR/v1/auth/approle/role/spring-app/secret-id" | jq -r '.data.secret_id')

  echo "ROLE_ID=$ROLE_ID"   | tee "$OUT_DIR/spring-app-role-id.txt"
  echo "SECRET_ID=$SECRET_ID" | tee "$OUT_DIR/spring-app-secret-id.txt"

  echo
  echo "➡ AppRole created:"  echo "   role_id:   $ROLE_ID"
  echo "   secret_id: $SECRET_ID"
  echo
  echo "Update your .env with these for Spring Boot AppRole auth."
}

main() {
  wait_for_vault
  init_if_needed  login_root  enable_kv_and_seed  apply_policy  create_approle  echo  echo "Root token (for UI): $(cat "$ROOT_TOKEN_FILE")"
  echo "Visit UI at ${VAULT_ADDR/\/vault/http://localhost}:8200"
}

main