package com.datagami.edudron.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_webhooks", schema = "payment")
public class PaymentWebhook {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 100)
    private String eventType;

    private String providerEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payloadJson;

    @Column(nullable = false)
    private Boolean processed = false;

    private OffsetDateTime processedAt;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public PaymentWebhook() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }

    public JsonNode getPayloadJson() { return payloadJson; }
    public void setPayloadJson(JsonNode payloadJson) { this.payloadJson = payloadJson; }

    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean processed) { this.processed = processed; }

    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

