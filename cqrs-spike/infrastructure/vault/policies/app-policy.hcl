# Application policy for CQRS Spike
# This policy grants read access to application secrets

# Allow reading database credentials
path "secret/data/cqrs-spike/database" {
  capabilities = ["read"]
}

# Allow reading API keys
path "secret/data/cqrs-spike/api-keys" {
  capabilities = ["read"]
}

# Allow reading encryption keys
path "secret/data/cqrs-spike/encryption" {
  capabilities = ["read"]
}

# Allow reading service credentials
path "secret/data/cqrs-spike/services" {
  capabilities = ["read"]
}

# Allow listing secrets
path "secret/metadata/cqrs-spike/*" {
  capabilities = ["list"]
}
