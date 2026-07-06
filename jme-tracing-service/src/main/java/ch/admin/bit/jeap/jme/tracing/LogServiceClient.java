package ch.admin.bit.jeap.jme.tracing;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP interface for the log service. Spring builds the implementation at runtime
 * via {@link org.springframework.web.client.support.RestClientAdapter}, so each method below
 * issues a HTTP request through the underlying {@link org.springframework.web.client.RestClient}.
 */
@HttpExchange
public interface LogServiceClient {

    @GetExchange("/")
    void index();

    @GetExchange("/document/{documentId}")
    void getDocument(@PathVariable String documentId);

    @GetExchange("/document/{documentId}/content/{documentType}")
    void getDocumentContent(@PathVariable String documentId, @PathVariable String documentType);

    @PostExchange("/document/{documentId}/content/{documentType}")
    void postDocumentContent(@PathVariable String documentId, @PathVariable String documentType);

    @GetExchange("/tracing{value}")
    void tracing(@PathVariable int value);
}
