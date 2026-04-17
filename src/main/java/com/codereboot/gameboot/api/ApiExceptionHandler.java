package com.codereboot.gameboot.api;

import com.codereboot.gameboot.security.RequestIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, sanitizeMessage(exception.getMessage()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validationFailed(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = "Invalid request payload";
        FieldError firstError = exception.getBindingResult().getFieldError();
        if (firstError != null && firstError.getDefaultMessage() != null) {
            message = firstError.getDefaultMessage();
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    private ResponseEntity<Map<String, String>> build(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("message", message == null ? "Request failed" : message);
        String requestId = RequestIdResolver.resolve(request);
        if (requestId != null) {
            payload.put("requestId", requestId);
        }
        return ResponseEntity.status(status).body(payload);
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