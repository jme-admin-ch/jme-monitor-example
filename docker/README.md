# Local OpenTelemetry backend for the demo

This directory contains a [`docker-compose.yml`](docker-compose.yml) that starts a
local OpenTelemetry backend. The services in this repository push their traces
to it via OTLP when the `local` Spring profile is active, and you can browse
them in Grafana.

## What it runs

A single container based on the [`grafana/otel-lgtm`](https://github.com/grafana/docker-otel-lgtm)
image. It bundles:

- **OpenTelemetry Collector** — receives OTLP on port 4317 (gRPC) and 4318 (HTTP)
- **Tempo** — stores traces (in-memory, wiped on container restart)
- **Grafana** — viewer, with the Tempo datasource pre-provisioned
- (Loki, Prometheus and Pyroscope are also bundled but not used by this demo)

The image is Apache-2.0 and explicitly intended for development and demo use.

## Starting and stopping

Start (from the repository root):
```bash
docker compose -f docker/docker-compose.yml up -d
```
Stop and remove (state is in-memory, so this also clears all traces)
```bash
docker compose -f docker/docker-compose.yml down
```
After the container is up:

| What | Where |
|---|---|
| Grafana UI | http://localhost:3000 (login `admin` / `admin`) |
| OTLP gRPC endpoint | `localhost:4317` |
| OTLP HTTP endpoint | `localhost:4318` |
| Tempo HTTP API | `localhost:3200` |

The Tempo HTTP API (`/api/search`, `/api/traces/{traceID}`) is also what `jme-monitor-test`'s
`TracingExportIT` queries directly to assert on exported spans — it starts this same
docker-compose file itself, so no manual setup is needed to run that test.
