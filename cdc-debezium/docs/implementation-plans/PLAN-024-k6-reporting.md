# PLAN-024: k6 Reporting and Local Test Execution

## Objective

Implement k6 test reporting with JSON outputs, HTML report generation, Grafana dashboards for result visualization, configurable thresholds, and a local test runner script.

## Parent Feature

[FEATURE-002](../features/FEATURE-002.md) - Section 2.7.7: Reporting & Visualization

## Dependencies

- PLAN-022: k6 Load Testing Infrastructure
- PLAN-023: k6 Load Test Scenarios

## Related Plans

- PLAN-026: GitHub Actions Performance Testing CI/CD (uses artifacts from this plan)

## Changes

### Files to Create/Modify

| File                                                 | Purpose                     |
|------------------------------------------------------|-----------------------------
| `k6/scripts/run-tests.sh`                            | Test runner script          |
| `k6/thresholds.json`                                 | Configurable threshold file |
| `k6/grafana/provisioning/dashboards/k6-results.json` | k6 results dashboard        |
| `k6/results/.gitkeep`                                | Results directory           |

### Test Runner Script (run-tests.sh)

```bash
#!/bin/bash
# k6/scripts/run-tests.sh

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

RESULTS_DIR="${RESULTS_DIR:-./k6/results}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Default scenarios
SCENARIOS="${1:-baseline}"

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Create results directory
mkdir -p "$RESULTS_DIR"

# Start infrastructure if not running
if ! docker compose ps postgres | grep -q "healthy"; then
    log "Starting CDC infrastructure..."
    docker compose up -d
    sleep 30
fi

# Build k6 if needed
log "Building k6 image..."
docker compose -f k6/docker-compose.k6.yml build k6

# Run health check
log "Running health check..."
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/health-check.js

# Run specified scenarios
IFS=',' read -ra SCENARIO_LIST <<< "$SCENARIOS"

for scenario in "${SCENARIO_LIST[@]}"; do
    log "Running $scenario test..."

    script_name="${scenario}-test.js"
    if [ "$scenario" == "e2e-latency" ]; then
        script_name="e2e-latency-test.js"
    elif [ "$scenario" == "mixed-workload" ]; then
        script_name="mixed-workload-test.js"
    fi

    docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
        --out json=/results/${scenario}-${TIMESTAMP}.json \
        --out experimental-prometheus-rw \
        /scripts/${script_name} || {
            warn "Test $scenario completed with warnings"
        }

    log "Test $scenario completed. Results: $RESULTS_DIR/${scenario}-${TIMESTAMP}.json"
done

# Generate summary
log "Generating summary..."
echo ""
echo "========================================"
echo "         TEST RESULTS SUMMARY"
echo "========================================"

for result in "$RESULTS_DIR"/*-${TIMESTAMP}.json; do
    if [ -f "$result" ]; then
        scenario=$(basename "$result" -${TIMESTAMP}.json)
        echo ""
        echo "--- $scenario ---"
        jq -r '
            "Iterations: \(.root_group.iterations // "N/A")",
            "Duration: \(.state.testRunDurationMs // 0 | . / 1000 | tostring + "s")",
            "VUs: \(.vus_max // "N/A")"
        ' "$result" 2>/dev/null || echo "Unable to parse results"

        # Check for failures
        fails=$(jq -r '.root_group.checks | to_entries | map(select(.value.fails > 0)) | length' "$result" 2>/dev/null || echo "0")
        if [ "$fails" -gt 0 ]; then
            error "  Checks failed: $fails"
        else
            log "  All checks passed"
        fi
    fi
done

echo ""
echo "========================================"
log "All tests completed. Results in $RESULTS_DIR"
```

### Configurable Thresholds (thresholds.json)

```json
{
  "baseline": {
    "cdc_e2e_latency": {
      "p95": 2000,
      "p99": 5000
    },
    "pg_write_duration": {
      "p95": 100
    },
    "mongo_read_duration": {
      "p95": 50
    },
    "cdc_success_rate": {
      "rate": 0.99
    }
  },
  "stress": {
    "cdc_e2e_latency": {
      "p95": 5000,
      "p99": 10000
    },
    "cdc_success_rate": {
      "rate": 0.95
    }
  },
  "spike": {
    "cdc_e2e_latency": {
      "p95": 10000
    },
    "cdc_success_rate": {
      "rate": 0.90
    }
  },
  "soak": {
    "cdc_e2e_latency": {
      "p95": 3000,
      "p99": 6000
    },
    "cdc_success_rate": {
      "rate": 0.99
    }
  }
}
```

### k6 Results Grafana Dashboard (k6-results.json)

```json
{
  "dashboard": {
    "title": "k6 Load Test Results",
    "uid": "k6-results",
    "tags": [
      "k6",
      "load-testing",
      "performance"
    ],
    "timezone": "browser",
    "refresh": "10s",
    "panels": [
      {
        "id": 1,
        "title": "Virtual Users",
        "type": "timeseries",
        "gridPos": {
          "x": 0,
          "y": 0,
          "w": 12,
          "h": 6
        },
        "targets": [
          {
            "expr": "k6_vus",
            "legendFormat": "VUs"
          },
          {
            "expr": "k6_vus_max",
            "legendFormat": "Max VUs"
          }
        ]
      },
      {
        "id": 2,
        "title": "Request Rate",
        "type": "timeseries",
        "gridPos": {
          "x": 12,
          "y": 0,
          "w": 12,
          "h": 6
        },
        "targets": [
          {
            "expr": "rate(k6_iterations_total[1m])",
            "legendFormat": "Iterations/sec"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "reqps"
          }
        }
      },
      {
        "id": 3,
        "title": "CDC E2E Latency",
        "type": "timeseries",
        "gridPos": {
          "x": 0,
          "y": 6,
          "w": 12,
          "h": 8
        },
        "targets": [
          {
            "expr": "histogram_quantile(0.50, rate(k6_cdc_e2e_latency_bucket[1m]))",
            "legendFormat": "p50"
          },
          {
            "expr": "histogram_quantile(0.95, rate(k6_cdc_e2e_latency_bucket[1m]))",
            "legendFormat": "p95"
          },
          {
            "expr": "histogram_quantile(0.99, rate(k6_cdc_e2e_latency_bucket[1m]))",
            "legendFormat": "p99"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "ms"
          }
        }
      },
      {
        "id": 4,
        "title": "Success Rate",
        "type": "gauge",
        "gridPos": {
          "x": 12,
          "y": 6,
          "w": 6,
          "h": 4
        },
        "targets": [
          {
            "expr": "k6_cdc_success_rate",
            "legendFormat": "Success Rate"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "thresholds": {
              "mode": "absolute",
              "steps": [
                {
                  "color": "red",
                  "value": 0
                },
                {
                  "color": "yellow",
                  "value": 0.95
                },
                {
                  "color": "green",
                  "value": 0.99
                }
              ]
            }
          }
        }
      },
      {
        "id": 5,
        "title": "Error Count",
        "type": "stat",
        "gridPos": {
          "x": 18,
          "y": 6,
          "w": 6,
          "h": 4
        },
        "targets": [
          {
            "expr": "sum(increase(k6_pg_write_errors[5m]))",
            "legendFormat": "PG Errors"
          },
          {
            "expr": "sum(increase(k6_mongo_read_errors[5m]))",
            "legendFormat": "Mongo Errors"
          }
        ]
      },
      {
        "id": 6,
        "title": "PostgreSQL Write Latency",
        "type": "timeseries",
        "gridPos": {
          "x": 12,
          "y": 10,
          "w": 12,
          "h": 4
        },
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(k6_pg_write_duration_bucket[1m]))",
            "legendFormat": "p95"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "ms"
          }
        }
      },
      {
        "id": 7,
        "title": "MongoDB Read Latency",
        "type": "timeseries",
        "gridPos": {
          "x": 0,
          "y": 14,
          "w": 12,
          "h": 4
        },
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(k6_mongo_read_duration_bucket[1m]))",
            "legendFormat": "p95"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "ms"
          }
        }
      },
      {
        "id": 8,
        "title": "Records Processed",
        "type": "stat",
        "gridPos": {
          "x": 12,
          "y": 14,
          "w": 6,
          "h": 4
        },
        "targets": [
          {
            "expr": "sum(k6_pg_records_inserted)",
            "legendFormat": "Inserted"
          }
        ]
      },
      {
        "id": 9,
        "title": "Documents Found",
        "type": "stat",
        "gridPos": {
          "x": 18,
          "y": 14,
          "w": 6,
          "h": 4
        },
        "targets": [
          {
            "expr": "sum(k6_mongo_documents_found)",
            "legendFormat": "Found"
          },
          {
            "expr": "sum(k6_mongo_documents_not_found)",
            "legendFormat": "Not Found"
          }
        ]
      },
      {
        "id": 10,
        "title": "Threshold Status",
        "type": "table",
        "gridPos": {
          "x": 0,
          "y": 18,
          "w": 24,
          "h": 6
        },
        "targets": [
          {
            "expr": "k6_threshold_passes",
            "format": "table"
          }
        ],
        "transformations": [
          {
            "id": "organize",
            "options": {
              "excludeByName": {},
              "indexByName": {},
              "renameByName": {
                "threshold": "Threshold",
                "Value": "Status"
              }
            }
          }
        ]
      }
    ],
    "templating": {
      "list": [
        {
          "name": "test_run",
          "type": "query",
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "query": "label_values(k6_vus, test_run_id)",
          "multi": false,
          "includeAll": false
        }
      ]
    }
  }
}
```

## Directory Structure

```
k6/
├── scripts/
│   └── run-tests.sh
├── results/
│   └── .gitkeep
├── grafana/
│   └── provisioning/
│       └── dashboards/
│           └── k6-results.json
├── thresholds.json
└── docker-compose.k6.yml
```

## Commands to Run

```bash
# Make run script executable
chmod +x k6/scripts/run-tests.sh

# Run all tests locally
./k6/scripts/run-tests.sh baseline,stress,e2e-latency

# Run specific scenario
./k6/scripts/run-tests.sh baseline

# Run via docker compose directly
docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
  --out json=/results/test.json \
  /scripts/baseline-test.js

# Convert JSON to HTML report (requires k6-html-reporter)
npm install -g k6-html-reporter
k6-html-reporter --input k6/results/baseline.json --output k6/results/baseline.html

# View results in Grafana (with test_run_id variable)
open http://localhost:3000/d/k6-results
```

## Acceptance Criteria

- [ ] Test runner script (`run-tests.sh`) executes multiple scenarios and prints summary
- [ ] Runner script starts infrastructure if not running
- [ ] Runner script builds k6 image if needed
- [ ] Runner script runs health check before tests
- [ ] JSON results are written to `k6/results/` with timestamp
- [ ] Summary shows iterations, duration, VUs, and check pass/fail status
- [ ] Configurable thresholds exist for baseline, stress, spike, and soak scenarios
- [ ] Grafana dashboard displays live k6 metrics:
  - [ ] Virtual Users panel
  - [ ] Request Rate panel
  - [ ] CDC E2E Latency (p50, p95, p99)
  - [ ] Success Rate gauge
  - [ ] Error Count stat
  - [ ] PostgreSQL Write Latency
  - [ ] MongoDB Read Latency
  - [ ] Records Processed stat
  - [ ] Documents Found stat
  - [ ] Threshold Status table
- [ ] Dashboard supports test run filtering via `test_run` variable
- [ ] HTML reports can be generated from JSON using k6-html-reporter

## Estimated Complexity

Medium - Involves bash scripting, Grafana dashboard configuration, and threshold management.

## Notes

- JSON output enables custom analysis and historical trending
- Thresholds are scenario-specific (stricter for baseline, relaxed for stress/spike)
- Grafana dashboard provides real-time visibility during test execution
- The runner script uses colored output for better readability
- Results directory is gitignored but preserved with `.gitkeep`
- HTML reports are optional and require `k6-html-reporter` npm package
- See PLAN-026 for GitHub Actions CI/CD integration
