package ch.admin.bit.jeap.jme.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handling so unhandled exceptions are logged inside the request scope.
 * <p>
 * If an exception escapes the controller, it bubbles up the servlet filter chain. The tracing
 * filter closes its observation scope (and clears the MDC) on the way out, so by the time
 * Tomcat's {@code StandardWrapperValve} catches the exception and logs it, the trace id is
 * already gone — the log line ends up untraced. Handling it here means the log statement
 * runs while the observation scope is still open, so the trace id stays in the MDC and
 * can be integrated into log messages.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body("unexpected error");
    }
}
