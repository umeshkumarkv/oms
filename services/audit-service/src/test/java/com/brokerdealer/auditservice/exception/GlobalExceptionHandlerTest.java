package com.brokerdealer.auditservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn400ForValidationException() {
        ValidationException ex = new ValidationException("INVALID_INPUT", "Field is required");

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INVALID_INPUT");
        assertThat(error.get("message")).isEqualTo("Field is required");
    }

    @Test
    void shouldReturn500ForGenericException() {
        Exception ex = new RuntimeException("Unexpected failure");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void shouldIncludeMetaWithTimestampAndRequestId() {
        ValidationException ex = new ValidationException("CODE", "msg");

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("timestamp")).isNotNull();
        assertThat(meta.get("requestId")).isNotNull();
    }

    @Test
    void shouldReturnErrorDetailsListInResponse() {
        ValidationException ex = new ValidationException("CODE", "msg");

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("details")).isNotNull();
    }
}
