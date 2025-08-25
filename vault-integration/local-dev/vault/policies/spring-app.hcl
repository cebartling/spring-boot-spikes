# Read-only access to the application's secret paths
path "secret/data/spring/*" {
  capabilities = ["read"]
}

# Allow listing if you use per-profile contexts
path "secret/metadata/spring/*" {
  capabilities = ["list"]
}
