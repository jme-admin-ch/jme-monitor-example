package ch.admin.bit.jeap.jme.monitor.test;

import ch.admin.bit.jeap.jme.test.BootServiceSpringIntegrationTestBase;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class MonitorExampleIT extends BootServiceSpringIntegrationTestBase {

    private static final String LOG_BASE_URL = "http://localhost:8090/jme-log-service";
    private static final String TRACING_BASE_URL = "http://localhost:8091/jme-tracing-service";
    private static final String PROMETHEUS_BASE_URL = "http://localhost:8092/jme-prometheus-service";

    @BeforeAll
    static void startServices() throws Exception {
        startService("jme-log-service", LOG_BASE_URL);
        startService("jme-tracing-service", TRACING_BASE_URL);
        startService("jme-prometheus-service", PROMETHEUS_BASE_URL);
    }

    @Test
    void logServiceGeneratesLogsForItsEndpoints() {
        given().baseUri(LOG_BASE_URL).when().get("/")
                .then().statusCode(HttpStatus.OK.value()).body(equalToOk());

        given().baseUri(LOG_BASE_URL).when().get("/document/1234")
                .then().statusCode(HttpStatus.OK.value()).body(equalToOk());
    }

    @Test
    void tracingServiceCallsLogServiceAcrossTheWire() {
        // Exercises the cross-service HTTP call from jme-tracing-service to jme-log-service.
        // If jme-log-service were unreachable or returned an error, LogServiceClient would throw
        // and this call would fail with a 5xx status.
        given().baseUri(TRACING_BASE_URL).when().get("/tracing")
                .then().statusCode(HttpStatus.OK.value()).body(equalToOk());
    }

    @Test
    void tracingServiceSupportsCustomSpans() {
        given().baseUri(TRACING_BASE_URL).when().get("/span?spanName=it-test-span")
                .then().statusCode(HttpStatus.OK.value()).body(equalToOk());
    }

    @Test
    void tracingServiceSupportsObservedAnnotation() {
        given().baseUri(TRACING_BASE_URL)
                .queryParam("highCardinalityValue", "it-test-value")
                .when().get("/observed")
                .then().statusCode(HttpStatus.OK.value()).body(equalToOk());
    }

    @Test
    void prometheusServiceExposesJobsCrudApiAndMetrics() {
        String jobId = UUID.randomUUID().toString();

        given().baseUri(PROMETHEUS_BASE_URL)
                .contentType(ContentType.URLENC)
                .formParam("id", jobId)
                .formParam("priority", "HIGH")
                .formParam("description", "it-test-job")
                .when().put("/api/jobs")
                .then().statusCode(HttpStatus.OK.value());

        given().baseUri(PROMETHEUS_BASE_URL).when().get("/api/jobs")
                .then().statusCode(HttpStatus.OK.value())
                .body("id", org.hamcrest.Matchers.hasItem(jobId));

        given().baseUri(PROMETHEUS_BASE_URL).when().delete("/api/jobs/{id}", jobId)
                .then().statusCode(HttpStatus.OK.value());

        String metrics = given().baseUri(PROMETHEUS_BASE_URL)
                .auth().preemptive().basic("prometheus", "test")
                .when().get("/actuator/prometheus")
                .then().statusCode(HttpStatus.OK.value())
                .extract().body().asString();
        assertThat(metrics).contains("jobs_available");
        assertThat(metrics).contains("jobs_remove_total");
    }

    @Test
    void prometheusServiceExposesCustomActuatorInfo() {
        Map<String, Object> info = given().baseUri(PROMETHEUS_BASE_URL).when().get("/actuator/info")
                .then().statusCode(HttpStatus.OK.value())
                .extract().jsonPath().getMap("$");
        assertThat(info).containsEntry("staticInfo", "value");
        assertThat(info).containsEntry("exampleWithoutChild", "value");
        @SuppressWarnings("unchecked")
        Map<String, Object> exampleWithChild = (Map<String, Object>) info.get("exampleWithChild");
        assertThat(exampleWithChild).containsEntry("key1", "value1");
    }

    private static org.hamcrest.Matcher<String> equalToOk() {
        return org.hamcrest.Matchers.equalTo("ok");
    }
}
