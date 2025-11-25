# Multi-stage build for faster rebuilds and smaller images
# Stage 1: Build stage
FROM gradle:9-jdk-21-and-24-alpine AS build

WORKDIR /app

# Copy Gradle files first (for dependency caching)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (this layer will be cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds - run tests in CI)
RUN gradle build -x test --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:24-alpine-3.22

WORKDIR /app

# Add non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose application and debug ports
EXPOSE 8080 5005

# JVM options for containerized environments
ENV JAVA_OPTS="-Xms256m -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0"

# Enable debug port when DEBUG env var is set
ENTRYPOINT sh -c "java $JAVA_OPTS ${DEBUG:+-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005} -jar app.jar"
