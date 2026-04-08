package com.codereboot.gameboot.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> build(HttpStatus status, String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("message", message == null ? "Request failed" : message);
        return ResponseEntity.status(status.value()).body(payload);
    }
}