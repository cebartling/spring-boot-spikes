# PLAN-020: Grafana Dashboards

## Objective

Create comprehensive Grafana dashboards for monitoring the CDC pipeline, including CDC throughput, consumer performance, MongoDB operations, and system health.

## Parent Feature

[FEATURE-002](../features/FEATURE-002.md) - Section 2.5.2-2.5.6: Grafana Dashboards

## Dependencies

- PLAN-019: Grafana LGTM Infrastructure

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `docker/grafana/provisioning/dashboards/json/cdc-overview.json` | Main CDC overview dashboard |
| `docker/grafana/provisioning/dashboards/json/consumer-performance.json` | Kafka consumer metrics |
| `docker/grafana/provisioning/dashboards/json/mongodb-operations.json` | MongoDB performance dashboard |
| `docker/grafana/provisioning/dashboards/json/service-map.json` | Distributed tracing service map |
| `docker/grafana/provisioning/dashboards/json/logs-explorer.json` | Centralized logs dashboard |

### Dashboard Architecture

```mermaid
flowchart TB
    subgraph DASH["Grafana Dashboards"]
        OVERVIEW[CDC Overview<br/>Main KPIs]
        CONSUMER[Consumer Performance<br/>Kafka Metrics]
        MONGO[MongoDB Operations<br/>DB Metrics]
        TRACE[Service Map<br/>Tracing]
        LOGS[Logs Explorer<br/>Log Analysis]
    end

    subgraph DATA["Data Sources"]
        PROM[(Prometheus)]
        TEMPO[(Tempo)]
        LOKI[(Loki)]
    end

    PROM --> OVERVIEW
    PROM --> CONSUMER
    PROM --> MONGO
    TEMPO --> TRACE
    TEMPO --> OVERVIEW
    LOKI --> LOGS
    LOKI --> OVERVIEW
```

### CDC Overview Dashboard (cdc-overview.json)

```json
{
  "annotations": {
    "list": [
      {
        "datasource": { "type": "loki", "uid": "loki" },
        "enable": true,
        "expr": "{service_name=\"cdc-debezium\"} |~ \"ERROR|WARN\"",
        "iconColor": "red",
        "name": "Error Annotations"
      }
    ]
  },
  "dashboard": {
    "title": "CDC Pipeline Overview",
    "uid": "cdc-overview",
    "tags": ["cdc", "overview"],
    "timezone": "browser",
    "refresh": "10s",
    "panels": [
      {
        "id": 1,
        "title": "Events Processed (Rate)",
        "type": "stat",
        "gridPos": { "x": 0, "y": 0, "w": 6, "h": 4 },
        "targets": [
          {
            "expr": "sum(rate(cdc_events_processed_total[5m]))",
            "legendFormat": "events/sec"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "ops",
            "thresholds": {
              "mode": "absolute",
              "steps": [
                { "color": "green", "value": null },
                { "color": "yellow", "value": 100 },
                { "color": "red", "value": 500 }
              ]
            }
          }
        }
      },
      {
        "id": 2,
        "title": "Consumer Lag",
        "type": "gauge",
        "gridPos": { "x": 6, "y": 0, "w": 6, "h": 4 },
        "targets": [
          {
            "expr": "sum(kafka_consumer_records_lag_max)",
            "legendFormat": "lag"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "short",
            "min": 0,
            "max": 10000,
            "thresholds": {
              "mode": "absolute",
              "steps": [
                { "color": "green", "value": 0 },
                { "color": "yellow", "value": 1000 },
                { "color": "red", "value": 5000 }
              ]
            }
          }
        }
      },
      {
        "id": 3,
        "title": "Processing Errors",
        "type": "stat",
        "gridPos": { "x": 12, "y": 0, "w": 6, "h": 4 },
        "targets": [
          {
            "expr": "sum(rate(cdc_events_failed_total[5m]))",
            "legendFormat": "errors/sec"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "ops",
            "thresholds": {
              "mode": "absolute",
              "steps": [
                { "color": "green", "value": null },
                { "color": "yellow", "value": 1 },
                { "color": "red", "value": 10 }
              ]
            }
          }
        }
      },
      {
        "id": 4,
        "title": "MongoDB Write Latency",
        "type": "stat",
        "gridPos": { "x": 18, "y": 0, "w": 6, "h": 4 },
        "targets": [
          {
            "expr": "histogram_quantile(0.99, sum(rate(cdc_mongodb_operation_duration_seconds_bucket[5m])) by (le))",
            "legendFormat": "p99"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "s",
            "thresholds": {
              "mode": "absolute",
              "steps": [
                { "color": "green", "value": null },
                { "color": "yellow", "value": 0.1 },
                { "color": "red", "value": 0.5 }
              ]
            }
          }
        }
      },
      {
        "id": 5,
        "title": "Events by Entity Type",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 4, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "sum(rate(cdc_events_processed_total[5m])) by (entity_type)",
            "legendFormat": "{{entity_type}}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "ops",
            "custom": {
              "drawStyle": "line",
              "lineInterpolation": "smooth",
              "fillOpacity": 20
            }
          }
        }
      },
      {
        "id": 6,
        "title": "Event Operations Distribution",
        "type": "piechart",
        "gridPos": { "x": 12, "y": 4, "w": 6, "h": 8 },
        "targets": [
          {
            "expr": "sum(increase(cdc_events_processed_total[1h])) by (operation)",
            "legendFormat": "{{operation}}"
          }
        ]
      },
      {
        "id": 7,
        "title": "Processing Latency Distribution",
        "type": "heatmap",
        "gridPos": { "x": 18, "y": 4, "w": 6, "h": 8 },
        "targets": [
          {
            "expr": "sum(rate(cdc_event_processing_duration_seconds_bucket[5m])) by (le)",
            "format": "heatmap",
            "legendFormat": "{{le}}"
          }
        ]
      },
      {
        "id": 8,
        "title": "End-to-End Latency (CDC Pipeline)",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 12, "w": 24, "h": 6 },
        "targets": [
          {
            "expr": "histogram_quantile(0.50, sum(rate(cdc_end_to_end_latency_seconds_bucket[5m])) by (le))",
            "legendFormat": "p50"
          },
          {
            "expr": "histogram_quantile(0.95, sum(rate(cdc_end_to_end_latency_seconds_bucket[5m])) by (le))",
            "legendFormat": "p95"
          },
          {
            "expr": "histogram_quantile(0.99, sum(rate(cdc_end_to_end_latency_seconds_bucket[5m])) by (le))",
            "legendFormat": "p99"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "s"
          }
        }
      },
      {
        "id": 9,
        "title": "Recent Traces",
        "type": "table",
        "gridPos": { "x": 0, "y": 18, "w": 24, "h": 6 },
        "datasource": { "type": "tempo", "uid": "tempo" },
        "targets": [
          {
            "queryType": "search",
            "limit": 20,
            "filters": [
              { "id": "service-name", "operator": "=", "value": ["cdc-debezium"] }
            ]
          }
        ]
      }
    ],
    "templating": {
      "list": [
        {
          "name": "entity_type",
          "type": "query",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "query": "label_values(cdc_events_processed_total, entity_type)",
          "includeAll": true,
          "multi": true
        }
      ]
    }
  }
}
```

### Consumer Performance Dashboard (consumer-performance.json)

```json
{
  "dashboard": {
    "title": "Kafka Consumer Performance",
    "uid": "consumer-perf",
    "tags": ["cdc", "kafka", "consumer"],
    "refresh": "10s",
    "panels": [
      {
        "id": 1,
        "title": "Consumer Group Lag by Partition",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "kafka_consumer_records_lag{topic=~\"cdc\\\\..*\"}",
            "legendFormat": "{{topic}} - p{{partition}}"
          }
        ]
      },
      {
        "id": 2,
        "title": "Records Consumed Rate",
        "type": "timeseries",
        "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "sum(rate(kafka_consumer_records_consumed_total[5m])) by (topic)",
            "legendFormat": "{{topic}}"
          }
        ]
      },
      {
        "id": 3,
        "title": "Fetch Latency",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 8, "w": 8, "h": 6 },
        "targets": [
          {
            "expr": "kafka_consumer_fetch_latency_avg",
            "legendFormat": "avg"
          },
          {
            "expr": "kafka_consumer_fetch_latency_max",
            "legendFormat": "max"
          }
        ],
        "fieldConfig": {
          "defaults": { "unit": "ms" }
        }
      },
      {
        "id": 4,
        "title": "Bytes Consumed",
        "type": "timeseries",
        "gridPos": { "x": 8, "y": 8, "w": 8, "h": 6 },
        "targets": [
          {
            "expr": "sum(rate(kafka_consumer_bytes_consumed_total[5m]))",
            "legendFormat": "bytes/sec"
          }
        ],
        "fieldConfig": {
          "defaults": { "unit": "Bps" }
        }
      },
      {
        "id": 5,
        "title": "Commit Rate",
        "type": "timeseries",
        "gridPos": { "x": 16, "y": 8, "w": 8, "h": 6 },
        "targets": [
          {
            "expr": "sum(rate(kafka_consumer_commit_total[5m]))",
            "legendFormat": "commits/sec"
          }
        ]
      },
      {
        "id": 6,
        "title": "Rebalance Events",
        "type": "stat",
        "gridPos": { "x": 0, "y": 14, "w": 6, "h": 4 },
        "targets": [
          {
            "expr": "sum(increase(kafka_consumer_rebalance_total[1h]))",
            "legendFormat": "rebalances"
          }
        ]
      },
      {
        "id": 7,
        "title": "Assigned Partitions",
        "type": "stat",
        "gridPos": { "x": 6, "y": 14, "w": 6, "h": 4 },
        "targets": [
          {
            "expr": "sum(kafka_consumer_assigned_partitions)",
            "legendFormat": "partitions"
          }
        ]
      },
      {
        "id": 8,
        "title": "Poll Rate",
        "type": "timeseries",
        "gridPos": { "x": 12, "y": 14, "w": 12, "h": 4 },
        "targets": [
          {
            "expr": "rate(kafka_consumer_poll_total[5m])",
            "legendFormat": "polls/sec"
          }
        ]
      }
    ]
  }
}
```

### MongoDB Operations Dashboard (mongodb-operations.json)

```json
{
  "dashboard": {
    "title": "MongoDB Operations",
    "uid": "mongodb-ops",
    "tags": ["cdc", "mongodb"],
    "refresh": "10s",
    "panels": [
      {
        "id": 1,
        "title": "Operations by Type",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "sum(rate(cdc_mongodb_operation_duration_seconds_count[5m])) by (operation)",
            "legendFormat": "{{operation}}"
          }
        ],
        "fieldConfig": {
          "defaults": { "unit": "ops" }
        }
      },
      {
        "id": 2,
        "title": "Operation Latency (p99)",
        "type": "timeseries",
        "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "histogram_quantile(0.99, sum(rate(cdc_mongodb_operation_duration_seconds_bucket[5m])) by (le, operation))",
            "legendFormat": "{{operation}} p99"
          }
        ],
        "fieldConfig": {
          "defaults": { "unit": "s" }
        }
      },
      {
        "id": 3,
        "title": "Documents by Collection",
        "type": "bargauge",
        "gridPos": { "x": 0, "y": 8, "w": 8, "h": 6 },
        "targets": [
          {
            "expr": "sum(cdc_mongodb_documents_total) by (collection)",
            "legendFormat": "{{collection}}"
          }
        ]
      },
      {
        "id": 4,
        "title": "Upsert Success Rate",
        "type": "gauge",
        "gridPos": { "x": 8, "y": 8, "w": 8, "h": 6 },
        "targets": [
          {
            "expr": "sum(rate(cdc_mongodb_operation_duration_seconds_count{operation=\"upsert\"}[5m])) / (sum(rate(cdc_mongodb_operation_duration_seconds_count{operation=\"upsert\"}[5m])) + sum(rate(cdc_mongodb_errors_total{operation=\"upsert\"}[5m])))",
            "legendFormat": "success rate"
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
                { "color": "red", "value": 0 },
                { "color": "yellow", "value": 0.95 },
                { "color": "green", "value": 0.99 }
              ]
            }
          }
        }
      },
      {
        "id": 5,
        "title": "Connection Pool",
        "type": "timeseries",
        "gridPos": { "x": 16, "y": 8, "w": 8, "h": 6 },
        "targets": [
          {
            "expr": "mongodb_connection_pool_size",
            "legendFormat": "pool size"
          },
          {
            "expr": "mongodb_connection_pool_checked_out",
            "legendFormat": "checked out"
          },
          {
            "expr": "mongodb_connection_pool_available",
            "legendFormat": "available"
          }
        ]
      },
      {
        "id": 6,
        "title": "Idempotent Skips (Out-of-Order Events)",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 14, "w": 12, "h": 6 },
        "targets": [
          {
            "expr": "sum(rate(cdc_mongodb_idempotent_skip_total[5m])) by (collection)",
            "legendFormat": "{{collection}}"
          }
        ]
      },
      {
        "id": 7,
        "title": "Error Rate",
        "type": "timeseries",
        "gridPos": { "x": 12, "y": 14, "w": 12, "h": 6 },
        "targets": [
          {
            "expr": "sum(rate(cdc_mongodb_errors_total[5m])) by (collection, error_type)",
            "legendFormat": "{{collection}} - {{error_type}}"
          }
        ]
      }
    ]
  }
}
```

### Service Map Dashboard (service-map.json)

```json
{
  "dashboard": {
    "title": "Service Map & Traces",
    "uid": "service-map",
    "tags": ["cdc", "tracing", "tempo"],
    "refresh": "30s",
    "panels": [
      {
        "id": 1,
        "title": "Service Dependency Graph",
        "type": "nodeGraph",
        "gridPos": { "x": 0, "y": 0, "w": 24, "h": 12 },
        "datasource": { "type": "tempo", "uid": "tempo" },
        "targets": [
          {
            "queryType": "serviceMap"
          }
        ]
      },
      {
        "id": 2,
        "title": "Trace Search",
        "type": "traces",
        "gridPos": { "x": 0, "y": 12, "w": 24, "h": 8 },
        "datasource": { "type": "tempo", "uid": "tempo" },
        "targets": [
          {
            "queryType": "traceql",
            "query": "{resource.service.name=\"cdc-debezium\"}"
          }
        ]
      },
      {
        "id": 3,
        "title": "Span Duration Histogram",
        "type": "histogram",
        "gridPos": { "x": 0, "y": 20, "w": 12, "h": 6 },
        "targets": [
          {
            "expr": "histogram_quantile(0.95, sum(rate(traces_spanmetrics_latency_bucket{service=\"cdc-debezium\"}[5m])) by (le, span_name))",
            "legendFormat": "{{span_name}}"
          }
        ]
      },
      {
        "id": 4,
        "title": "Error Traces",
        "type": "table",
        "gridPos": { "x": 12, "y": 20, "w": 12, "h": 6 },
        "datasource": { "type": "tempo", "uid": "tempo" },
        "targets": [
          {
            "queryType": "traceql",
            "query": "{resource.service.name=\"cdc-debezium\" && status=error}",
            "limit": 10
          }
        ]
      }
    ]
  }
}
```

### Logs Explorer Dashboard (logs-explorer.json)

```json
{
  "dashboard": {
    "title": "Logs Explorer",
    "uid": "logs-explorer",
    "tags": ["cdc", "logs", "loki"],
    "refresh": "10s",
    "panels": [
      {
        "id": 1,
        "title": "Log Volume by Level",
        "type": "timeseries",
        "gridPos": { "x": 0, "y": 0, "w": 24, "h": 6 },
        "datasource": { "type": "loki", "uid": "loki" },
        "targets": [
          {
            "expr": "sum(count_over_time({service_name=\"cdc-debezium\"}[1m])) by (level)",
            "legendFormat": "{{level}}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "custom": {
              "drawStyle": "bars",
              "stacking": { "mode": "normal" }
            }
          },
          "overrides": [
            { "matcher": { "id": "byName", "options": "error" }, "properties": [{ "id": "color", "value": { "fixedColor": "red", "mode": "fixed" } }] },
            { "matcher": { "id": "byName", "options": "warn" }, "properties": [{ "id": "color", "value": { "fixedColor": "yellow", "mode": "fixed" } }] },
            { "matcher": { "id": "byName", "options": "info" }, "properties": [{ "id": "color", "value": { "fixedColor": "green", "mode": "fixed" } }] }
          ]
        }
      },
      {
        "id": 2,
        "title": "Application Logs",
        "type": "logs",
        "gridPos": { "x": 0, "y": 6, "w": 24, "h": 12 },
        "datasource": { "type": "loki", "uid": "loki" },
        "targets": [
          {
            "expr": "{service_name=\"cdc-debezium\"} | json | line_format \"{{.timestamp}} [{{.level}}] {{.logger}} - {{.message}}\"",
            "legendFormat": ""
          }
        ],
        "options": {
          "showTime": true,
          "showLabels": true,
          "wrapLogMessage": true,
          "prettifyLogMessage": true,
          "enableLogDetails": true
        }
      },
      {
        "id": 3,
        "title": "Error Logs with Trace Links",
        "type": "logs",
        "gridPos": { "x": 0, "y": 18, "w": 24, "h": 8 },
        "datasource": { "type": "loki", "uid": "loki" },
        "targets": [
          {
            "expr": "{service_name=\"cdc-debezium\"} |= \"ERROR\" | json",
            "legendFormat": ""
          }
        ],
        "options": {
          "showTime": true,
          "enableLogDetails": true
        }
      }
    ],
    "templating": {
      "list": [
        {
          "name": "level",
          "type": "custom",
          "options": [
            { "text": "All", "value": "" },
            { "text": "Error", "value": "error" },
            { "text": "Warn", "value": "warn" },
            { "text": "Info", "value": "info" },
            { "text": "Debug", "value": "debug" }
          ],
          "current": { "text": "All", "value": "" }
        },
        {
          "name": "search",
          "type": "textbox",
          "label": "Search"
        }
      ]
    }
  }
}
```

## Directory Structure

```
docker/grafana/provisioning/dashboards/
├── dashboards.yml
└── json/
    ├── cdc-overview.json
    ├── consumer-performance.json
    ├── mongodb-operations.json
    ├── service-map.json
    └── logs-explorer.json
```

## Commands to Run

```bash
# Create dashboard directory
mkdir -p docker/grafana/provisioning/dashboards/json

# Start Grafana with dashboards
docker compose up -d grafana

# Wait for Grafana to be ready
until curl -sf http://localhost:3000/api/health; do sleep 2; done

# List provisioned dashboards
curl -u admin:admin http://localhost:3000/api/search?type=dash-db

# Open specific dashboard
open "http://localhost:3000/d/cdc-overview/cdc-pipeline-overview"

# Verify dashboard loading
curl -u admin:admin http://localhost:3000/api/dashboards/uid/cdc-overview | jq '.dashboard.title'

# Export dashboard (for backup)
curl -u admin:admin http://localhost:3000/api/dashboards/uid/cdc-overview | jq '.dashboard' > backup-cdc-overview.json

# Test dashboard with sample data
# Generate some CDC events first
docker compose exec postgres psql -U postgres -c \
  "INSERT INTO customer (id, email, status) VALUES
   (gen_random_uuid(), 'dash-test@example.com', 'active');"

# Wait and check dashboard
sleep 5
open "http://localhost:3000/d/cdc-overview"
```

## Acceptance Criteria

- [ ] CDC Overview dashboard loads successfully with panels for events processed, consumer lag, and errors
- [ ] Dashboard displays real-time CDC metrics (updates every 10 seconds)
- [ ] Consumer Performance dashboard shows Kafka consumer lag by partition and records consumed rate
- [ ] MongoDB Operations dashboard shows operations by type and latency percentiles
- [ ] Service Map displays trace topology from Tempo
- [ ] Logs Explorer shows application logs grouped by level with search filtering
- [ ] Dashboard variables work correctly (filtering by entity_type)
- [ ] Trace links work from logs (click through to Tempo)
- [ ] Dashboard panels link to each other for drill-down navigation
- [ ] Dashboards persist across container restarts

## Panel Types Reference

```mermaid
flowchart LR
    subgraph STAT["Stat Panels"]
        S1[Single Value]
        S2[Sparkline]
    end

    subgraph TIME["Time Series"]
        T1[Line Graph]
        T2[Stacked Area]
        T3[Bars]
    end

    subgraph SPECIAL["Special Types"]
        SP1[Gauge]
        SP2[Heatmap]
        SP3[Node Graph]
        SP4[Logs]
        SP5[Traces]
    end

    METRICS[Prometheus Metrics] --> STAT
    METRICS --> TIME
    METRICS --> SP1
    METRICS --> SP2

    TRACES[Tempo Traces] --> SP3
    TRACES --> SP5

    LOGS[Loki Logs] --> SP4
    LOGS --> TIME
```

## Estimated Complexity

Medium - Multiple dashboards with various panel types and cross-data source linking.

## Notes

- Dashboards use UID for stable referencing
- Variables enable filtering across all panels
- Refresh interval set to 10s for near-real-time monitoring
- Error annotations overlay on time series panels
- Trace-to-logs and logs-to-trace linking configured via data source settings
- Consider adding alerting rules directly in dashboards (PLAN-021)
- Dashboard JSON can be exported/imported for versioning
- Production: Consider Grafana Organizations for multi-tenant setups
