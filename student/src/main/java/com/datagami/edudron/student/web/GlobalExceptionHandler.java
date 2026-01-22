package com.datagami.edudron.student.web;

import com.datagami.edudron.student.service.CommonEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @Autowired
    private CommonEventService eventService;

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

