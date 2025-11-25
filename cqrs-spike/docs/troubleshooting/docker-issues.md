# Docker Troubleshooting

This guide covers Docker and container-specific issues and solutions.

## Quick Diagnostics

```bash
# Check Docker is running
docker info

# Check Docker Compose
docker compose version

# View all containers
docker ps -a

# View container logs
docker compose logs

# Check resource usage
docker stats --no-stream
```

## Common Issues

### Docker Daemon Not Running

**Symptoms:**
```
Cannot connect to the Docker daemon
```

**Solutions:**

1. **macOS:**
   - Open Docker Desktop
   - Wait for the whale icon to be steady
   - If stuck, restart Docker Desktop

2. **Linux:**
   ```bash
   sudo systemctl start docker
   sudo systemctl status docker
   ```

3. **Windows:**
   - Open Docker Desktop
   - Check Windows Services for "Docker Desktop Service"

### Docker Compose Not Found

**Symptoms:**
```
docker-compose: command not found
```

**Solutions:**

1. **Use v2 syntax:**
   ```bash
   docker compose version  # Note: no hyphen
   ```

2. **Install standalone:**
   ```bash
   # Follow Docker documentation for your OS
   ```

### Container Won't Start

**Check exit code:**
```bash
docker inspect cqrs-postgres --format '{{.State.ExitCode}}'
```

**Common exit codes:**
| Code | Meaning |
|------|---------|
| 0 | Graceful exit |
| 1 | Application error |
| 137 | OOM killed |
| 139 | Segmentation fault |
| 143 | SIGTERM received |

**View logs:**
```bash
docker logs cqrs-postgres
docker logs --tail 100 cqrs-postgres
```

### Out of Memory (Exit Code 137)

**Symptoms:**
- Container exits with code 137
- "Killed" message in logs

**Solutions:**

1. **Increase Docker memory:**
   - Docker Desktop > Settings > Resources
   - Increase Memory to 4GB+

2. **Reduce container memory:**
   ```yaml
   # docker-compose.override.yml
   services:
     postgres:
       deploy:
         resources:
           limits:
             memory: 512M
   ```

### Port Already in Use

**Symptoms:**
```
Bind for 0.0.0.0:8080 failed: port is already allocated
```

**Find and kill process:**
```bash
# Find process
lsof -i :8080

# Kill it
kill -9 <PID>
```

**Or change port:**
```yaml
# docker-compose.override.yml
services:
  app:
    ports:
      - "8081:8080"
```

### Volume Permission Issues

**Symptoms:**
```
Permission denied
cannot create directory
```

**Solutions:**

1. **Fix permissions:**
   ```bash
   chmod -R 755 infrastructure/
   ```

2. **Remove and recreate volume:**
   ```bash
   docker compose down -v
   docker compose up -d
   ```

3. **Check ownership:**
   ```bash
   ls -la infrastructure/
   ```

### Network Issues

**Symptoms:**
- Containers can't communicate
- DNS resolution fails
- Connection refused between services

**Check network:**
```bash
# List networks
docker network ls

# Inspect network
docker network inspect cqrs-network

# Check container IPs
docker inspect cqrs-postgres --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

**Solutions:**

1. **Recreate network:**
   ```bash
   docker network rm cqrs-network
   docker compose up -d
   ```

2. **Restart Docker:**
   - Docker Desktop > Restart

### Image Pull Failures

**Symptoms:**
```
Error response from daemon: manifest not found
unable to pull image
```

**Solutions:**

1. **Check image name:**
   ```yaml
   # Verify in docker-compose.yml
   image: postgres:18-alpine  # Correct
   ```

2. **Pull manually:**
   ```bash
   docker pull postgres:18-alpine
   ```

3. **Check Docker Hub:**
   - Verify image exists at hub.docker.com

### Build Failures

**Symptoms:**
```
failed to build: error during build
```

**Solutions:**

1. **Check Dockerfile:**
   ```bash
   docker build . -f Dockerfile --no-cache
   ```

2. **Clear build cache:**
   ```bash
   docker builder prune
   ```

3. **Check disk space:**
   ```bash
   docker system df
   ```

### Stale Containers

**Symptoms:**
- Old configuration being used
- Changes not taking effect

**Solutions:**

1. **Recreate containers:**
   ```bash
   docker compose up -d --force-recreate
   ```

2. **Remove and restart:**
   ```bash
   docker compose down
   docker compose up -d
   ```

3. **Full cleanup:**
   ```bash
   docker compose down -v
   docker system prune -f
   docker compose up -d
   ```

### Healthcheck Failures

**Check healthcheck status:**
```bash
docker inspect cqrs-vault --format '{{.State.Health.Status}}'
```

**View healthcheck logs:**
```bash
docker inspect cqrs-vault --format '{{json .State.Health}}' | jq
```

**Common causes:**
- Service not ready yet (need more start_period)
- Wrong healthcheck command
- Port not open

### Slow Container Startup

**Check resource usage:**
```bash
docker stats
```

**Solutions:**

1. **Increase Docker resources:**
   - CPU: 4 cores
   - Memory: 4GB
   - Disk: Use SSD

2. **Enable BuildKit:**
   ```bash
   export DOCKER_BUILDKIT=1
   ```

3. **Pre-pull images:**
   ```bash
   docker compose pull
   ```

## Resource Management

### Check Resource Usage

```bash
# Real-time stats
docker stats

# One-time stats
docker stats --no-stream

# System-wide usage
docker system df
```

### Clean Up Resources

```bash
# Remove stopped containers
docker container prune

# Remove unused images
docker image prune

# Remove unused volumes
docker volume prune

# Remove everything unused
docker system prune -a

# Nuclear option (removes ALL)
docker system prune -a --volumes
```

### Set Resource Limits

```yaml
# docker-compose.override.yml
services:
  postgres:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M
```

## Debugging Containers

### Enter Container Shell

```bash
# Interactive shell
docker exec -it cqrs-postgres sh
docker exec -it cqrs-vault sh

# Run command
docker exec cqrs-postgres pg_isready -U cqrs_user
```

### View Container Details

```bash
# Full inspection
docker inspect cqrs-postgres

# Specific fields
docker inspect cqrs-postgres --format '{{.State.Status}}'
docker inspect cqrs-postgres --format '{{.NetworkSettings.IPAddress}}'
```

### Copy Files

```bash
# From container
docker cp cqrs-postgres:/var/lib/postgresql/data/log ./logs

# To container
docker cp ./config.yml cqrs-app:/app/config.yml
```

## Docker Compose Commands

```bash
# Start services
docker compose up -d

# Stop services
docker compose down

# Stop and remove volumes
docker compose down -v

# View logs
docker compose logs -f

# Restart service
docker compose restart postgres

# Rebuild and restart
docker compose up -d --build --force-recreate

# Scale service
docker compose up -d --scale app=3
```

## Useful Aliases

Add to `.bashrc` or `.zshrc`:

```bash
alias dc='docker compose'
alias dps='docker ps'
alias dlogs='docker compose logs -f'
alias dstats='docker stats'
alias dclean='docker system prune -f'
```

## Reset Procedures

### Soft Reset

```bash
docker compose restart
```

### Hard Reset (Keep Data)

```bash
docker compose down
docker compose up -d
```

### Full Reset (Clean State)

```bash
docker compose down -v
docker system prune -f
docker compose up -d
```

### Reset Using Makefile

```bash
make clean
make start
```

## See Also

- [Infrastructure Components](../architecture/infrastructure-components.md)
- [Networking](../architecture/networking.md)
- [Common Issues](common-issues.md)
- [docker-compose.yml](../../docker-compose.yml)
