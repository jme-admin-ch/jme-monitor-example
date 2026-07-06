package ch.admin.bit.jeap.jme.log;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Self-contained, in-process test of jme-log-service's own REST endpoints. This complements the
 * cross-service scenario in jme-monitor-test, which starts all three services as real processes
 * and exercises them together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LogServiceIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/jme-log-service";
    }

    @Test
    void index_returnsOk() {
        given().when().get("/")
                .then().statusCode(HttpStatus.OK.value()).body(equalTo("ok"));
    }

    @Test
    void loggingAnException_stillReturnsOk() {
        given().when().get("/exception")
                .then().statusCode(HttpStatus.OK.value()).body(equalTo("ok"));
    }

    @Test
    void unhandledException_isHandledByGlobalExceptionHandler() {
        given().when().get("/throws")
                .then().statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .body(equalTo("unexpected error"));
    }

    @Test
    void getDocumentById_returnsOk() {
        given().when().get("/document/1234")
                .then().statusCode(HttpStatus.OK.value()).body(equalTo("ok"));
    }

    @Test
    void getDocumentContentByIdAndType_returnsOk() {
        given().when().get("/document/1234/content/pdf")
                .then().statusCode(HttpStatus.OK.value()).body(equalTo("ok"));
    }

    @Test
    void postDocumentContentByIdAndType_returnsOk() {
        given().when().post("/document/1234/content/pdf")
                .then().statusCode(HttpStatus.OK.value()).body(equalTo("ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tracing1", "tracing2", "tracing3", "tracing4"})
    void tracingEndpoints_returnOk(String path) {
        given().when().get("/" + path)
                .then().statusCode(HttpStatus.OK.value()).body(equalTo("ok"));
    }
}
