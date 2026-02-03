package com.datagami.edudron.common.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.domain.AuditLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract Audit Service for logging CRUD operations to common.audit_logs table.
 * Each service provides a concrete implementation with a repository that extends JpaRepository&lt;AuditLog, UUID&gt;.
 * Concrete implementations should add @Async and @Transactional(propagation = REQUIRES_NEW) on logCrud so
 * audit never blocks or rolls back with business logic.
 */
public abstract class AuditService {

    protected static final Logger log = LoggerFactory.getLogger(AuditService.class);
    protected static final int META_JSON_MAX_LENGTH = 32_768; // cap payload size

    protected final ObjectMapper objectMapper;

    public AuditService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("rawtypes")
    protected abstract org.springframework.data.repository.CrudRepository getAuditLogRepository();

    protected abstract String getServiceName();

    /**
     * Log a CRUD operation. Call after successful save/delete. Implementations should be @Async.
     * Failures are logged and never propagated.
     */
    public void logCrud(String operation, String entityType, String entityId,
                        String actorUserId, String actorEmail, Map<String, Object> meta) {
        try {
            AuditLog entry = new AuditLog();
            entry.setId(UUID.randomUUID());
            entry.setClientId(getClientId());
            entry.setAction(truncate(operation, 64));
            entry.setEntity(truncate(entityType, 64));
            entry.setEntityId(truncate(entityId, 64));
            entry.setActor(truncate(actorUserId != null ? actorUserId : actorEmail, 128));
            entry.setCreatedAt(OffsetDateTime.now());

            if (meta != null && !meta.isEmpty()) {
                meta.put("serviceName", getServiceName());
                String json = objectMapper.writeValueAsString(meta);
                if (json.length() > META_JSON_MAX_LENGTH) {
                    json = json.substring(0, META_JSON_MAX_LENGTH) + "\"...[truncated]";
                }
                entry.setMeta(json);
            } else {
                entry.setMeta(objectMapper.writeValueAsString(Map.of("serviceName", getServiceName())));
            }

            @SuppressWarnings("unchecked")
            var repo = (org.springframework.data.repository.CrudRepository<AuditLog, UUID>) getAuditLogRepository();
            repo.save(entry);
        } catch (JsonProcessingException e) {
            log.warn("Audit meta serialization failed for {} {} {}", operation, entityType, entityId, e);
        } catch (Exception e) {
            log.error("Failed to write audit log for {} {} {}", operation, entityType, entityId, e);
        }
    }

    protected UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            try {
                return UUID.fromString(clientIdStr);
            } catch (IllegalArgumentException ignored) {
                // fallback
            }
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    protected static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
