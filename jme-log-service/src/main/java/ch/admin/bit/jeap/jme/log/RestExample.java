package ch.admin.bit.jeap.jme.log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

@RestController
@Slf4j
public class RestExample {

    private static final String DOCUMENT_ID_KEY = "document-id";

    @GetMapping(path = "/")
    public String index() {
        log.info("Received a rest call");
        return "ok";
    }

    @GetMapping(path = "exception")
    public String error() {
        log.error("Logging an exception", new IllegalStateException("Simulated error"));
        return "ok";
    }

    @GetMapping(path = "throws")
    public String throwing() {
        throw new IllegalStateException("Actual exception");
    }

    @GetMapping(path = "document/{documentId}")
    public String getDocumentById(@PathVariable("documentId") String documentId) {
        log.info("Received a GET call for the document with id {}", keyValue(DOCUMENT_ID_KEY, documentId));
        return "ok";
    }

    @GetMapping(path = "document/{documentId}/content/{documentType}")
    public String getDocumentContentByIdAndType(@PathVariable("documentId") String documentId, @PathVariable("documentType") String documentType) {
        log.info("Received a GET-Call for the document with id '{}' and type '{}'",
                keyValue(DOCUMENT_ID_KEY, documentId),
                keyValue("document-type", documentType));
        return "ok";
    }

    @PostMapping(path = "document/{documentId}/content/{documentType}")
    public String saveNewDocumentWithIdAndType(@PathVariable("documentId") String documentId, @PathVariable("documentType") String documentType) {
        log.info("Received a POST-Call for the document with id '{}' and type '{}'",
                keyValue(DOCUMENT_ID_KEY, documentId),
                keyValue("document-type", documentType));
        return "ok";
    }

    @GetMapping(path = {"tracing1", "tracing2", "tracing3", "tracing4"})
    public String tracing(HttpServletRequest request) {
        log.info("Received a GET call to {}", request.getRequestURI());
        return "ok";
    }

}
