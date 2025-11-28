# OpenNMS to Prometheus Alertmanager Bridge

A Spring Boot 3.5 application that bridges OpenNMS alarms to Prometheus Alertmanager, enabling unified alerting and visualization through Grafana.

## Overview

This application subscribes to OpenNMS Kafka topics (alarms and nodes) and forwards alarm data to Prometheus Alertmanager. It enriches alerts with full OpenNMS node information, including custom metadata, making it available for display in Grafana dashboards.

### Features

- **Kafka Consumer**: Subscribes to OpenNMS `alarms` and `nodes` topics
- **Protobuf Decoding**: Parses Google Protocol Buffer encoded messages from OpenNMS
- **Node Enrichment**: Caches node data including custom metadata for alarm enrichment
- **Alertmanager Integration**: Forwards alarms as Prometheus alerts via Alertmanager API v2
- **Automatic Resend**: Periodically resends active alerts (required by Alertmanager)
- **Alert Resolution**: Handles cleared alarms and tombstones (deleted alarms)
- **Grafana Ready**: Full node metadata available as labels/annotations for Grafana dashboards
- **Metrics Export**: Prometheus metrics for monitoring the bridge itself
- **Container Ready**: Dockerfile and Docker Compose for easy deployment

## Architecture

```
┌─────────────────┐     ┌───────────────────┐     ┌─────────────────────┐
│    OpenNMS      │────▶│   Kafka Topics    │────▶│  Alertmanager       │
│   (Horizon)     │     │  - alarms         │     │      Bridge         │
│                 │     │  - nodes          │     │  (This App)         │
└─────────────────┘     └───────────────────┘     └──────────┬──────────┘
                                                             │
                                                             ▼
                        ┌───────────────────┐     ┌─────────────────────┐
                        │     Grafana       │◀────│   Prometheus        │
                        │   Dashboard       │     │   Alertmanager      │
                        └───────────────────┘     └─────────────────────┘
```

## Prerequisites

- Java 21+
- Apache Kafka with OpenNMS topics
- Prometheus Alertmanager
- OpenNMS Horizon 35.x with Kafka Producer enabled

## Configuration

### OpenNMS Kafka Producer

Enable the Kafka Producer in OpenNMS:

```bash
ssh -p 8101 admin@opennms

config:edit org.opennms.features.kafka.producer.client
config:property-set bootstrap.servers kafka:9092
config:update

feature:install opennms-kafka-producer
```

### Application Configuration

Configure via `application.yml` or environment variables:

```yaml
opennms:
  bridge:
    alertmanager:
      url: http://alertmanager:9093
      enabled: true
    topics:
      alarms: alarms
      nodes: nodes
    alert:
      resend-interval: 60000  # ms
      static-labels:
        environment: production
      label-mappings:
        include-node-metadata: true
        include-node-categories: true
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `KAFKA_CONSUMER_GROUP` | Consumer group ID | `opennms-alertmanager-bridge` |
| `KAFKA_TOPIC_ALARMS` | Alarms topic name | `alarms` |
| `KAFKA_TOPIC_NODES` | Nodes topic name | `nodes` |
| `ALERTMANAGER_URL` | Alertmanager base URL | `http://localhost:9093` |
| `ALERTMANAGER_ENABLED` | Enable/disable forwarding | `true` |
| `OPENNMS_URL` | OpenNMS URL for links | `http://localhost:8980/opennms` |
| `ALERT_RESEND_INTERVAL` | Resend interval (ms) | `60000` |
| `LOG_LEVEL` | Application log level | `INFO` |

## Building

### With Maven

```bash
./mvnw clean package
```

### With Docker

```bash
docker build -t opennms-alertmanager-bridge:latest .
```

## Running

### Local Development

```bash
./mvnw spring-boot:run
```

### Docker

```bash
docker run -d \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e ALERTMANAGER_URL=http://alertmanager:9093 \
  -p 8080:8080 \
  opennms-alertmanager-bridge:latest
```

### Docker Compose (Full Stack)

```bash
docker-compose up -d
```

This starts:
- Zookeeper & Kafka
- Prometheus Alertmanager
- Prometheus
- Grafana (with Alertmanager datasource plugin)
- The bridge application

## Alert Mapping

### Labels (Identifying Information)

OpenNMS alarms are mapped to Alertmanager alerts with the following labels:

| Label | Source |
|-------|--------|
| `alertname` | Derived from UEI (e.g., `opennms_nodes_nodeDown`) |
| `severity` | OpenNMS severity (critical, major, minor, warning, info) |
| `node_id` | Node ID |
| `node_label` | Node label |
| `foreign_source` | Foreign source |
| `foreign_id` | Foreign ID |
| `location` | Node location |
| `instance` | IP address |
| `service` | Service name |
| `opennms_alarm_id` | OpenNMS alarm ID |
| `opennms_reduction_key` | Alarm reduction key |
| `opennms_categories` | Node categories (comma-separated) |
| `opennms_meta_*` | Custom node metadata |

### Annotations (Additional Information)

| Annotation | Content |
|------------|---------|
| `summary` | Alarm log message |
| `description` | Alarm description |
| `runbook` | Operator instructions |
| `opennms_node` | Full node details as JSON |
| `alarm_count` | Alarm occurrence count |
| `acknowledged_by` | User who acknowledged |

## Grafana Integration

### Datasource Setup

1. Install the `camptocamp-prometheus-alertmanager-datasource` plugin
2. Add Alertmanager as a datasource pointing to your Alertmanager URL
3. Create dashboards using the alert data

### Dashboard Example

The project includes a sample dashboard (`grafana/provisioning/dashboards/json/opennms-alerts.json`) that displays:

- Alert counts by severity
- Table of active OpenNMS alerts
- Bridge throughput metrics
- Bridge state (active alarms, cached nodes)

### Querying Node Metadata

Node metadata is available as labels prefixed with `opennms_meta_`. For example, if a node has metadata:
- Context: `requisition`
- Key: `site`
- Value: `datacenter-1`

It becomes the label: `opennms_meta_requisition_site="datacenter-1"`

You can filter alerts in Grafana using: `opennms_meta_requisition_site="datacenter-1"`

## API Endpoints

### Bridge Status

```bash
GET /api/v1/bridge/status
```

### Active Alarms

```bash
GET /api/v1/bridge/alarms
```

### Cached Nodes

```bash
GET /api/v1/bridge/nodes
GET /api/v1/bridge/nodes/{nodeId}
```

### Manual Resend

```bash
POST /api/v1/bridge/alarms/resend
```

### Alertmanager Status

```bash
GET /api/v1/bridge/alertmanager/status
```

## Metrics

The bridge exposes Prometheus metrics at `/actuator/prometheus`:

| Metric | Description |
|--------|-------------|
| `opennms_bridge_alarms_received_total` | Alarms received from Kafka |
| `opennms_bridge_alarms_parsed_total` | Alarms successfully parsed |
| `opennms_bridge_alerts_sent_total` | Alerts sent to Alertmanager |
| `opennms_bridge_alerts_failed_total` | Failed alert sends |
| `opennms_bridge_active_alarms` | Currently tracked active alarms |
| `opennms_bridge_node_cache_size` | Nodes in cache |

## Health Check

```bash
GET /actuator/health
```

## Troubleshooting

### No Alarms Received

1. Verify OpenNMS Kafka Producer is enabled
2. Check Kafka topic exists and has messages
3. Verify `KAFKA_BOOTSTRAP_SERVERS` is correct
4. Check consumer group offset

### Alerts Not Appearing in Alertmanager

1. Verify Alertmanager URL is correct
2. Check `ALERTMANAGER_ENABLED=true`
3. Review application logs for errors
4. Test Alertmanager connectivity: `GET /api/v1/bridge/alertmanager/status`

### Missing Node Metadata

1. Ensure nodes topic is being consumed
2. Check if node was received before alarm
3. Verify node metadata is present in OpenNMS
4. Review `label-mappings.include-node-metadata` setting

## License

Apache License 2.0

## Contributing

Contributions welcome! Please submit pull requests with tests.
