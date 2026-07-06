package ch.admin.bit.jeap.jme.tracing;

import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class RestExample {

    private final LogServiceClient logServiceClient;
    private final Tracer tracer;

    RestExample(LogServiceClient logServiceClient, Tracer tracer) {
        this.logServiceClient = logServiceClient;
        this.tracer = tracer;
    }

    @GetMapping(path = "/")
    public String callLogServiceIndex() {
        log.info("Making a REST call to the log service index page");
        // Tracing is provided by jeap-spring-boot-monitoring-starter via Micrometer Tracing on top of OpenTelemetry.
        // Each HTTP hop produces its own client and server span; the W3C traceparent header (and B3 for backwards
        // compatibility) propagates the trace id between services so the log lines in the services correlate.
        logServiceClient.index();
        return "ok";
    }

    @GetMapping(path = "tracing")
    public String callLogServiceDocumentEndpoint() {
        log.info("Making a GET call for a document by id");
        logServiceClient.getDocument("1234");

        log.info("Making a GET call for a document by id and type");
        logServiceClient.getDocumentContent("1234", "pdf");

        log.info("Making a POST call for a document by id and type");
        logServiceClient.postDocumentContent("1234", "pdf");

        return "ok";
    }

    @GetMapping(path = "tracing/{value}")
    public String tracing(@PathVariable("value") Integer value) {
        log.info("Making a GET call to tracing{}", value);
        logServiceClient.tracing(value);
        return "ok";
    }

    /**
     * Demonstrates the creation of a custom span.
     */
    @GetMapping(path = "span")
    public String span(@RequestParam(required = false, value = "spanName") String spanName) {
        if (!StringUtils.hasText(spanName)) {
            spanName = "default-custom-span";
        }
        log.info("Starting a custom span with name {}", spanName);
        // Create a child of the current (request) span and put it in scope. While in scope,
        // it is the active span: the OTel/Micrometer-Tracing logback bridge puts its span id
        // into the MDC, so log lines emitted here carry the new span id, and any nested
        // operations (e.g. an outbound HTTP call) attach to this span.
        Span span = tracer.nextSpan().name(spanName).start();
        // Always use try with resources to ensure that the tracing scope of the custom span is closed
        try (Tracer.SpanInScope _ = tracer.withSpan(span)) {
            // Adding a tag to the span.
            // Tags can make additional information available for filtering and aggregation in the tracing UI.
            span.tag("demo.foo", "bar");
            log.info("Within custom span with name {}", spanName);
        } finally {
            span.end();
        }
        log.info("Back outside of custom span with name {}", spanName);
        return "ok";
    }

    /**
     * Demonstrates Micrometer Observation's {@code @Observed} annotation.
     * <p>
     * Each call produces both a <strong>span</strong> (visible on the trace in Grafana)
     * and a <strong>Timer metric</strong> (visible at {@code /actuator/prometheus}),
     * i.e. the annotation is feeding two observability pillars at the same time (metrics, tracing).
     * The metric records the method's call duration and frequency, the span records
     * the method's place in a surrounding trace.
     * <p>
     * The {@code highCardinalityValue} request parameter shows how to add a high-cardinality tag just to the span and
     * not to the metric. It is set programmatically via {@link Span#tag(String, String)} on the current span only, so
     * it does not become a high-cardinality dimension on the Timer metric.
     */
    @GetMapping(path = "observed")
    @Observed(
            // Used as the metric name (after Micrometer's dot-to-underscore conversion for Prometheus: "demo_observed_*"),
            // and as the span name (unless overridden by contextualName).
            name = "demo.observed",
            // Span name. Lets the span carry a human-friendly label while the metric keeps its machine-friendly name.
            contextualName = "observed-endpoint",
            // Pairs of strings (key, value, key, value, ...) added as tags on BOTH the metric and the span.
            // Use only for key/value pairs where the value comes from a small, bounded set! Otherwise, too many Prometheus
            // time series would be created. High-cardinality key values are fine on a trace, though. See example below.
            lowCardinalityKeyValues = {"demo.business_function", "BF-12345"})
    public String observed(@RequestParam String highCardinalityValue) {
        log.info("Inside @Observed-annotated method");
        // tracer.currentSpan() returns the span the @Observed annotation just opened.
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            // Attach the high-cardinality value to the current span. Only affects the tracing data not the metrics data.
            currentSpan.tag("demo.high_cardinality_key", highCardinalityValue);
        }
        return "ok";
    }
}
