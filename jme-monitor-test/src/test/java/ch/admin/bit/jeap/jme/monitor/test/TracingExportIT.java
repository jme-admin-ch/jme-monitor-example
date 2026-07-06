package ch.admin.bit.jeap.jme.monitor.test;

import ch.admin.bit.jeap.jme.test.BootServiceSpringIntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that OpenTelemetry tracing is actually wired up end to end: that spans are exported
 * over OTLP/HTTP to a real collector, that a cross-service call correlates spans from both
 * services under a shared trace id, and that the manual-span and {@code @Observed} patterns shown
 * in {@code RestExample} produce the spans/attributes they claim to.
 * <p>
 * Unlike {@link MonitorExampleIT}, which only asserts on HTTP responses, this test relies on the
 * same {@code docker/docker-compose.yml} backend the README tells developers to run locally (OTel
 * Collector + Tempo, bundled in the grafana/otel-lgtm image) — started/stopped automatically via
 * {@code application.yml}'s {@code spring.docker.compose.*} settings — and queries Tempo's HTTP
 * API, via {@link TempoClient}, to inspect what was actually exported.
 */
class TracingExportIT extends BootServiceSpringIntegrationTestBase {

    private static final String LOG_BASE_URL = "http://localhost:8090/jme-log-service";
    private static final String TRACING_BASE_URL = "http://localhost:8091/jme-tracing-service";
    private static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(60);

    private final TempoClient tempo = new TempoClient();

    @BeforeAll
    static void startServices() throws Exception {
        startService("jme-log-service", LOG_BASE_URL);
        startService("jme-tracing-service", TRACING_BASE_URL);
    }

    @Test
    void crossServiceCallProducesCorrelatedSpans() {
        long since = System.currentTimeMillis() * 1_000_000L;

        given().baseUri(TRACING_BASE_URL).when().get("/tracing").then().statusCode(200);

        await().atMost(EXPORT_TIMEOUT).untilAsserted(() -> {
            List<TempoClient.SpanInfo> spans = spansExportedSince(since, "jme-tracing-service");

            Set<String> tracingServiceNames = spans.stream()
                    .map(TempoClient.SpanInfo::serviceName)
                    .collect(Collectors.toSet());

            assertThat(tracingServiceNames)
                    .as("a trace involving jme-tracing-service should also include spans from jme-log-service")
                    .contains("jme-tracing-service", "jme-log-service");
        });
    }

    @Test
    void customSpanIsExportedWithNameAndTag() {
        long since = System.currentTimeMillis() * 1_000_000L;

        given().baseUri(TRACING_BASE_URL).when().get("/span?spanName=it-otel-custom-span").then().statusCode(200);

        await().atMost(EXPORT_TIMEOUT).untilAsserted(() -> {
            List<TempoClient.SpanInfo> spans = spansExportedSince(since, "jme-tracing-service");

            assertThat(spans)
                    .filteredOn(span -> span.name().equals("it-otel-custom-span"))
                    .as("the manually created span should be exported with its name and 'demo.foo' tag")
                    .anySatisfy(span -> assertThat(span.attributes()).containsEntry("demo.foo", "bar"));
        });
    }

    @Test
    void observedAnnotationProducesSpanWithLowCardinalityTag() {
        long since = System.currentTimeMillis() * 1_000_000L;

        given().baseUri(TRACING_BASE_URL)
                .queryParam("highCardinalityValue", "it-otel-value")
                .when().get("/observed")
                .then().statusCode(200);

        await().atMost(EXPORT_TIMEOUT).untilAsserted(() -> {
            List<TempoClient.SpanInfo> spans = spansExportedSince(since, "jme-tracing-service");

            assertThat(spans)
                    .filteredOn(span -> span.name().equals("observed-endpoint"))
                    .as("the @Observed-annotated endpoint should export a span named after its contextualName")
                    .anySatisfy(span -> assertThat(span.attributes()).containsEntry("demo.business_function", "BF-12345"));
        });
    }

    /**
     * All spans, from every participating service, of every trace involving {@code serviceName}
     * that started at or after {@code sinceEpochNanos}.
     */
    private List<TempoClient.SpanInfo> spansExportedSince(long sinceEpochNanos, String serviceName) {
        return tempo.traceIdsSince(serviceName, sinceEpochNanos).stream()
                .flatMap(traceId -> tempo.spansOf(traceId).stream())
                .toList();
    }
}
