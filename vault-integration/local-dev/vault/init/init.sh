#!/bin/sh
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"
OUT_DIR="/vault/file"
INIT_JSON="$OUT_DIR/init.json"
ROOT_TOKEN_FILE="$OUT_DIR/root-token.txt"
UNSEAL_KEYS_FILE="$OUT_DIR/unseal-keys.txt"

KNOWN_ROOT_TOKEN="${KNOWN_ROOT_TOKEN:-}"
KNOWN_UNSEAL_KEY="${KNOWN_UNSEAL_KEY:-}"

wait_for_vault() {
  echo "Waiting for Vault HTTP endpoint (up to 60s)..."
  timeout 60 sh -c '
    until curl -sS -o /dev/null "$VAULT_ADDR/v1/sys/health?standbyok=true&sealedcode=200&uninitcode=200"; do
      sleep 1
    done
  ' || { echo "Vault did not become reachable within 60s"; exit 1; }
  echo "Vault HTTP endpoint is up."
}

init_if_needed() {
  # Query health once and reuse
  HEALTH_JSON=$(curl -fsS "$VAULT_ADDR/v1/sys/health" || true)
  INIT=$(printf "%s" "$HEALTH_JSON" | jq -r 'try .initialized // empty')
  SEALED=$(printf "%s" "$HEALTH_JSON" | jq -r 'try .sealed // empty')

  if [ "$INIT" = "false" ]; then
    echo "Initializing Vault..."
    curl -fsS --request PUT --data '{"secret_shares":1,"secret_threshold":1}' \
      "$VAULT_ADDR/v1/sys/init" > "$INIT_JSON"

    UNSEAL_KEY=$(jq -r '.keys_base64[0]' "$INIT_JSON")
    ROOT_TOKEN=$(jq -r '.root_token' "$INIT_JSON")

    printf "%s\n" "$ROOT_TOKEN" > "$ROOT_TOKEN_FILE"
    printf "%s\n" "$UNSEAL_KEY" > "$UNSEAL_KEYS_FILE"

    echo "Unsealing Vault..."
    curl -fsS --request PUT --data "{\"key\":\"$UNSEAL_KEY\"}" \
      "$VAULT_ADDR/v1/sys/unseal" >/dev/null
  else
    echo "Vault already initialized."

    # Handle sealed state
    if [ "$SEALED" = "true" ]; then
      if [ -f "$UNSEAL_KEYS_FILE" ]; then
        UNSEAL_KEY=$(cat "$UNSEAL_KEYS_FILE")
      elif [ -n "$KNOWN_UNSEAL_KEY" ]; then
        UNSEAL_KEY="$KNOWN_UNSEAL_KEY"
        echo "Using KNOWN_UNSEAL_KEY from environment to unseal."
      else
        echo "ERROR: Vault is sealed and no unseal key file found at $UNSEAL_KEYS_FILE."
        echo "Provide KNOWN_UNSEAL_KEY env var, or restore $UNSEAL_KEYS_FILE, or re-create the data volume for a fresh dev init."
        exit 1
      fi
      echo "Unsealing Vault..."
      curl -fsS --request PUT --data "{\"key\":\"$UNSEAL_KEY\"}" \
        "$VAULT_ADDR/v1/sys/unseal" >/dev/null
    fi

    # Determine root token source
    if [ -f "$ROOT_TOKEN_FILE" ]; then
      ROOT_TOKEN=$(cat "$ROOT_TOKEN_FILE")
    elif [ -n "$KNOWN_ROOT_TOKEN" ]; then
      ROOT_TOKEN="$KNOWN_ROOT_TOKEN"
      echo "Using KNOWN_ROOT_TOKEN from environment."
    else
      echo "ERROR: Root token file not found at $ROOT_TOKEN_FILE and KNOWN_ROOT_TOKEN not provided."
      echo "To proceed: set KNOWN_ROOT_TOKEN with a valid token, or remove the Vault data volume to reinitialize in dev."
      exit 1
    fi
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
    --data @"/vault/policies/spring-app.hcl" \
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
  echo "➡ AppRole created:"
  echo "   role_id:   $ROLE_ID"
  echo "   secret_id: $SECRET_ID"
  echo
  echo "Update your .env with these for Spring Boot AppRole auth."
}

main() {
  wait_for_vault
  init_if_needed
  login_root
  enable_kv_and_seed
  apply_policy
  create_approle
  echo
  if [ -f "$ROOT_TOKEN_FILE" ]; then
    echo "Root token (for UI): $(cat "$ROOT_TOKEN_FILE")"
  elif [ -n "$KNOWN_ROOT_TOKEN" ]; then
    echo "Root token (from KNOWN_ROOT_TOKEN env)"
  fi
  echo "Visit UI at ${VAULT_ADDR/\/vault/http://localhost}:8200"
}

main