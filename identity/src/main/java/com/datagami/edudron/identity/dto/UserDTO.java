package com.datagami.edudron.identity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserDTO {
    private String id;
    private UUID clientId;
    private String email;
    private String name;
    private String phone;
    private String role;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastLoginAt;

    // Constructors
    public UserDTO() {}

    public UserDTO(String id, UUID clientId, String email, String name, String phone, 
                   String role, Boolean active, OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
        this.id = id;
        this.clientId = clientId;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}

