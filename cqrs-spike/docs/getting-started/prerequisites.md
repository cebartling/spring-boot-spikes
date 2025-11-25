# Prerequisites

Before you can run the CQRS Spike application, ensure you have the following software installed and configured.

## Required Software

### Docker Desktop

Docker Desktop is required for running all infrastructure services (Vault, PostgreSQL).

**Minimum Version:** 4.25.0

**Installation:**
- macOS: https://docs.docker.com/desktop/install/mac-install/
- Windows: https://docs.docker.com/desktop/install/windows-install/
- Linux: https://docs.docker.com/desktop/install/linux-install/

**Verification:**
```bash
docker --version
# Expected: Docker version 24.x or later

docker compose version
# Expected: Docker Compose version v2.x
```

### Java Development Kit

Java 21 or later is required for building and running the application.

**Recommended Options:**
- Amazon Corretto 21: https://aws.amazon.com/corretto/
- Eclipse Temurin 21: https://adoptium.net/
- Oracle JDK 21: https://www.oracle.com/java/technologies/downloads/

**Using SDKMAN (Recommended):**
```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Install Java 21
sdk install java 21-tem

# Set as default
sdk default java 21-tem
```

**Verification:**
```bash
java -version
# Expected: openjdk version "21.x.x" or similar
```

### Gradle

Gradle 8+ is required, though the project includes a Gradle wrapper.

**Using Gradle Wrapper (Recommended):**
```bash
# The wrapper is included in the project
./gradlew --version
```

**Manual Installation:**
```bash
# macOS
brew install gradle

# Linux (Debian/Ubuntu)
sudo apt install gradle

# Linux (RHEL/CentOS)
sudo yum install gradle

# Windows
choco install gradle
```

**Verification:**
```bash
./gradlew --version
# Expected: Gradle 8.x or later
```

### Git

Git is required for version control.

**Installation:**
- Download from: https://git-scm.com/downloads

**Verification:**
```bash
git --version
# Expected: git version 2.x
```

## Optional Tools

### jq (JSON Processor)

Useful for parsing JSON logs and API responses.

```bash
# macOS
brew install jq

# Linux (Debian/Ubuntu)
sudo apt install jq

# Linux (RHEL/CentOS)
sudo yum install jq

# Windows
choco install jq
```

**Verification:**
```bash
jq --version
# Expected: jq-1.x
```

### tmux (Terminal Multiplexer)

Useful for viewing multiple log streams simultaneously with the log dashboard.

```bash
# macOS
brew install tmux

# Linux (Debian/Ubuntu)
sudo apt install tmux

# Linux (RHEL/CentOS)
sudo yum install tmux
```

**Verification:**
```bash
tmux -V
# Expected: tmux 3.x
```

### psql (PostgreSQL Client)

Useful for direct database access from the host machine.

```bash
# macOS
brew install postgresql

# Linux (Debian/Ubuntu)
sudo apt install postgresql-client

# Linux (RHEL/CentOS)
sudo yum install postgresql
```

**Verification:**
```bash
psql --version
# Expected: psql (PostgreSQL) 14.x or later
```

## System Requirements

### Minimum Resources

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4+ cores |
| RAM | 4GB | 8GB+ |
| Disk | 10GB free | 20GB+ free (SSD preferred) |
| OS | macOS 11+, Windows 10+, or modern Linux | Latest stable version |

### Docker Resource Configuration

Configure Docker Desktop with adequate resources:

1. Open Docker Desktop
2. Go to **Settings** (or **Preferences** on macOS)
3. Navigate to **Resources** tab
4. Configure:
   - **CPUs:** 4 (minimum 2)
   - **Memory:** 4GB (minimum 2GB)
   - **Swap:** 1GB
   - **Disk image size:** 20GB+

5. Click **Apply & Restart**

## Network Requirements

### Required Ports

Ensure these ports are available on your machine:

| Port | Service | Purpose |
|------|---------|---------|
| 5432 | PostgreSQL | Database connections |
| 8080 | Application | REST API and health endpoints |
| 8200 | Vault | Secrets management UI and API |
| 5005 | JDWP | Java remote debugging |

### Check Port Availability

**macOS/Linux:**
```bash
# Check if ports are in use
lsof -i :5432
lsof -i :8080
lsof -i :8200
lsof -i :5005
```

**Windows:**
```bash
netstat -ano | findstr :5432
netstat -ano | findstr :8080
netstat -ano | findstr :8200
netstat -ano | findstr :5005
```

### Resolving Port Conflicts

If a port is in use:

1. **Identify the process:**
   ```bash
   lsof -i :8080  # Shows PID
   ```

2. **Stop the process:**
   ```bash
   kill -9 <PID>
   ```

3. **Or change the port in `.env`:**
   ```bash
   APP_PORT=8081
   ```

## IDE Setup (Optional)

### Visual Studio Code

**Recommended Extensions:**
- Extension Pack for Java
- Spring Boot Extension Pack
- Kotlin Language
- Docker
- YAML
- GitLens

**Configuration:**
The project includes VS Code configuration in `.vscode/` directory.

### IntelliJ IDEA

**Recommended Plugins:**
- Kotlin (bundled)
- Spring Boot
- Docker
- Database Tools and SQL

**Setup:**
1. Open project as Gradle project
2. Enable annotation processing
3. Set Java SDK to 21
4. Import run configurations from `.run/` directory

## Verification Script

Run the prerequisite verification script to check your environment:

```bash
./scripts/verify-prerequisites.sh
```

This script checks:
- Docker installation and version
- Docker Compose availability
- Java installation and version
- Gradle wrapper availability
- Port availability
- Docker resource allocation

## Troubleshooting Prerequisites

### Docker Not Starting

```bash
# Restart Docker Desktop
# macOS: Click whale icon > Restart
# Windows: Right-click system tray icon > Restart

# Check Docker service status
docker info
```

### Java Version Issues

```bash
# List available Java versions (SDKMAN)
sdk list java

# Switch Java version
sdk use java 21-tem

# Verify JAVA_HOME
echo $JAVA_HOME
```

### Port Already in Use

```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use alternative port in .env
```

## Next Steps

Once prerequisites are met, proceed to:
- [First-Time Setup](first-time-setup.md) - Configure your environment
- [Quick Start](quick-start.md) - Get running quickly
