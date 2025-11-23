# Read-only policy for monitoring and debugging
# This policy grants read-only access to all secrets

# Allow reading all secrets
path "secret/data/cqrs-spike/*" {
  capabilities = ["read"]
}

# Allow listing all secrets
path "secret/metadata/cqrs-spike/*" {
  capabilities = ["list"]
}

# Deny write, update, and delete operations
path "secret/data/cqrs-spike/*" {
  capabilities = ["read"]
}
