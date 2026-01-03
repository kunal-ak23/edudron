package com.datagami.edudron.identity.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        // Check if it's an authentication-related error
        if (ex.getMessage() != null && ex.getMessage().contains("Invalid credentials")) {
            logger.warn("Invalid credentials attempt: {}", ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid email or password");
            errorResponse.put("code", "INVALID_CREDENTIALS");
            errorResponse.put("status", "error");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        
        // Check if it's a login/registration failure
        if (ex.getMessage() != null && (ex.getMessage().contains("Login failed") || ex.getMessage().contains("Registration failed"))) {
            String errorMsg = ex.getMessage();
            // Extract the actual error message
            if (errorMsg.contains("Invalid credentials")) {
                logger.warn("Authentication error: {}", errorMsg);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid email or password");
                errorResponse.put("code", "INVALID_CREDENTIALS");
                errorResponse.put("status", "error");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            logger.warn("Authentication error: {}", errorMsg);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", errorMsg);
            errorResponse.put("code", "AUTH_ERROR");
            errorResponse.put("status", "error");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        
        // Check if it's a token refresh error
        if (ex.getMessage() != null && (ex.getMessage().contains("Token refresh") || ex.getMessage().contains("refresh token"))) {
            logger.warn("Token refresh error: {}", ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid or expired refresh token");
            errorResponse.put("code", "INVALID_REFRESH_TOKEN");
            errorResponse.put("status", "error");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        
        logger.error("Runtime exception: ", ex);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage() != null ? ex.getMessage() : "An error occurred");
        errorResponse.put("code", "INTERNAL_ERROR");
        errorResponse.put("status", "error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Validation failed");
        errorResponse.put("code", "VALIDATION_ERROR");
        errorResponse.put("status", "error");
        errorResponse.put("errors", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: ", ex);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "An unexpected error occurred");
        errorResponse.put("code", "INTERNAL_ERROR");
        errorResponse.put("status", "error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

