# Container Deployment Guide

This guide covers building and running the Spring Boot Resiliency Spike application as a container.

## Prerequisites

- Docker or Podman
- Docker Compose or Podman Compose
- 4GB+ available memory for all containers

## Container Architecture

The application uses a multi-stage build process:

### Stage 1: Builder
- Base: `gradle:8.14.3-jdk21-alpine`
- Purpose: Build the Spring Boot application JAR
- Optimizations: Gradle dependency caching for faster rebuilds

### Stage 2: Runtime
- Base: `eclipse-temurin:21-jre-alpine`
- Purpose: Run the application with minimal footprint
- Features:
  - Non-root user (`spring:spring`)
  - Health checks via Actuator
  - JVM tuned for containers
  - curl installed for health checks

## Building the Container Image

### Using Docker

```bash
# Build the image
docker build -f Containerfile -t resiliency-spike:latest .

# Build with specific tag
docker build -f Containerfile -t resiliency-spike:0.0.1-SNAPSHOT .

# Build without cache
docker build --no-cache -f Containerfile -t resiliency-spike:latest .
```

### Using Podman

```bash
# Build the image
podman build -f Containerfile -t resiliency-spike:latest .

# Build with specific tag
podman build -f Containerfile -t resiliency-spike:0.0.1-SNAPSHOT .
```

## Running the Container

### Option 1: Standalone Container (requires external services)

```bash
# Run with Docker
docker run -d \
  --name resiliency-spike \
  -p 8080:8080 \
  -e SPRING_R2DBC_URL=r2dbc:postgresql://host.docker.internal:5432/resiliency_spike \
  -e SPRING_R2DBC_USERNAME=resiliency_user \
  -e SPRING_R2DBC_PASSWORD=resiliency_password \
  resiliency-spike:latest

# Run with Podman
podman run -d \
  --name resiliency-spike \
  -p 8080:8080 \
  -e SPRING_R2DBC_URL=r2dbc:postgresql://host.containers.internal:5432/resiliency_spike \
  -e SPRING_R2DBC_USERNAME=resiliency_user \
  -e SPRING_R2DBC_PASSWORD=resiliency_password \
  resiliency-spike:latest
```

### Option 2: Docker Compose with All Dependencies (Recommended)

The `docker-compose.app.yml` file includes the application plus all required services:

```bash
# Start all services (builds app image automatically)
docker-compose -f docker-compose.app.yml up -d

# Start with build (force rebuild)
docker-compose -f docker-compose.app.yml up -d --build

# View logs
docker-compose -f docker-compose.app.yml logs -f

# View app logs only
docker-compose -f docker-compose.app.yml logs -f resiliency-spike-app

# Stop all services
docker-compose -f docker-compose.app.yml down

# Stop and remove volumes
docker-compose -f docker-compose.app.yml down -v
```

### Option 3: Podman Compose

```bash
# Start all services
podman-compose -f docker-compose.app.yml up -d

# View logs
podman-compose -f docker-compose.app.yml logs -f

# Stop all services
podman-compose -f docker-compose.app.yml down
```

## Accessing the Application

Once running, the application is available at:

- **Application**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs
- **Actuator Health**: http://localhost:8080/actuator/health
- **Actuator Metrics**: http://localhost:8080/actuator/metrics

Supporting services:
- **PostgreSQL**: localhost:5432
- **Vault UI**: http://localhost:8200/ui (token: `dev-root-token`)
- **Pulsar Admin**: http://localhost:8081

## Container Environment Variables

### Required Variables

```bash
# Database Configuration
SPRING_R2DBC_URL=r2dbc:postgresql://postgres:5432/resiliency_spike
SPRING_R2DBC_USERNAME=resiliency_user
SPRING_R2DBC_PASSWORD=resiliency_password

# Vault Configuration
SPRING_CLOUD_VAULT_HOST=vault
SPRING_CLOUD_VAULT_PORT=8200
SPRING_CLOUD_VAULT_SCHEME=http
SPRING_CLOUD_VAULT_TOKEN=dev-root-token

# Pulsar Configuration
SPRING_PULSAR_CLIENT_SERVICE_URL=pulsar://pulsar:6650
SPRING_PULSAR_ADMIN_ADMIN_URL=http://pulsar:8080
```

### Optional Variables

```bash
# Spring Profile
SPRING_PROFILES_ACTIVE=docker

# JVM Options (already set in Containerfile)
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC

# Logging Level
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_PINTAILCONSULTINGLLC_RESILIENCYSPIKE=DEBUG
```

## Health Checks

The container includes a built-in health check that monitors the Spring Boot Actuator health endpoint:

```bash
# Check container health status
docker ps --filter name=resiliency-spike

# Manual health check
curl http://localhost:8080/actuator/health

# Detailed health check
curl http://localhost:8080/actuator/health | jq
```

Health check configuration:
- **Interval**: 30 seconds
- **Timeout**: 3 seconds
- **Start Period**: 60 seconds (allows app startup)
- **Retries**: 3

## Container Resource Limits

### Docker Compose (docker-compose.app.yml)

Add resource limits to the app service:

```yaml
services:
  resiliency-spike-app:
    # ... other configuration ...
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 1G
        reservations:
          cpus: '1.0'
          memory: 512M
```

### Docker Run

```bash
docker run -d \
  --name resiliency-spike \
  --cpus="2.0" \
  --memory="1g" \
  -p 8080:8080 \
  resiliency-spike:latest
```

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker logs resiliency-spike-app

# Check if database is ready
docker exec resiliency-spike-postgres pg_isready -U resiliency_user

# Check Vault status
docker exec resiliency-spike-vault vault status

# Check network connectivity
docker network inspect resiliency-spike-network
```

### Application Health Issues

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Check if all dependencies are healthy
docker-compose -f docker-compose.app.yml ps

# Restart the application
docker-compose -f docker-compose.app.yml restart resiliency-spike-app
```

### Memory Issues

```bash
# Check container memory usage
docker stats resiliency-spike-app

# Adjust JVM memory (in docker-compose.app.yml or docker run)
-e JAVA_OPTS="-XX:MaxRAMPercentage=50.0"
```

### Build Issues

```bash
# Clean build with no cache
docker build --no-cache -f Containerfile -t resiliency-spike:latest .

# Check build logs
docker build -f Containerfile -t resiliency-spike:latest . 2>&1 | tee build.log
```

## Production Considerations

For production deployment, consider:

1. **Security**:
   - Don't use dev mode Vault
   - Use secrets management (Docker secrets, Kubernetes secrets)
   - Enable TLS/SSL for all connections
   - Scan images for vulnerabilities

2. **Performance**:
   - Tune JVM settings for your workload
   - Set appropriate resource limits
   - Use connection pooling effectively
   - Monitor with Actuator metrics

3. **High Availability**:
   - Run multiple application replicas
   - Use external PostgreSQL cluster
   - Use Pulsar cluster mode
   - Implement proper health checks and readiness probes

4. **Monitoring**:
   - Export metrics to Prometheus
   - Set up Grafana dashboards
   - Configure alerting
   - Centralized logging (ELK, Splunk, etc.)

## Image Information

### Image Layers

```bash
# Inspect image
docker inspect resiliency-spike:latest

# View image history
docker history resiliency-spike:latest

# Check image size
docker images resiliency-spike
```

### Image Size Optimization

The multi-stage build significantly reduces image size:
- **Builder stage**: ~800MB (not in final image)
- **Runtime image**: ~250-300MB (JRE + application)

### Image Tags

Recommended tagging strategy:

```bash
# Latest version
docker tag resiliency-spike:latest resiliency-spike:latest

# Semantic version
docker tag resiliency-spike:latest resiliency-spike:0.0.1-SNAPSHOT

# Git commit
docker tag resiliency-spike:latest resiliency-spike:$(git rev-parse --short HEAD)
```

## Cleaning Up

```bash
# Stop and remove containers
docker-compose -f docker-compose.app.yml down

# Remove containers and volumes
docker-compose -f docker-compose.app.yml down -v

# Remove images
docker rmi resiliency-spike:latest

# Clean up unused images and containers
docker system prune -a
```
