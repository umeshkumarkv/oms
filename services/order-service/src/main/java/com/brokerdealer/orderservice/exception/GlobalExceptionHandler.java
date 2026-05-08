package com.brokerdealer.orderservice.exception;

import com.brokerdealer.orderservice.dto.response.FieldErrorResponse;
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
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("L1 validation error: code={} field={} message={}", ex.getErrorCode(), ex.getFieldName(), ex.getMessage());
        List<FieldErrorResponse> details = ex.getFieldName() != null
                ? List.of(FieldErrorResponse.builder().field(ex.getFieldName()).code(ex.getErrorCode()).message(ex.getMessage()).build())
                : List.of();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), details);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldErrorResponse> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldErrorResponse.builder()
                        .field(fe.getField())
                        .code("VALIDATION_ERROR")
                        .message(fe.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());
        String message = details.isEmpty() ? "Validation failed" : details.get(0).getMessage();
        log.warn("L1 bean validation error: fields={}", details.stream().map(FieldErrorResponse::getField).toList());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details);
    }

    @ExceptionHandler(SemanticValidationException.class)
    public ResponseEntity<Map<String, Object>> handleSemanticValidation(SemanticValidationException ex) {
        log.warn("L2 semantic validation error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        log.warn("Order not found: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.warn("Downstream service unavailable: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", List.of());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String code, String message, List<?> details) {
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
