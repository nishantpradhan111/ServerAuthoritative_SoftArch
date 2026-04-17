package com.codereboot.gameboot.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return build(HttpStatus.BAD_REQUEST, sanitizeMessage(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validationFailed(MethodArgumentNotValidException exception) {
        String message = "Invalid request payload";
        FieldError firstError = exception.getBindingResult().getFieldError();
        if (firstError != null && firstError.getDefaultMessage() != null) {
            message = firstError.getDefaultMessage();
        }
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden(AccessDeniedException exception) {
        return build(HttpStatus.FORBIDDEN, "Access denied");
    }

    private ResponseEntity<Map<String, String>> build(HttpStatus status, String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("message", message == null ? "Request failed" : message);
        return ResponseEntity.status(status.value()).body(payload);
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed";
        }
        if (message.contains("Username already taken") || message.contains("Email already registered")) {
            return "User credentials conflict with an existing account";
        }
        return message;
    }
}