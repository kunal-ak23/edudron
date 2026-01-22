package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.domain.Event;
import com.datagami.edudron.common.service.EventService;
import com.datagami.edudron.identity.repo.CommonEventRepository;
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
        return "identity";
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
    public void logLogin(String userId, String userEmail, String ipAddress, String userAgent,
                        String sessionId, java.util.Map<String, Object> loginData) {
        super.logLogin(userId, userEmail, ipAddress, userAgent, sessionId, loginData);
    }
    
    @Override
    @Async("eventTaskExecutor")
    @Transactional
    public void logLogout(String userId, String userEmail, String sessionId, java.util.Map<String, Object> logoutData) {
        super.logLogout(userId, userEmail, sessionId, logoutData);
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
