package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.BatchOperationError;
import com.datagami.edudron.student.dto.BatchOperationErrorResponse;
import com.datagami.edudron.student.service.CommonEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @Autowired
    private CommonEventService eventService;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        String message = ex.getMessage();
        
        // Determine appropriate status code based on error message
        HttpStatus status = HttpStatus.BAD_REQUEST;
        
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
            
            // Check if this is a batch operation error (contains "at index")
            if (lowerMessage.contains("at index")) {
                return handleBatchOperationError(message, status);
            }
        }
        
        // Regular error response
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return ResponseEntity.status(status).body(error);
    }
    
    private ResponseEntity<BatchOperationErrorResponse> handleBatchOperationError(String message, HttpStatus status) {
        List<BatchOperationError> errors = new ArrayList<>();
        
        // Parse error message to extract index and details
        // Pattern: "... at index N: 'value'"
        Pattern pattern = Pattern.compile("at index (\\d+)");
        Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            
            // Determine field based on error message
            String field = "unknown";
            if (message.toLowerCase().contains("code")) {
                field = "code";
            } else if (message.toLowerCase().contains("name")) {
                field = "name";
            }
            
            errors.add(new BatchOperationError(index, field, message));
        } else {
            // If pattern doesn't match, create a general error
            errors.add(new BatchOperationError(0, "general", message));
        }
        
        BatchOperationErrorResponse response = new BatchOperationErrorResponse(
            "Batch operation failed",
            errors
        );
        
        return ResponseEntity.status(status).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BatchOperationErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<BatchOperationError> errors = new ArrayList<>();
        
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            
            // Try to extract index from field name (e.g., "sections[0].name")
            Pattern pattern = Pattern.compile("\\[(\\d+)\\]");
            Matcher matcher = pattern.matcher(fieldName);
            
            int index = -1;
            if (matcher.find()) {
                index = Integer.parseInt(matcher.group(1));
            }
            
            errors.add(new BatchOperationError(index, fieldName, errorMessage));
        });
        
        BatchOperationErrorResponse response = new BatchOperationErrorResponse(
            "Validation failed",
            errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
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
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "An unexpected error occurred: " + ex.getMessage());
        
        // Log error to event service
        logErrorToEventService(ex, request);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    private void logErrorToEventService(Exception ex, HttpServletRequest request) {
        try {
            String traceId = getTraceId(request);
            String userId = (String) request.getAttribute("userId");
            String endpoint = request.getRequestURI();
            String stackTrace = getStackTrace(ex);
            
            eventService.logError(
                ex.getClass().getName(),
                ex.getMessage(),
                stackTrace,
                endpoint,
                userId,
                traceId
            );
        } catch (Exception e) {
            // Don't let event logging break error handling
            // Error is already logged by the interceptor
        }
    }
    
    private String getTraceId(HttpServletRequest request) {
        String traceId = (String) request.getAttribute("traceId");
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId == null) {
            traceId = request.getHeader("X-Request-Id");
        }
        return traceId;
    }
    
    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}

