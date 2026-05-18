package com.brokerdealer.riskservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("Validation error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(RuleNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRuleNotFoundException(RuleNotFoundException ex) {
        log.warn("Rule not found: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(PendingChangeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePendingChangeNotFoundException(PendingChangeNotFoundException ex) {
        log.warn("Pending change not found: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(SelfApprovalNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleSelfApprovalNotAllowedException(SelfApprovalNotAllowedException ex) {
        log.warn("Self-approval attempt: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ChangeAlreadyReviewedException.class)
    public ResponseEntity<Map<String, Object>> handleChangeAlreadyReviewedException(ChangeAlreadyReviewedException ex) {
        log.warn("Change already reviewed: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), List.of());
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
