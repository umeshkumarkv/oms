package com.brokerdealer.marketdataservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SymbolNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSymbolNotFoundException(SymbolNotFoundException ex) {
        log.warn("Symbol not found: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ScenarioNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleScenarioNotFoundException(ScenarioNotFoundException ex) {
        log.warn("Scenario not found: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ScenarioAlreadyActiveException.class)
    public ResponseEntity<Map<String, Object>> handleScenarioAlreadyActiveException(ScenarioAlreadyActiveException ex) {
        log.warn("Scenario already active: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(SymbolHaltedException.class)
    public ResponseEntity<Map<String, Object>> handleSymbolHaltedException(SymbolHaltedException ex) {
        log.warn("Symbol halted: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleAccessForbiddenException(AccessForbiddenException ex) {
        log.warn("Access denied: code={} message={}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = "UNAUTHORIZED".equals(ex.getErrorCode()) ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return buildErrorResponse(status, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(SymbolAlreadyInWatchlistException.class)
    public ResponseEntity<Map<String, Object>> handleSymbolAlreadyInWatchlistException(SymbolAlreadyInWatchlistException ex) {
        log.warn("Symbol already in watchlist: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(WatchlistLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleWatchlistLimitExceededException(WatchlistLimitExceededException ex) {
        log.warn("Watchlist limit exceeded: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(SymbolNotInWatchlistException.class)
    public ResponseEntity<Map<String, Object>> handleSymbolNotInWatchlistException(SymbolNotInWatchlistException ex) {
        log.warn("Symbol not in watchlist: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("Validation error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Request validation failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter: {}", ex.getParameterName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid parameter value: param={} value={}", ex.getName(), ex.getValue());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value for parameter '" + ex.getName() + "'", List.of());
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
