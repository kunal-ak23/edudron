package com.datagami.edudron.student.service;

import com.datagami.edudron.common.domain.Event;
import com.datagami.edudron.common.service.EventService;
import com.datagami.edudron.student.repo.CommonEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonEventService extends EventService {
    
    private final CommonEventRepository eventRepository;
    
    @Autowired
    public CommonEventService(CommonEventRepository eventRepository, ObjectMapper objectMapper) {
        super(objectMapper);
        this.eventRepository = eventRepository;
    }
    
    @Override
    @SuppressWarnings("rawtypes")
    protected org.springframework.data.repository.CrudRepository getEventRepository() {
        return eventRepository;
    }
    
    @Override
    protected String getServiceName() {
        return "student";
    }
    
    @Override
    @Async("eventTaskExecutor")
    @Transactional
    public void logHttpRequest(String method, String path, Integer status, Long durationMs,
                              String traceId, String requestId, String userAgent, String ipAddress,
                              String userId, String userEmail, java.util.Map<String, Object> additionalData) {
        super.logHttpRequest(method, path, status, durationMs, traceId, requestId, userAgent, ipAddress, userId, userEmail, additionalData);
    }
    
    @Override
    @Async("eventTaskExecutor")
    @Transactional
    public void logUserAction(String actionType, String userId, String userEmail,
                             String endpoint, java.util.Map<String, Object> actionData) {
        super.logUserAction(actionType, userId, userEmail, endpoint, actionData);
    }
    
    @Override
    @Async("eventTaskExecutor")
    @Transactional
    public void logError(String errorType, String errorMessage, String stackTrace,
                        String endpoint, String userId, String traceId) {
        super.logError(errorType, errorMessage, stackTrace, endpoint, userId, traceId);
    }
    
    @Override
    @Async("eventTaskExecutor")
    @Transactional
    public void logEvent(Event event) {
        super.logEvent(event);
    }
}
