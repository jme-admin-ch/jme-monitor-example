package ch.admin.bit.jeap.jme.monitor.test;

import ch.admin.bit.jeap.jme.test.BootServiceIntegrationTestBase;
import io.restassured.path.json.JsonPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Thin client for the subset of Tempo's HTTP API used to verify what the services actually
 * exported: {@code /api/search} to find recently created traces for a given service, and
 * {@code /api/traces/{traceID}} to fetch the full trace (spans from all participating services)
 * for inspection. Both endpoints return plain JSON, no protobuf decoding required.
 */
class TempoClient {

    record SpanInfo(String serviceName, String name, Map<String, String> attributes) {
    }

    /**
     * On CI, docker-compose-ci.yml doesn't publish host ports (see its header comment for why), so
     * Tempo must be reached via the compose service name on the shared Docker network instead of localhost.
     */
    private static final String BASE_URL = BootServiceIntegrationTestBase.TestProfileResolver.isCI()
            ? "http://lgtm:3200"
            : "http://localhost:3200";

    /**
     * Trace ids for traces that involved the given service and started at or after {@code sinceEpochNanos}.
     */
    List<String> traceIdsSince(String serviceName, long sinceEpochNanos) {
        String body = given().baseUri(BASE_URL)
                .queryParam("tags", "service.name=" + serviceName)
                .when().get("/api/search")
                .then().statusCode(200)
                .extract().asString();

        List<Map<String, Object>> traces = JsonPath.from(body).getList("traces");
        List<String> traceIds = new ArrayList<>();
        for (Map<String, Object> trace : traces) {
            long startTimeUnixNano = Long.parseLong(String.valueOf(trace.get("startTimeUnixNano")));
            if (startTimeUnixNano >= sinceEpochNanos) {
                traceIds.add((String) trace.get("traceID"));
            }
        }
        return traceIds;
    }

    /**
     * All spans (from every service participating in the trace) contained in the given trace.
     */
    @SuppressWarnings("unchecked")
    List<SpanInfo> spansOf(String traceId) {
        String body = given().baseUri(BASE_URL)
                .when().get("/api/traces/{traceId}", traceId)
                .then().statusCode(200)
                .extract().asString();

        List<SpanInfo> spans = new ArrayList<>();
        List<Map<String, Object>> batches = JsonPath.from(body).getList("batches");
        for (Map<String, Object> batch : batches) {
            Map<String, Object> resource = (Map<String, Object>) batch.get("resource");
            String serviceName = stringAttribute((List<Map<String, Object>>) resource.get("attributes"), "service.name")
                    .orElse("unknown");
            for (Map<String, Object> scopeSpans : (List<Map<String, Object>>) batch.get("scopeSpans")) {
                for (Map<String, Object> span : (List<Map<String, Object>>) scopeSpans.get("spans")) {
                    Map<String, String> attributes = new HashMap<>();
                    List<Map<String, Object>> rawAttributes = (List<Map<String, Object>>) span.get("attributes");
                    if (rawAttributes != null) {
                        for (Map<String, Object> attribute : rawAttributes) {
                            stringValue(attribute).ifPresent(value -> attributes.put((String) attribute.get("key"), value));
                        }
                    }
                    spans.add(new SpanInfo(serviceName, (String) span.get("name"), attributes));
                }
            }
        }
        return spans;
    }

    private static java.util.Optional<String> stringAttribute(List<Map<String, Object>> attributes, String key) {
        if (attributes == null) {
            return java.util.Optional.empty();
        }
        return attributes.stream()
                .filter(attribute -> key.equals(attribute.get("key")))
                .findFirst()
                .flatMap(TempoClient::stringValue);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Optional<String> stringValue(Map<String, Object> attribute) {
        Object value = attribute.get("value");
        if (value instanceof Map<?, ?> valueMap) {
            Object stringValue = ((Map<String, Object>) valueMap).get("stringValue");
            if (stringValue instanceof String s) {
                return java.util.Optional.of(s);
            }
        }
        return java.util.Optional.empty();
    }
}
