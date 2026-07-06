# JME Monitor Example

This example shows how to set up logging, distributed tracing and technical monitoring for jEAP Blueprint
Microservices. It consists of three services and a local OpenTelemetry backend:

* jme-log-service: A simple example how to generate logs. Provides a REST-Interface
  that generates a log statement. Also includes a `GlobalExceptionHandler` that
  demonstrates how to log unhandled exceptions inside the request scope so the
  trace id stays in the MDC — see the class javadoc for the why.
* jme-tracing-service: An example how to use distributed tracing to match log lines of
  different services. Provides a REST interface that generates a log statement and then
  calls jme-log-service where another log statement is generated. Those two statements can
  then be linked using a tracing-ID
* jme-prometheus-service: An example how to integrate a prometheus endpoint for
  technical monitoring
* a local OpenTelemetry backend with a Grafana UI to inspect traces (see `docker/README.md` for details)

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Prerequisites

To use this project, ensure you have the following installed:

1. **Java Development Kit (JDK)**: Version 25.
2. **Docker**: For running the local OpenTelemetry backend.

**Note:** Use the provided maven wrapper to build and run the project.

## Getting started

### Build

The project itself can be built with a simple

```shell
./mvnw install
```

### Starting Order

* The local OpenTelemetry backend must be started before the services:
  ```shell
  docker compose -f docker/docker-compose.yml up -d
  ```
* The log service can be started independently of the other two services and offers a rest interface at
  http://localhost:8090/jme-log-service.
  ```shell
  ./mvnw -pl jme-log-service spring-boot:run -Dspring-boot.run.profiles=local
  ```
* The tracing service must be started after the log service and offers a rest interface at
  http://localhost:8091/jme-tracing-service.
  ```shell
  ./mvnw -pl jme-tracing-service spring-boot:run -Dspring-boot.run.profiles=local
  ```
* The prometheus service can be started independently of the other two services and offers a prometheus endpoint at
  http://localhost:8092/jme-prometheus-service/actuator/prometheus (User: prometheus, PWD: test)
  and an info endpoint at http://localhost:8092/jme-prometheus-service/actuator/info.
  ```shell
  ./mvnw -pl jme-prometheus-service spring-boot:run -Dspring-boot.run.profiles=local
  ```
  Besides the standard information, the example provides the following additional information:
    * Some dynamic info in the info endpoint (see ExampleInfoContributor)
    * Some static info in the info endpoint (see application.yml)
    * Some metrics about rest calls to http://localhost:8092/jme-prometheus-service/test (see `TestController`)
    * Counters, gauges, distribution summaries and timers around a small CRUD-like API at
      http://localhost:8092/jme-prometheus-service/api/jobs (see `JobController` — also showcases
      `@Timed` and tagged metrics)

## Tracing Example

### How do the services provide tracing information to the local OpenTelemetry backend?

Each service's `application-local.yml` sets:

```yaml
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
```

That single property is what flips on OTLP export. Without it, Spring Boot
still generates spans and adds the trace/span id to the MDC (so they appear in
log lines), but nothing is shipped to a backend.

The default sampling probability is overridden to `1.0` in each service's
`application.yml` so every demo call produces a trace — the Spring Boot
default of 0.1 would be confusing here.

### How does jme-tracing-service know where jme-log-service runs?

The tracing service talks to the log service through a declarative HTTP
interface (`LogServiceClient`, annotated with `@HttpExchange`). Spring builds
the implementation at runtime via `RestClientAdapter`, wired up in
`LogServiceClientConfig`, which reads the base URL from a `loguri` property
(`@Value("${loguri}")`). The value is set per profile: `application-local.yml`
points at `http://localhost:8090/jme-log-service/`.

### Running an end-to-end demo

1. Start the backend:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   ```
2. Start `jme-log-service` (port 8090) and `jme-tracing-service` (port 8091)
   with the `local` profile, each in its own terminal from the repository root:
   ```bash
   ./mvnw -pl jme-log-service spring-boot:run -Dspring-boot.run.profiles=local
   ```
   ```bash
   ./mvnw -pl jme-tracing-service spring-boot:run -Dspring-boot.run.profiles=local
   ```
3. Trigger a cross-service call:
   ```bash
   curl http://localhost:8091/jme-tracing-service/tracing
   ```
4. Open Grafana at <http://localhost:3000> → **Explore** → datasource **Tempo**
   → **Search**, pick `jme-tracing-service` from the *Service Name* dropdown,
   click **Run query**.
5. Click a trace. You should see spans from `jme-tracing-service` *and*
   `jme-log-service` linked under the same trace id, demonstrating that the
   W3C `traceparent` header propagated across the HTTP hop.

### Verifying trace propagation in logs

Both services log the trace id in the MDC (look at the
`[service-name,traceId,spanId]` prefix in each log line). For one cross-service
call you should see the same `traceId` appear in both services' logs — that is
the same id that identifies the trace in Grafana.

### How to create custom spans

#### Using the `Tracer` API
The jeap-monitoring starter wires up a Micrometer Tracing `Tracer` bean
(backed by OpenTelemetry) that you can inject and use to add your own spans
around a unit of work, typically to delineate a step that's interesting to
see separately in Grafana, or to attach business attributes (customer id,
document id, etc.) to a slice of the trace.

`jme-tracing-service` shows the pattern in `RestExample.span(...)` (called via
`GET /jme-tracing-service/span?spanName=demoSpan`):

Try it:
```bash
curl 'http://localhost:8091/jme-tracing-service/span?spanName=my-custom-span'
```

In the service log you will see three lines on the same trace id but with
**two different span ids**: the "Starting…" and "Back outside…" lines share
the request span id; the "Within…" line has the custom span's id. In Grafana
the trace contains a child span named `my-custom-span` with the `demo.foo` tag
attached.

#### Using the `@Observed` annotation
A less verbose option, especially for instrumenting a whole method, is Micrometer's
`@Observed` annotation. `jme-tracing-service` shows this on `RestExample.observed()`.
Note however that a single `@Observed` annotation produces both a span (visible
in the tracing data) and a Timer metric (visible in metrics data).

Reach for the manual `Tracer` API instead when you don't need the additional Timer metrics or need finer control.
With the API, you can create a span around a *block* rather than a whole method. In addition, it supports dynamic naming,
high-cardinality tags sourced from method parameters, and `span.error(throwable)` on exceptions.

### Troubleshooting

- **Traces from previous runs pollute the search.** Tempo is in-memory in
  this image; `docker compose down` clears it. `docker compose restart lgtm`
  also wipes state.
- **Connection refused on 4318.** The container exposes ports on `localhost`.
  If you run the services inside another container or on a different host,
  swap `localhost` for the appropriate hostname.

## Integration Tests

The `jme-monitor-test` module contains end-to-end integration tests that start all three services locally (via
`mvnw spring-boot:run`) and exercise their REST interfaces, cross-service tracing call, and Prometheus/actuator
endpoints.

### Running locally

```shell
# Build and install all local modules
./mvnw install -pl '!:jme-monitor-test'
# Run integration tests
./mvnw verify -pl jme-monitor-test
```

This will:

1. Build and start the three Spring Boot services on ports 8090, 8091 and 8092.
2. Run the integration tests.
3. Stop all services.

Ensure ports 8090–8092 are available.

### Running on CI

On CI the `CI` environment variable must be set. This activates the `ci` Spring profile.

## Note

This repository is part of the open source distribution of JME. See [github.com/jme-admin-ch/jme](https://github.com/jme-admin-ch/jme)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).

## Documentation
https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-logging-starter
https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-monitoring-starter
https://jeap-admin-ch.github.io/docs/building-blocks/spring-boot-starters/jeap-spring-boot-starters/jeap-spring-boot-rest-request-tracing
