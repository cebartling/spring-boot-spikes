# Networking Architecture

This document describes the network configuration for the CQRS Spike infrastructure.

## Network Topology

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Host Machine                               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    Docker Network: cqrs-network                 │ │
│  │                    Subnet: 172.28.0.0/16                        │ │
│  │                                                                  │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │ │
│  │  │    vault     │  │   postgres   │  │     app      │          │ │
│  │  │  172.28.x.x  │  │  172.28.x.x  │  │  172.28.x.x  │          │ │
│  │  │    :8200     │  │    :5432     │  │    :8080     │          │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │ │
│  │                                                                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                          │                │                │         │
│                    Port: 8200       Port: 5432       Port: 8080     │
│                                                                      │
└──────────────────────────┼────────────────┼────────────────┼────────┘
                           │                │                │
                      localhost:8200   localhost:5432   localhost:8080
```

## Docker Network Configuration

### Network Definition

```yaml
networks:
  cqrs-network:
    name: cqrs-network
    driver: bridge
    ipam:
      driver: default
      config:
        - subnet: 172.28.0.0/16
    driver_opts:
      com.docker.network.bridge.name: br-cqrs
      com.docker.network.bridge.enable_icc: "true"
      com.docker.network.bridge.enable_ip_masquerade: "true"
```

### Network Properties

| Property | Value | Purpose |
|----------|-------|---------|
| Name | `cqrs-network` | Network identifier |
| Driver | `bridge` | Isolated network bridge |
| Subnet | `172.28.0.0/16` | IP address range |
| ICC | `true` | Inter-container communication |
| IP Masquerade | `true` | NAT for external access |

## Service Connectivity

### Internal Communication

Services communicate using container names as hostnames:

| From | To | URL |
|------|-----|-----|
| Application | Vault | `http://vault:8200` |
| Application | PostgreSQL | `postgres:5432` |
| Vault-init | Vault | `http://vault:8200` |

### External Access (Host)

Services are accessible from the host machine:

| Service | Host URL | Container URL |
|---------|----------|---------------|
| Vault | `http://localhost:8200` | `http://vault:8200` |
| PostgreSQL | `localhost:5432` | `postgres:5432` |
| Application | `http://localhost:8080` | `http://app:8080` |

## Port Mappings

### Exposed Ports

| Service | Container Port | Host Port | Protocol |
|---------|----------------|-----------|----------|
| Vault | 8200 | 8200 | HTTP |
| PostgreSQL | 5432 | 5432 | TCP |
| Application | 8080 | 8080 | HTTP |
| Debug | 5005 | 5005 | TCP |

### Port Configuration

In `docker-compose.yml`:

```yaml
services:
  vault:
    ports:
      - "8200:8200"

  postgres:
    ports:
      - "5432:5432"

  app:
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
```

## DNS Resolution

### Container DNS

Docker provides automatic DNS resolution within the network:

```bash
# From any container
ping vault           # Resolves to Vault container
ping postgres        # Resolves to PostgreSQL container
```

### Testing DNS

```bash
# Test from application container
docker exec cqrs-app nslookup vault
docker exec cqrs-app nslookup postgres
```

## Network Troubleshooting

### Inspect Network

```bash
# List networks
docker network ls

# Inspect CQRS network
docker network inspect cqrs-network
```

### Check Container IPs

```bash
# Get container IP
docker inspect cqrs-vault --format '{{.NetworkSettings.Networks.cqrs-network.IPAddress}}'
docker inspect cqrs-postgres --format '{{.NetworkSettings.Networks.cqrs-network.IPAddress}}'
```

### Test Connectivity

```bash
# From host to services
curl http://localhost:8200/v1/sys/health
nc -zv localhost 5432

# Between containers
docker exec cqrs-app curl http://vault:8200/v1/sys/health
docker exec cqrs-app nc -zv postgres 5432
```

### Common Network Issues

#### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change port in .env
APP_PORT=8081
```

#### Container Cannot Reach Another

```bash
# Check both containers on same network
docker network inspect cqrs-network

# Check container is running
docker ps | grep <container-name>

# Check healthcheck status
docker inspect <container-name> --format '{{.State.Health.Status}}'
```

#### DNS Resolution Failure

```bash
# Restart Docker daemon
# macOS: Docker Desktop > Restart

# Recreate network
docker network rm cqrs-network
docker compose up -d
```

## Security Considerations

### Network Isolation

The bridge network provides:
- Isolation from host network
- Isolation from other Docker networks
- Controlled port exposure

### Port Exposure

Only expose necessary ports:
- Development: All services exposed
- Production: Only application port

### Firewall Recommendations

For development workstations:
- Allow inbound on ports 5432, 8080, 8200
- Restrict to localhost only

## Network Configuration Files

### docker-compose.yml

```yaml
networks:
  cqrs-network:
    name: cqrs-network
    driver: bridge
    ipam:
      driver: default
      config:
        - subnet: 172.28.0.0/16
```

### Environment Variables

Network-related configuration in `.env`:

```bash
# Default ports
VAULT_PORT=8200
POSTGRES_PORT=5432
APP_PORT=8080
DEBUG_PORT=5005
```

## Monitoring Network

### Docker Stats

```bash
# Network I/O per container
docker stats --format "table {{.Name}}\t{{.NetIO}}"
```

### Container Network Info

```bash
# Detailed network info
docker inspect cqrs-vault -f '{{json .NetworkSettings.Networks}}' | jq
```

## See Also

- [Infrastructure Components](infrastructure-components.md) - Service details
- [Architecture Overview](overview.md) - System architecture
- [Security](security.md) - Security model
- [Troubleshooting Docker](../troubleshooting/docker-issues.md) - Docker problems
