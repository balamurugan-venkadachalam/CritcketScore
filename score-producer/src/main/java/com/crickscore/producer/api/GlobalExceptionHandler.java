package com.crickscore.producer.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised exception-to-HTTP-response mapping for all producer API
 * endpoints.
 *
 * <h3>Response format</h3>
 * All error responses use {@link ProblemDetail} (RFC 9457 / RFC 7807) so the
 * shape is consistent and machine-readable:
 * 
 * <pre>
 * {
 *   "type":     "https://crickscore.io/errors/validation",
 *   "title":    "Validation Failed",
 *   "status":   400,
 *   "detail":   "Request body validation failed with 2 error(s)",
 *   "instance": "/api/v1/scores",
 *   "errors":   [ { "field": "matchId", "message": "must not be blank" } ]
 * }
 * </pre>
 *
 * <h3>Handled cases</h3>
 * <ul>
 * <li>{@code 400 Bad Request} — Bean Validation failures on the request
 * body.</li>
 * <li>{@code 503 Service Unavailable} — Kafka serialisation or immediate send
 * failure (retries are handled asynchronously; this covers synchronous
 * path).</li>
 * <li>{@code 500 Internal Server Error} — Unexpected exceptions.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_TYPE_URI = "https://crickscore.io/errors/";

    // ── 400: Bean Validation ──────────────────────────────────────────────────

    /**
     * Handles {@link MethodArgumentNotValidException} thrown when a request
     * body fails {@code @Valid} Bean Validation.
     *
     * <p>
     * Collects all field error messages and includes them as a structured
     * {@code errors} extension property in the {@link ProblemDetail}.
     *
     * @param ex the validation exception populated by Spring MVC.
     * @return 400 response with a structured list of field errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> {
                    Map<String, String> err = new LinkedHashMap<>();
                    err.put("field", fe.getField());
                    err.put("message", fe.getDefaultMessage());
                    if (fe.getRejectedValue() != null) {
                        err.put("rejectedValue", String.valueOf(fe.getRejectedValue()));
                    }
                    return err;
                })
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(BASE_TYPE_URI + "validation"));
        problem.setTitle("Validation Failed");
        problem.setDetail("Request body validation failed with %d error(s)".formatted(fieldErrors.size()));
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now().toString());

        log.warn("Validation failed: {} field error(s) — {}",
                fieldErrors.size(),
                fieldErrors.stream()
                        .map(e -> e.get("field") + ": " + e.get("message"))
                        .toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // ── 503: Kafka send failure ───────────────────────────────────────────────

    /**
     * Handles {@link KafkaSendException} thrown when the Kafka producer
     * encounters a synchronous failure (e.g. serialisation error, immediate
     * broker rejection before async callback).
     *
     * <p>
     * Returns 503 so upstream load balancers can retry on a different
     * instance. Note: most broker failures are handled asynchronously by
     * {@link com.crickscore.producer.kafka.ProducerCallbackHandler} and do
     * NOT reach this handler.
     *
     * @param ex the Kafka send exception.
     * @return 503 response.
     */
    @ExceptionHandler(KafkaSendException.class)
    public ResponseEntity<ProblemDetail> handleKafkaSendException(KafkaSendException ex) {
        log.error("Kafka send failed: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create(BASE_TYPE_URI + "kafka-send-failure"));
        problem.setTitle("Score Event Publish Failed");
        problem.setDetail("Failed to publish score event to Kafka — please retry");
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    // ── 500: Catch-all ────────────────────────────────────────────────────────

    /**
     * Catch-all handler for any unexpected {@link Exception}.
     *
     * <p>
     * Logs the full stack trace at ERROR level and returns a generic 500
     * response without leaking internal implementation details.
     *
     * @param ex the unexpected exception.
     * @return 500 response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception in producer API: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(BASE_TYPE_URI + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred — please contact support");
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // ── Marker exception ──────────────────────────────────────────────────────

    /**
     * Marker exception for synchronous Kafka send failures.
     *
     * <p>
     * Thrown by the controller layer when a synchronous/immediate error
     * occurs prior to the async callback path.
     */
    public static class KafkaSendException extends RuntimeException {
        public KafkaSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
