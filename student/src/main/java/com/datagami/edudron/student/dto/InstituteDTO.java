package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.Institute;
import java.time.OffsetDateTime;
import java.util.UUID;

public class InstituteDTO {
    private String id;
    private UUID clientId;
    private String name;
    private String code;
    private Institute.InstituteType type;
    private String address;
    private Boolean isActive;
    private Long classCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Institute.InstituteType getType() { return type; }
    public void setType(Institute.InstituteType type) { this.type = type; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Long getClassCount() { return classCount; }
    public void setClassCount(Long classCount) { this.classCount = classCount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}


