package com.brokerdealer.authservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {
        log.warn("Auth error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("Validation error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenException(ForbiddenException ex) {
        log.warn("Forbidden: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTokenException(InvalidTokenException ex) {
        log.warn("Token error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", List.of());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String code, String message, List<String> details) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", code,
                "message", message,
                "details", details
            ),
            "meta", Map.of(
                "timestamp", Instant.now().toString(),
                "requestId", UUID.randomUUID().toString()
            )
        );
        return ResponseEntity.status(status).body(body);
    }
}
