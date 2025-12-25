# k6/Dockerfile
# Custom k6 build with extensions for CDC pipeline load testing
FROM golang:1.23-alpine AS builder

# Install build dependencies (git required for Go modules)
RUN apk add --no-cache git

# Install xk6 (using v0.13.0 for compatibility with Go 1.23)
RUN go install go.k6.io/xk6/cmd/xk6@v0.13.0

# Build k6 with extensions
# - xk6-sql v0.4.1: built-in PostgreSQL support (pre-modularization)
# - xk6-mongo v0.1.3: MongoDB driver (Go 1.23 compatible)
# - xk6-output-prometheus-remote v0.5.0: Prometheus remote write support
RUN xk6 build \
    --with github.com/grafana/xk6-sql@v0.4.1 \
    --with github.com/GhMartingit/xk6-mongo@v0.1.3 \
    --with github.com/grafana/xk6-output-prometheus-remote@v0.5.0 \
    --output /k6

FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache ca-certificates

# Copy k6 binary
COPY --from=builder /k6 /usr/bin/k6

# Create non-root user
RUN adduser -D -u 1000 k6user
USER k6user

WORKDIR /scripts

ENTRYPOINT ["k6"]
