package com.datagami.edudron.common.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit log entity stored in common.audit_logs table.
 * All services use this entity to log CRUD operations for audit (who changed what).
 */
@Entity
@Table(name = "audit_logs", schema = "common", indexes = {
    @Index(name = "idx_audit_logs_client_id", columnList = "clientId"),
    @Index(name = "idx_audit_logs_entity", columnList = "entity"),
    @Index(name = "idx_audit_logs_entity_id", columnList = "entityId"),
    @Index(name = "idx_audit_logs_action", columnList = "action"),
    @Index(name = "idx_audit_logs_created_at", columnList = "createdAt"),
    @Index(name = "idx_audit_logs_actor", columnList = "actor")
})
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(length = 128)
    private String actor;

    @Column(length = 64)
    private String action; // CREATE, UPDATE, DELETE, etc.

    @Column(length = 64)
    private String entity; // User, Course, Enrollment, etc.

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode meta; // JSON: serviceName, oldValue, newValue, etc.

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AuditLog() {
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public JsonNode getMeta() { return meta; }
    public void setMeta(JsonNode meta) { this.meta = meta; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
