package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.service.AuditService;
import com.datagami.edudron.identity.repo.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class IdentityAuditService extends AuditService {

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public IdentityAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        super(objectMapper);
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected org.springframework.data.repository.CrudRepository getAuditLogRepository() {
        return auditLogRepository;
    }

    @Override
    protected String getServiceName() {
        return "identity";
    }

    @Override
    @Async("eventTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCrud(String operation, String entityType, String entityId,
                        String actorUserId, String actorEmail, Map<String, Object> meta) {
        super.logCrud(operation, entityType, entityId, actorUserId, actorEmail, meta);
    }
}
