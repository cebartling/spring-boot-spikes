# Chaos Engineering for CDC Pipeline

This directory contains chaos engineering tools and scenarios for testing the resilience of the CDC (Change Data Capture) pipeline under failure conditions.

## What is Chaos Engineering?

Chaos Engineering is the discipline of experimenting on a system to build confidence in its capability to withstand turbulent conditions in production. It involves deliberately introducing failures to:

- **Validate assumptions** about system behavior under stress
- **Discover weaknesses** before they cause production incidents
- **Build confidence** in recovery mechanisms
- **Improve system resilience** through iterative testing

### Principles of Chaos Engineering

1. **Build a hypothesis around steady-state behavior** - Define what "normal" looks like
2. **Vary real-world events** - Simulate realistic failure scenarios
3. **Run experiments in production** - Or as close to production as possible
4. **Automate experiments** - Make chaos testing repeatable
5. **Minimize blast radius** - Start small and increase scope gradually

## Tools

### Pumba

[Pumba](https://github.com/alexei-led/pumba) is a chaos testing tool for Docker containers. It can:

- **Kill containers** - Simulate process crashes
- **Pause containers** - Simulate freezes or hangs
- **Stop containers** - Graceful shutdown testing
- **Network emulation** - Inject latency, packet loss, bandwidth limits
- **Stress testing** - CPU and memory pressure

```bash
# Example: Kill a container
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba kill cdc-mongodb

# Example: Add network delay
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba netem --duration 60s \
  --tc-image gaiadocker/iproute2 \
  delay --time 200 --jitter 50 \
  cdc-kafka
```

### Toxiproxy

[Toxiproxy](https://github.com/Shopify/toxiproxy) is a TCP proxy for simulating network conditions. It provides:

- **Latency injection** - Add delays to connections
- **Bandwidth limiting** - Simulate slow networks
- **Timeout simulation** - Test timeout handling
- **Connection severing** - Simulate network partitions
- **Slow close** - Test connection cleanup

Toxiproxy is useful for more fine-grained network chaos compared to Pumba's netem approach.

## Directory Structure

```
chaos/
├── README.md                      # This documentation
├── docker-compose.chaos.yml       # Pumba and Toxiproxy services
├── run-chaos.sh                   # Chaos test runner script
└── scenarios/
    ├── kafka-partition.yml        # Kafka network partition scenario
    ├── mongodb-failure.yml        # MongoDB crash/recovery scenario
    ├── consumer-restart.yml       # Consumer crash/recovery scenario
    └── network-delay.yml          # Network latency injection scenario
```

## Available Scenarios

### 1. Kafka Network Partition (`kafka-partition`)

Simulates a network partition between the consumer and Kafka broker.

| Parameter | Value |
|-----------|-------|
| Duration | 30 seconds |
| Effect | 100% packet loss |
| Target | cdc-kafka container |

**What it tests:**
- Consumer reconnection logic
- Exponential backoff behavior
- Event processing after recovery
- No duplicate processing

**Expected behavior:**
- Consumer loses connection to Kafka
- Events accumulate in Kafka during partition
- Consumer reconnects when network heals
- All accumulated events are processed

### 2. MongoDB Failure (`mongodb-failure`)

Simulates a MongoDB crash and recovery scenario.

| Parameter | Value |
|-----------|-------|
| Outage Duration | 60 seconds |
| Recovery Wait | Up to 60 seconds |
| Effect | Container killed with SIGKILL |

**What it tests:**
- Consumer behavior when sink is unavailable
- Retry logic for MongoDB writes
- Idempotent processing (no duplicates after recovery)
- Event buffering during outage

**Expected behavior:**
- MongoDB container is killed
- Consumer retries writes with backoff
- MongoDB restarts and becomes healthy
- Consumer resumes processing
- No events are lost or duplicated

### 3. Consumer Restart (`consumer-restart`)

Simulates a consumer crash and recovery.

| Parameter | Value |
|-----------|-------|
| Outage Duration | 30 seconds |
| Recovery Wait | Up to 90 seconds |
| Effect | Container killed with SIGKILL |

**What it tests:**
- Kafka offset management
- Consumer group rebalancing
- Resume from last committed offset
- Event processing continuity

**Expected behavior:**
- Consumer container is killed
- Events continue flowing to Kafka
- Consumer restarts and rejoins group
- Processing resumes from last offset
- No events are lost

### 4. Network Delay (`network-delay`)

Injects network latency to test degraded conditions.

| Parameter | Value |
|-----------|-------|
| Duration | 120 seconds |
| Latency | 200ms + 50ms jitter |
| Targets | cdc-kafka, cdc-mongodb |

**What it tests:**
- Timeout handling
- Performance under degraded conditions
- Throughput degradation
- Error rates under stress

**Expected behavior:**
- E2E latency increases proportionally
- Pipeline continues processing (slower)
- Error rate remains low (<1%)
- Recovery to baseline after delay removal

### 5. CPU Stress (`cpu-stress`)

Applies CPU pressure to the consumer.

| Parameter | Value |
|-----------|-------|
| Duration | 60 seconds |
| CPU Load | 80% |
| Target | Consumer container |

**What it tests:**
- Performance under CPU contention
- Processing delays
- Timeout behavior

### 6. Memory Stress (`memory-stress`)

Applies memory pressure to the consumer.

| Parameter | Value |
|-----------|-------|
| Duration | 60 seconds |
| Memory | 256MB |
| Target | Consumer container |

**What it tests:**
- Behavior under memory pressure
- Garbage collection impact
- OOM handling

## Usage

### Prerequisites

1. Main CDC stack must be running:
   ```bash
   docker compose up -d
   ```

2. k6 load testing stack should be running:
   ```bash
   docker compose -f k6/docker-compose.k6.yml up -d
   ```

### Running Chaos Tests

#### Interactive Testing

Run chaos scenarios manually while observing the system:

```bash
# Terminal 1: Start k6 chaos resilience test
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/chaos-resilience-test.js

# Terminal 2: Inject chaos at ~3 minute mark
./chaos/run-chaos.sh mongodb-failure
```

#### Available Commands

```bash
# Show help and available scenarios
./chaos/run-chaos.sh help

# Run specific scenario
./chaos/run-chaos.sh kafka-partition
./chaos/run-chaos.sh mongodb-failure
./chaos/run-chaos.sh consumer-restart
./chaos/run-chaos.sh network-delay
./chaos/run-chaos.sh cpu-stress
./chaos/run-chaos.sh memory-stress

# Run all scenarios sequentially (with 60s cooldown between each)
./chaos/run-chaos.sh all
```

### Using Pumba Directly

For custom chaos experiments:

```bash
# Kill a container
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba kill cdc-mongodb

# Pause a container for 30 seconds
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba pause --duration 30s cdc-consumer-k6

# Add 500ms latency with 100ms jitter for 2 minutes
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba netem --duration 2m \
  --tc-image gaiadocker/iproute2 \
  delay --time 500 --jitter 100 \
  cdc-kafka

# 50% packet loss for 1 minute
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba netem --duration 1m \
  --tc-image gaiadocker/iproute2 \
  loss --percent 50 \
  cdc-mongodb

# Limit bandwidth to 1Mbps
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba netem --duration 2m \
  --tc-image gaiadocker/iproute2 \
  rate --rate 1mbit \
  cdc-kafka
```

### Using Toxiproxy

Start Toxiproxy and configure proxies:

```bash
# Start Toxiproxy
docker compose -f chaos/docker-compose.chaos.yml --profile chaos up -d toxiproxy

# Create a proxy for MongoDB
curl -X POST http://localhost:8474/proxies \
  -H "Content-Type: application/json" \
  -d '{"name": "mongodb", "listen": "0.0.0.0:27018", "upstream": "cdc-mongodb:27017"}'

# Add latency toxic
curl -X POST http://localhost:8474/proxies/mongodb/toxics \
  -H "Content-Type: application/json" \
  -d '{"name": "latency", "type": "latency", "attributes": {"latency": 200, "jitter": 50}}'

# Remove toxic
curl -X DELETE http://localhost:8474/proxies/mongodb/toxics/latency
```

## k6 Chaos Resilience Test

The `k6/scripts/chaos-resilience-test.js` test is designed to run alongside chaos injection:

### Test Phases

| Phase | Time | Description |
|-------|------|-------------|
| BASELINE | 0-3 min | Establish steady-state metrics |
| CHAOS | 3-4 min | Chaos injection window |
| RECOVERY | 4-6 min | Monitor system recovery |
| VERIFICATION | 6-7 min | Final verification |

### Metrics Collected

| Metric | Description |
|--------|-------------|
| `chaos_phase` | Current test phase (1-4) |
| `chaos_recovery_time` | Time to recover from failure |
| `chaos_events_lost` | Count of events not replicated |
| `chaos_resilience_rate` | Success rate during chaos |

### Thresholds

| Threshold | Value | Description |
|-----------|-------|-------------|
| `chaos_resilience_rate` | >90% | Events should succeed even with chaos |
| `chaos_recovery_time` | p95 <30s | Recovery should be fast |
| `cdc_success_rate` | >85% | Allow more failures during chaos |

## Observability During Chaos

### Grafana Dashboards

Monitor these dashboards during chaos experiments:

- **CDC Pipeline Overview** - Overall health and event flow
- **Consumer Performance** - Processing latency and throughput
- **MongoDB Operations** - Write latency and errors
- **k6 Load Testing** - Test metrics and thresholds

### Key Metrics to Watch

1. **During Chaos:**
   - Error rates increasing
   - Latency spikes
   - Consumer lag growing
   - Connection failures

2. **During Recovery:**
   - Error rates decreasing
   - Latency returning to baseline
   - Consumer lag decreasing
   - Connections re-established

### Alerts

The Grafana alerting configuration should fire during chaos:
- High error rate alerts
- Consumer lag alerts
- Latency threshold alerts

Verify alerts resolve after recovery.

## Best Practices

### Before Running Chaos Tests

1. **Establish baseline** - Know what "normal" looks like
2. **Have observability** - Ensure dashboards and alerts are working
3. **Prepare rollback** - Know how to quickly recover
4. **Notify stakeholders** - Communicate when running tests
5. **Start small** - Begin with short durations and single failures

### During Chaos Tests

1. **Monitor actively** - Watch dashboards in real-time
2. **Document observations** - Record unexpected behaviors
3. **Be ready to abort** - Have a kill switch ready
4. **Collect evidence** - Save logs and metrics

### After Chaos Tests

1. **Verify recovery** - Ensure system returns to steady state
2. **Analyze results** - Review metrics and logs
3. **Document findings** - Record what worked and what didn't
4. **Create action items** - File issues for problems found
5. **Iterate** - Increase scope gradually

## Troubleshooting

### Pumba Container Can't Access Docker Socket

```bash
# Ensure socket is accessible
ls -la /var/run/docker.sock

# On Linux, user may need docker group membership
sudo usermod -aG docker $USER
```

### Network Chaos Not Working

```bash
# Pumba netem requires the tc (traffic control) image
docker pull gaiadocker/iproute2

# Verify container has NET_ADMIN capability
docker run --cap-add NET_ADMIN ...
```

### Container Not Found

```bash
# List running containers
docker ps --format '{{.Names}}'

# Verify container names match scenario targets
docker inspect cdc-mongodb --format='{{.Name}}'
```

### Recovery Taking Too Long

```bash
# Check container health
docker inspect cdc-mongodb --format='{{.State.Health.Status}}'

# View container logs
docker logs cdc-mongodb --tail 50

# Force restart if needed
docker compose up -d --force-recreate mongodb
```

## References

- [Principles of Chaos Engineering](https://principlesofchaos.org/)
- [Pumba Documentation](https://github.com/alexei-led/pumba)
- [Toxiproxy Documentation](https://github.com/Shopify/toxiproxy)
- [Netflix Chaos Engineering](https://netflix.github.io/chaosmonkey/)
- [k6 Load Testing](https://k6.io/docs/)
