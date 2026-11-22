package com.sakhtyar.shared.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Invalid request.",
                        List.of(),
                        "INVALID_ARGUMENT",
                        Map.of()
                )
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        // Never return arbitrary internal exception messages for server-side failures.
        String message = ex.getStatusCode().is5xxServerError() || ex.getReason() == null
                ? "Request failed."
                : ex.getReason();

        return ResponseEntity.status(ex.getStatusCode()).body(
                new ApiError(
                        Instant.now(),
                        status,
                        message,
                        List.of(),
                        "HTTP_" + status,
                        Map.of()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        List<String> details = new ArrayList<>();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String message = error.getDefaultMessage() == null
                    ? "Invalid value."
                    : error.getDefaultMessage();
            details.add(error.getField() + ": " + message);
            fieldErrors.putIfAbsent(error.getField(), message);
        });

        return ResponseEntity.badRequest().body(
                new ApiError(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed.",
                        details,
                        "VALIDATION_FAILED",
                        fieldErrors
                )
        );
    }

    /** A malformed JSON body is a stable client error, never an internal stack trace. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "Malformed JSON request.", List.of(),
                "MALFORMED_JSON", Map.of()));
    }

    /** Type conversion failures and missing required query parameters are 400. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> invalidParameter(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "Invalid request parameter.", List.of(),
                "INVALID_ARGUMENT", Map.of()));
    }

    /** Method and @Validated parameter constraint failures share the DTO error code. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "Validation failed.", List.of(),
                "VALIDATION_FAILED", Map.of()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handlerMethodValidation(HandlerMethodValidationException ex) {
        int status = ex.getStatusCode().value();
        // A validated RETURN value is a server error, not a 400 from the caller.
        boolean clientInput = ex.getStatusCode().is4xxClientError();
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(
                Instant.now(), status,
                clientInput ? "Validation failed." : "Request failed.", List.of(),
                clientInput ? "VALIDATION_FAILED" : "HTTP_" + status, Map.of()));
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String message,
            List<String> details,
            String code,
            Map<String, String> fieldErrors
    ) {
        // Preserve the existing four-argument Java constructor for downstream callers.
        public ApiError(Instant timestamp, int status, String message, List<String> details) {
            this(timestamp, status, message, details, "HTTP_" + status, Map.of());
        }

        public ApiError {
            details = List.copyOf(details);
            fieldErrors = Map.copyOf(fieldErrors);
        }
    }
}
