package com.datagami.edudron.student.web;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        
        // Determine appropriate status code based on error message
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = ex.getMessage();
        
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("already enrolled") || 
                lowerMessage.contains("already exists") ||
                lowerMessage.contains("duplicate")) {
                status = HttpStatus.CONFLICT; // 409 Conflict
            } else if (lowerMessage.contains("not found")) {
                status = HttpStatus.NOT_FOUND; // 404 Not Found
            } else if (lowerMessage.contains("not active") || 
                       lowerMessage.contains("inactive")) {
                status = HttpStatus.BAD_REQUEST; // 400 Bad Request
            } else if (lowerMessage.contains("full") || 
                       lowerMessage.contains("capacity")) {
                status = HttpStatus.BAD_REQUEST; // 400 Bad Request
            }
        }
        
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        
        // IllegalStateException often indicates server configuration issues
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getMessage();
        
        if (message != null && message.toLowerCase().contains("tenant context")) {
            status = HttpStatus.BAD_REQUEST; // 400 Bad Request for missing tenant context
        }
        
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleIncorrectResultSizeDataAccessException(IncorrectResultSizeDataAccessException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "An unexpected error occurred: " + ex.getMessage());
        // Log the full exception for debugging
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "An unexpected error occurred: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

