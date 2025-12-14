# LOAD-001: k6 Load Testing Integration

## Overview

This implementation plan details the integration of [k6](https://k6.io/) load testing framework into the saga pattern spike project. k6 is a modern load testing tool that provides excellent developer experience, native Prometheus/Grafana integration, and powerful scripting capabilities using JavaScript.

## Goals

1. **Performance Validation**: Validate the saga pattern implementation under load
2. **Metrics Integration**: Export k6 metrics to Prometheus for unified observability
3. **Visualization**: Create Grafana dashboards to visualize load test results alongside application metrics
4. **Automation**: Provide easy-to-use scripts for running various test scenarios
5. **CI/CD Ready**: Structure for future pipeline integration

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Load Testing Architecture                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         ┌─────────────────────┐                           │
│  │     k6       │────────▶│   Spring Boot App   │                           │
│  │  Test Runner │  HTTP   │   (localhost:8080)  │                           │
│  └──────┬───────┘         └──────────┬──────────┘                           │
│         │                            │                                       │
│         │ Metrics                    │ Metrics                               │
│         ▼                            ▼                                       │
│  ┌──────────────┐         ┌─────────────────────┐                           │
│  │  Prometheus  │◀────────│  /actuator/prometheus│                          │
│  │              │ Scrape  │                     │                           │
│  └──────┬───────┘         └─────────────────────┘                           │
│         │                                                                    │
│         │ Query                                                              │
│         ▼                                                                    │
│  ┌──────────────┐                                                           │
│  │   Grafana    │  ◀── k6 Load Testing Dashboard                            │
│  │              │  ◀── Application Metrics Dashboard                         │
│  └──────────────┘  ◀── Combined Performance View                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## API Endpoints to Test

| Endpoint | Method | Description | Priority |
|----------|--------|-------------|----------|
| `/api/orders` | POST | Create order (saga execution) | High |
| `/api/orders/{orderId}` | GET | Retrieve order details | High |
| `/api/orders/{orderId}/status` | GET | Get order processing status | High |
| `/api/orders/customer/{customerId}` | GET | List customer orders | Medium |
| `/api/orders/{orderId}/history` | GET | Get order history | Medium |
| `/api/orders/{orderId}/timeline` | GET | Get order timeline | Low |
| `/api/orders/{orderId}/events` | GET | Get raw order events | Low |
| `/api/orders/{orderId}/retry/eligibility` | GET | Check retry eligibility | Low |
| `/actuator/health` | GET | Health check | High |

## Implementation Phases

---

### Phase 1: Project Structure Setup

**Objective**: Create the directory structure and configuration files for k6 tests.

#### 1.1 Create Directory Structure

```
load-tests/
├── scripts/
│   ├── lib/
│   │   ├── config.js         # Shared configuration
│   │   ├── helpers.js        # Utility functions
│   │   └── data-generators.js # Test data generators
│   ├── scenarios/
│   │   ├── smoke.js          # Quick validation (1-2 VUs, 1 min)
│   │   ├── load.js           # Normal load (10-50 VUs, 5 min)
│   │   ├── stress.js         # Peak load (100+ VUs, 10 min)
│   │   └── soak.js           # Endurance (20 VUs, 30+ min)
│   └── api/
│       ├── orders.js         # Order API tests
│       └── health.js         # Health check tests
├── dashboards/
│   └── k6-dashboard.json     # Grafana dashboard
├── results/                   # Test output (gitignored)
└── README.md                  # Load testing documentation
```

#### 1.2 Create Base Configuration

Create shared configuration for all k6 scripts including:
- Base URL configuration
- Default thresholds
- Common tags for metrics

---

### Phase 2: Test Data Generators

**Objective**: Create realistic test data generators for order creation.

#### 2.1 Order Data Generator

Implement functions to generate:
- Random customer IDs
- Product catalog with realistic items
- Shipping addresses
- Payment method IDs

#### 2.2 Validation

Ensure generated data:
- Matches API schema requirements
- Provides variety for realistic testing
- Is deterministic when seeded (for reproducibility)

---

### Phase 3: Core Test Scripts

**Objective**: Implement the main test scenarios.

#### 3.1 Smoke Test (`smoke.js`)

**Purpose**: Quick validation that the system works under minimal load.

**Configuration**:
- Virtual Users (VUs): 1-2
- Duration: 1 minute
- Thresholds:
  - HTTP request duration p(95) < 500ms
  - HTTP request failed rate < 1%

**Scenarios**:
1. Create a single order
2. Retrieve the order
3. Check order status

#### 3.2 Load Test (`load.js`)

**Purpose**: Validate system under expected normal load.

**Configuration**:
- Virtual Users: 10-50 (ramping)
- Duration: 5 minutes
- Stages:
  - Ramp up: 2 min to 50 VUs
  - Steady: 2 min at 50 VUs
  - Ramp down: 1 min to 0 VUs
- Thresholds:
  - HTTP request duration p(95) < 1000ms
  - HTTP request duration p(99) < 2000ms
  - HTTP request failed rate < 5%

**Scenarios**:
1. Create orders (70% of traffic)
2. Get order status (20% of traffic)
3. List customer orders (10% of traffic)

#### 3.3 Stress Test (`stress.js`)

**Purpose**: Find system breaking points and recovery behavior.

**Configuration**:
- Virtual Users: 100-200 (aggressive ramping)
- Duration: 10 minutes
- Stages:
  - Ramp up: 3 min to 100 VUs
  - Spike: 1 min to 200 VUs
  - Recovery: 2 min to 100 VUs
  - Steady: 2 min at 100 VUs
  - Ramp down: 2 min to 0 VUs
- Thresholds:
  - HTTP request duration p(95) < 3000ms
  - HTTP request failed rate < 15%

**Scenarios**:
1. Heavy order creation
2. Concurrent status checks
3. Recovery validation

#### 3.4 Soak Test (`soak.js`)

**Purpose**: Detect memory leaks and resource exhaustion over time.

**Configuration**:
- Virtual Users: 20-30 (constant)
- Duration: 30+ minutes
- Thresholds:
  - HTTP request duration p(95) < 1500ms (should not degrade)
  - HTTP request failed rate < 5%
  - No increasing trend in response times

**Scenarios**:
1. Sustained order creation
2. Mixed read/write operations

---

### Phase 4: Prometheus Integration

**Objective**: Export k6 metrics to Prometheus for unified observability.

#### 4.1 Configure k6 Prometheus Output

k6 supports experimental Prometheus remote write. Configuration options:
- Use `K6_PROMETHEUS_RW_SERVER_URL` environment variable
- Push metrics to Prometheus Pushgateway
- Alternative: Use k6 Cloud or custom output

#### 4.2 Add Prometheus Pushgateway to Docker Compose

Add Prometheus Pushgateway service for k6 metrics collection:
- Image: `prom/pushgateway`
- Port: 9091
- Configure Prometheus to scrape from Pushgateway

#### 4.3 Custom Metrics

Define custom k6 metrics for saga-specific measurements:
- `saga_order_creation_duration` - Time to complete full saga
- `saga_step_success_rate` - Individual step success rates
- `saga_compensation_triggered` - Counter for compensations

---

### Phase 5: Grafana Dashboard

**Objective**: Create a comprehensive k6 load testing dashboard.

#### 5.1 Dashboard Panels

**Overview Row**:
- Total requests
- Request rate (req/s)
- Error rate percentage
- Active VUs

**Latency Row**:
- Response time distribution (histogram)
- p50, p90, p95, p99 percentiles
- Response time over time

**Throughput Row**:
- Requests per second by endpoint
- Data sent/received rates
- Iteration duration

**Errors Row**:
- Error rate by endpoint
- HTTP status code distribution
- Error timeline

**Correlation Row**:
- k6 metrics vs application metrics side-by-side
- Response time correlation with JVM metrics
- Database connection pool during load

#### 5.2 Dashboard Variables

- Time range selector
- Test run filter (by tags)
- Endpoint filter

---

### Phase 6: Automation Scripts

**Objective**: Provide easy-to-use commands for running tests.

#### 6.1 Makefile Targets

```makefile
# Load testing targets
load-test-smoke       # Run smoke test
load-test-load        # Run load test
load-test-stress      # Run stress test
load-test-soak        # Run soak test
load-test-all         # Run all tests sequentially
load-test-report      # Generate HTML report from last run
```

#### 6.2 Docker-based Execution

Create Docker command for running k6:
- Mount test scripts
- Set environment variables
- Output results to shared volume

---

### Phase 7: Documentation

**Objective**: Document usage, best practices, and troubleshooting.

#### 7.1 Load Testing README

Create `load-tests/README.md` with:
- Prerequisites
- Quick start guide
- Test scenario descriptions
- Interpreting results
- Troubleshooting guide

#### 7.2 Update Project Documentation

Update `CLAUDE.md` and `README.md` with:
- Load testing commands
- Dashboard access
- Best practices

---

## File Changes Summary

### New Files

| File | Description |
|------|-------------|
| `load-tests/scripts/lib/config.js` | Shared k6 configuration |
| `load-tests/scripts/lib/helpers.js` | Utility functions |
| `load-tests/scripts/lib/data-generators.js` | Test data generation |
| `load-tests/scripts/scenarios/smoke.js` | Smoke test scenario |
| `load-tests/scripts/scenarios/load.js` | Load test scenario |
| `load-tests/scripts/scenarios/stress.js` | Stress test scenario |
| `load-tests/scripts/scenarios/soak.js` | Soak test scenario |
| `load-tests/scripts/api/orders.js` | Order API test functions |
| `load-tests/scripts/api/health.js` | Health check tests |
| `docker/grafana/provisioning/dashboards/json/k6-dashboard.json` | k6 Grafana dashboard |
| `load-tests/README.md` | Load testing documentation |
| `Makefile` | Build and test automation |

### Modified Files

| File | Changes |
|------|---------|
| `docker-compose.yml` | Add Prometheus Pushgateway service |
| `docker/prometheus/prometheus.yml` | Add Pushgateway scrape config |
| `CLAUDE.md` | Add load testing section |
| `README.md` | Add load testing documentation |
| `.gitignore` | Add `load-tests/results/` |

---

## Test Thresholds Reference

| Scenario | p95 Latency | p99 Latency | Error Rate | Duration |
|----------|-------------|-------------|------------|----------|
| Smoke | < 500ms | < 1000ms | < 1% | 1 min |
| Load | < 1000ms | < 2000ms | < 5% | 5 min |
| Stress | < 3000ms | < 5000ms | < 15% | 10 min |
| Soak | < 1500ms | < 3000ms | < 5% | 30 min |

---

## Success Criteria

1. **Scripts Execute Successfully**: All k6 scripts run without errors
2. **Metrics Flow**: k6 metrics appear in Prometheus and Grafana
3. **Dashboard Works**: k6 dashboard displays all panels correctly
4. **Thresholds Pass**: Smoke test passes all thresholds against running application
5. **Documentation Complete**: README and project docs updated
6. **Makefile Works**: All make targets execute correctly

---

## Dependencies

- k6 (installed locally or via Docker)
- Running infrastructure (docker compose up)
- Running Spring Boot application
- Prometheus and Grafana services

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| WireMock rate limiting | Configure WireMock with appropriate delays |
| Database connection exhaustion | Monitor connection pool metrics |
| k6 Prometheus integration experimental | Fallback to JSON output + import |
| Resource constraints on dev machine | Use conservative VU counts for local testing |

---

## Timeline Estimate

This implementation can be completed in phases, with each phase building on the previous:

- Phase 1-2: Foundation and data generators
- Phase 3: Core test scripts
- Phase 4-5: Metrics and dashboard
- Phase 6-7: Automation and documentation

---

## References

- [k6 Documentation](https://k6.io/docs/)
- [k6 Prometheus Output](https://k6.io/docs/results-output/real-time/prometheus-remote-write/)
- [k6 Test Types](https://k6.io/docs/test-types/)
- [Grafana k6 Plugin](https://grafana.com/grafana/plugins/grafana-k6-app/)
