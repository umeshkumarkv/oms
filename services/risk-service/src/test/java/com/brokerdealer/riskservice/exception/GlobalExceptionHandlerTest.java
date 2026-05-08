package com.brokerdealer.riskservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn400ForValidationException() {
        ValidationException ex = new ValidationException("RULE_DISABLED", "Rule is disabled");

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("RULE_DISABLED");
        assertThat(error.get("message")).isEqualTo("Rule is disabled");
    }

    @Test
    void shouldReturn500ForUnexpectedException() {
        RuntimeException ex = new RuntimeException("unexpected");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void shouldIncludeMetaWithTimestampAndRequestId() {
        ValidationException ex = new ValidationException("CODE", "msg");

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("timestamp")).isNotNull();
        assertThat(meta.get("requestId")).isNotNull();
    }

    @Test
    void validationExceptionShouldReturnErrorCode() {
        ValidationException ex = new ValidationException("MY_CODE", "my message");
        assertThat(ex.getErrorCode()).isEqualTo("MY_CODE");
        assertThat(ex.getMessage()).isEqualTo("my message");
    }
}
