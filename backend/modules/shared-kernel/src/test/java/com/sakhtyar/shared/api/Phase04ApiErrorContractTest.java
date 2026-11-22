package com.sakhtyar.shared.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;

class Phase04ApiErrorContractTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test void stripsUntrustedIllegalArgumentMessage() {
        var result = handler.badRequest(new IllegalArgumentException("internal user secret token=AAA"));
        assertEquals(400, result.getStatusCode().value());
        assertEquals("INVALID_ARGUMENT", result.getBody().code());
        assertEquals("Invalid request.", result.getBody().message());
        assertFalse(result.getBody().message().contains("AAA"));
    }

    @Test void stableValidationCodeAndFieldErrorsPreserveOldDetails() {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", "must not be blank"));
        binding.addError(new FieldError("request", "title", "too short"));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);
        var result = handler.validation(ex);
        assertEquals("VALIDATION_FAILED", result.getBody().code());
        assertEquals(2, result.getBody().details().size());
        assertEquals("must not be blank", result.getBody().fieldErrors().get("title"));
    }

    @Test void keepsNonSensitiveStatusReasonButMasksServerErrors() {
        var notFound = handler.responseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        assertEquals(404, notFound.getStatusCode().value());
        assertEquals("Workspace not found", notFound.getBody().message());
        assertEquals("HTTP_404", notFound.getBody().code());
        var server = handler.responseStatus(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "jdbc:postgresql://secret-host/internal?password=BAD"));
        assertEquals("Request failed.", server.getBody().message());
    }

    @Test void originalFourArgumentApiErrorConstructorAndJsonAreCompatible() throws Exception {
        var error = new ApiExceptionHandler.ApiError(Instant.parse("2026-11-22T01:02:03Z"),
                400, "Invalid request.", List.of());
        assertEquals("HTTP_400", error.code());
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(error));
        assertTrue(json.has("timestamp"));
        assertTrue(json.has("status"));
        assertTrue(json.has("message"));
        assertTrue(json.has("details"));
        assertEquals("HTTP_400", json.get("code").asText());
        assertTrue(json.get("fieldErrors").isObject());
    }
}
