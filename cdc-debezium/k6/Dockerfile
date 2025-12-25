# k6/Dockerfile
# Custom k6 build with extensions for CDC pipeline load testing
FROM golang:1.21-alpine AS builder

# Install xk6
RUN go install go.k6.io/xk6/cmd/xk6@latest

# Build k6 with extensions
RUN xk6 build \
    --with github.com/grafana/xk6-sql@latest \
    --with github.com/grafana/xk6-sql-driver-postgres@latest \
    --with github.com/oleiade/xk6-mongo@latest \
    --with github.com/grafana/xk6-output-prometheus-remote@latest \
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
