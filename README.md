# OpenNMS to Prometheus Alertmanager Bridge

A Spring Boot 3.5 application that bridges OpenNMS alarms to Prometheus Alertmanager, enabling unified alerting and visualization through Grafana.

## Overview

This application subscribes to OpenNMS Kafka topics (alarms and nodes) and forwards alarm data to Prometheus Alertmanager. It enriches alerts with full OpenNMS node information, including custom metadata.

### Features

- **Kafka Consumer**: Subscribes to OpenNMS `alarms` and `nodes` topics
- **Protobuf Decoding**: Parses GPB-encoded messages from OpenNMS
- **Node Enrichment**: Caches node data including custom metadata
- **Alertmanager Integration**: Forwards alarms via Alertmanager API v2
- **Automatic Resend**: Periodically resends active alerts
- **Metrics Export**: Prometheus metrics at `/actuator/prometheus`

## Quick Start

```bash
# Build
./build.sh -c

# Run with Docker Compose (full stack)
docker-compose up -d
```

## Configuration

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `ALERTMANAGER_URL` | Alertmanager URL | `http://localhost:9093` |
| `OPENNMS_URL` | OpenNMS URL | `http://localhost:8980/opennms` |

## Alert Labels

| Label | Source |
|-------|--------|
| `alertname` | Derived from UEI |
| `severity` | OpenNMS severity |
| `node_id`, `node_label` | Node info |
| `opennms_categories` | Node categories |
| `opennms_meta_*` | Custom metadata |

## API Endpoints

- `GET /api/v1/bridge/status` - Bridge status
- `GET /api/v1/bridge/alarms` - Active alarms
- `GET /api/v1/bridge/nodes` - Cached nodes
- `POST /api/v1/bridge/alarms/resend` - Force resend

## License

Apache License 2.0
