package com.datagami.edudron.payment.service;

import com.datagami.edudron.common.service.AuditService;
import com.datagami.edudron.payment.repo.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PaymentAuditService extends AuditService {

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public PaymentAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
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
        return "payment";
    }

    @Override
    @Async("eventTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCrud(String operation, String entityType, String entityId,
                        String actorUserId, String actorEmail, Map<String, Object> meta) {
        super.logCrud(operation, entityType, entityId, actorUserId, actorEmail, meta);
    }

    @Async("eventTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCrud(UUID clientId, String operation, String entityType, String entityId,
                        String actorUserId, String actorEmail, Map<String, Object> meta) {
        super.logCrud(clientId, operation, entityType, entityId, actorUserId, actorEmail, meta);
    }
}
