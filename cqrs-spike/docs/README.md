# CQRS Spike - Documentation

Welcome to the CQRS Spike documentation. This comprehensive guide covers all aspects of the local development infrastructure, setup, and operation.

## Quick Links

- [Getting Started](getting-started/quick-start.md) - Get up and running quickly
- [Architecture Overview](architecture/overview.md) - Understand the system design
- [Daily Workflow](guides/daily-workflow.md) - Common development tasks
- [Troubleshooting](troubleshooting/common-issues.md) - Solutions to common problems

## Documentation Structure

### Getting Started

Step-by-step guides for new developers:

- [Prerequisites](getting-started/prerequisites.md) - Required software and system requirements
- [First-Time Setup](getting-started/first-time-setup.md) - Initial environment configuration
- [Quick Start](getting-started/quick-start.md) - Get running in minutes
- [Verification](getting-started/verification.md) - Verify your setup is working

### User Guides

Detailed guides for common tasks:

- [Daily Workflow](guides/daily-workflow.md) - Day-to-day development patterns
- [Secrets Management](guides/secrets-management.md) - Working with HashiCorp Vault
- [Database Operations](guides/database-operations.md) - PostgreSQL management
- [Data Seeding](guides/seeding-data.md) - Loading test data
- [Debugging](guides/debugging.md) - Debugging techniques and tools

### Architecture

Technical deep-dives:

- [Overview](architecture/overview.md) - High-level architecture
- [Infrastructure Components](architecture/infrastructure-components.md) - Service descriptions
- [Networking](architecture/networking.md) - Docker network configuration
- [Security](architecture/security.md) - Security model and practices

### Troubleshooting

Problem-solving guides:

- [Common Issues](troubleshooting/common-issues.md) - Frequently encountered problems
- [Vault Issues](troubleshooting/vault-issues.md) - Vault-specific troubleshooting
- [Database Issues](troubleshooting/database-issues.md) - PostgreSQL troubleshooting
- [Docker Issues](troubleshooting/docker-issues.md) - Container and Docker problems

### Reference

Quick reference materials:

- [Commands](reference/commands.md) - Complete command reference
- [Environment Variables](reference/environment-variables.md) - Configuration options
- [Ports and URLs](reference/ports-and-urls.md) - Service endpoints
- [Scripts](reference/scripts.md) - Helper script documentation

## Quick Start

```bash
# Clone the repository
git clone <repository-url>
cd cqrs-spike

# Copy environment configuration
cp .env.example .env

# Start infrastructure
make start

# Build and verify
make build
make health
```

## Service Access

| Service | URL | Credentials |
|---------|-----|-------------|
| Application | http://localhost:8080 | N/A |
| Vault UI | http://localhost:8200/ui | Token: `dev-root-token` |
| PostgreSQL | localhost:5432 | User: `cqrs_user`, Password: `local_dev_password` |
| Health Check | http://localhost:8080/actuator/health | N/A |
| Debug Port | localhost:5005 | JDWP |

## Common Commands

| Command | Description |
|---------|-------------|
| `make start` | Start all infrastructure services |
| `make stop` | Stop all services |
| `make health` | Check service health |
| `make logs` | View all logs |
| `make build` | Build application |
| `make test` | Run tests |

## Need Help?

1. Check the [Troubleshooting Guide](troubleshooting/common-issues.md)
2. Review application logs: `./scripts/logs.sh app`
3. Verify infrastructure health: `make health`
4. Consult the [Architecture Overview](architecture/overview.md) for context

## Additional Resources

- [Main README](../README.md) - Project overview
- [CLAUDE.md](../CLAUDE.md) - AI development guide
- [CONSTITUTION.md](../documentation/CONSTITUTION.md) - Coding standards
- [VAULT_SETUP.md](../VAULT_SETUP.md) - Vault configuration details
- [Feature Plans](../documentation/plans) - Implementation roadmaps
