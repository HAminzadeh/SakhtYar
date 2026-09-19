package com.sakhtyar.shared.api;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(
                new ApiError(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed.",
                        details
                )
        );
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String message,
            List<String> details
    ) {
    }
}
