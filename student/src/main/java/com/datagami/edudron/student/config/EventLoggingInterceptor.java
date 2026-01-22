package com.datagami.edudron.student.config;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.service.CommonEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor to automatically log HTTP requests as events.
 */
@Component
public class EventLoggingInterceptor implements HandlerInterceptor {
    
    private static final String START_TIME_ATTRIBUTE = "startTime";
    
    private final CommonEventService eventService;
    
    public EventLoggingInterceptor(CommonEventService eventService) {
        this.eventService = eventService;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;
            
            String method = request.getMethod();
            String path = request.getRequestURI();
            int status = response.getStatus();
            String traceId = getTraceId(request);
            String requestId = request.getHeader("X-Request-Id");
            String userAgent = request.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(request);
            
            // Try to extract user info from request attributes or headers
            String userId = (String) request.getAttribute("userId");
            String userEmail = (String) request.getAttribute("userEmail");
            
            // Additional data
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("queryString", request.getQueryString());
            additionalData.put("contentType", request.getContentType());
            additionalData.put("contentLength", request.getContentLength());
            
            if (ex != null) {
                additionalData.put("exception", ex.getClass().getName());
                additionalData.put("exceptionMessage", ex.getMessage());
            }
            
            // Log the HTTP request event
            eventService.logHttpRequest(
                method, path, status, durationMs,
                traceId, requestId, userAgent, ipAddress,
                userId, userEmail, additionalData
            );
            
            // Also log error if exception occurred
            if (ex != null) {
                String stackTrace = getStackTrace(ex);
                eventService.logError(
                    ex.getClass().getName(),
                    ex.getMessage(),
                    stackTrace,
                    path,
                    userId,
                    traceId
                );
            }
        } catch (Exception e) {
            // Don't let event logging break the request
            // Log to standard logger instead
            org.slf4j.LoggerFactory.getLogger(EventLoggingInterceptor.class)
                .error("Failed to log event in interceptor", e);
        }
    }
    
    private String getTraceId(HttpServletRequest request) {
        // Try multiple sources for trace ID
        String traceId = (String) request.getAttribute("traceId");
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId == null) {
            traceId = request.getHeader("X-Request-Id");
        }
        if (traceId == null) {
            traceId = request.getHeader("X-Trace-Id");
        }
        return traceId;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For can contain multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    private String getStackTrace(Exception ex) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
