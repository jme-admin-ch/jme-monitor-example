package ch.admin.bit.jeap.jme.prometheus;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;

/**
 * Self-contained, in-process test of jme-prometheus-service's own REST endpoints and actuator
 * info contribution. This complements the cross-service scenario in jme-monitor-test, which
 * starts all three services as real processes and also asserts on the (secured) /actuator/prometheus
 * scrape output.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrometheusServiceIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/jme-prometheus-service";
    }

    @Test
    void welcome_returnsActiveProfiles() {
        given().when().get("/")
                .then().statusCode(HttpStatus.OK.value())
                .body(containsString("Welcome JME Prometheus Service"));
    }

    @Test
    void test_returnsRandomValueBetweenZeroAndOne() {
        double result = Double.parseDouble(given().when().get("/test").getBody().asString());
        assertThat(result).isBetween(0.0, 1.0);
    }

    @Test
    void addJob_withValidDescription_isListed() {
        String jobId = UUID.randomUUID().toString();

        given().contentType(ContentType.URLENC)
                .formParam("id", jobId)
                .formParam("priority", "HIGH")
                .formParam("description", "valid-job")
                .when().put("/api/jobs")
                .then().statusCode(HttpStatus.OK.value());

        given().when().get("/api/jobs")
                .then().statusCode(HttpStatus.OK.value())
                .body("id", hasItem(jobId));

        given().when().delete("/api/jobs/{id}", jobId)
                .then().statusCode(HttpStatus.OK.value());
    }

    @Test
    void addJob_withDescriptionContainingInvalid_isRejected() {
        given().contentType(ContentType.URLENC)
                .formParam("id", UUID.randomUUID().toString())
                .formParam("priority", "LOW")
                .formParam("description", "this is invalid")
                .when().put("/api/jobs")
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void addJob_withDescriptionTooLong_isRejected() {
        given().contentType(ContentType.URLENC)
                .formParam("id", UUID.randomUUID().toString())
                .formParam("priority", "LOW")
                .formParam("description", "a".repeat((int) Job.getMaxAllowedJobDescriptionSize() + 1))
                .when().put("/api/jobs")
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void actuatorInfo_containsCustomContributions() {
        Map<String, Object> info = given().when().get("/actuator/info")
                .then().statusCode(HttpStatus.OK.value())
                .extract().jsonPath().getMap("$");

        assertThat(info).containsEntry("staticInfo", "value");
        assertThat(info).containsEntry("exampleWithoutChild", "value");
        @SuppressWarnings("unchecked")
        Map<String, Object> exampleWithChild = (Map<String, Object>) info.get("exampleWithChild");
        assertThat(exampleWithChild).containsEntry("key1", "value1");
    }
}
