# OpenNMS Prometheus Alertmanager Bridge

A Spring Boot 3.5 application that bridges OpenNMS alarms to Prometheus Alertmanager, enabling unified alerting and visualization through Grafana.

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

This application subscribes to OpenNMS Kafka topics (alarms and nodes) and forwards alarm data to Prometheus Alertmanager. It enriches alerts with full OpenNMS node information, including custom metadata.

## Features

- **Kafka Consumer**: Subscribes to OpenNMS `alarms` and `nodes` topics
- **Protobuf Decoding**: Parses GPB-encoded messages from OpenNMS
- **Node Enrichment**: Caches node data including custom metadata
- **Alertmanager Integration**: Forwards alarms via Alertmanager API v2
- **Automatic Resend**: Periodically resends active alerts
- **Metrics Export**: Prometheus metrics at `/actuator/prometheus`

## Quick Start

```bash
# Build
make oci

# Run with Docker Compose (full stack)
cd example/docker
docker compose up -d
```

## Configuration

Key environment variables:

| Variable                  | Description      | Default                         |
|---------------------------|------------------|---------------------------------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers    | `localhost:9092`                |
| `ALERTMANAGER_URL`        | Alertmanager URL | `http://localhost:9093`         |
| `OPENNMS_URL`             | OpenNMS URL      | `http://localhost:8980/opennms` |

## Alert Labels

| Label                   | Source           |
|-------------------------|------------------|
| `alertname`             | Derived from UEI |
| `severity`              | OpenNMS severity |
| `node_id`, `node_label` | Node info        |
| `opennms_categories`    | Node categories  |
| `opennms_meta_*`        | Custom metadata  |

## API Endpoints

- `GET /api/v1/bridge/status` - Bridge status
- `GET /api/v1/bridge/alarms` - Active alarms
- `GET /api/v1/bridge/nodes` - Cached nodes
- `POST /api/v1/bridge/alarms/resend` - Force resend

## Create and publish a new release

To make a release the following steps are required:

1. Set the Maven project version without -SNAPSHOT
2. Make a version tag with git
3. Set a new SNAPSHOT version in the main branch
4. Publish a release

To help you with these steps you can run a make goal `make release`.
It requires a version number you want to release.
As an example the current main branch has 0.0.2-SNAPSHOT and you want to release 0.0.2 you need to run

```shell
make release RELEASE_VERSION=0.0.2
```

The 0.0.2 version is set with the git tag v0.0.2.
It will automatically set the main branch to 0.0.3-SNAPSHOT for the next iteration.
All changes stay in your local repository.
When you want to publish the new released version you need to run

```shell
git push                # Push the main branch with the new -SNAPSHOT version
git push origin v0.0.2  # Push the release tag which triggers the build which publishes artifacts.
```

## License

Apache License 2.0
