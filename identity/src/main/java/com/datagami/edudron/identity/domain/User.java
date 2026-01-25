package com.datagami.edudron.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "idp")
public class User {
    @Id
    private String id; // ULID string

    @Column
    private UUID clientId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // Encrypted password

    @Column(nullable = false)
    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime lastLoginAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean passwordResetRequired = false;

    public enum Role {
        // System-level roles (highest privilege)
        SYSTEM_ADMIN,        // Full system access, can manage all tenants
        
        // Tenant-level administrative roles
        TENANT_ADMIN,        // Full access to one or more specific tenants (university admin)
        CONTENT_MANAGER,     // Can manage course content within tenant
        
        // Tenant-level operational roles
        INSTRUCTOR,          // View-only access, can view student progress on dashboard
        STUDENT,             // Can enroll in courses and access content
        SUPPORT_STAFF        // Support staff with limited access
    }

    // Constructors
    public User() {}

    public User(String id, UUID clientId, String email, String password, String name, String phone, Role role) {
        this.id = id;
        this.clientId = clientId;
        this.email = email != null ? email.toLowerCase().trim() : null;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.createdAt = OffsetDateTime.now();
        this.active = true;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { 
        this.email = email != null ? email.toLowerCase().trim() : null; 
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getPasswordResetRequired() { return passwordResetRequired; }
    public void setPasswordResetRequired(Boolean passwordResetRequired) { this.passwordResetRequired = passwordResetRequired; }
    
    // Role hierarchy and permission methods
    public boolean isSystemAdmin() {
        return this.role == Role.SYSTEM_ADMIN;
    }
    
    public boolean isTenantAdmin() {
        return this.role == Role.TENANT_ADMIN || this.role == Role.CONTENT_MANAGER;
    }
    
    public boolean isInstructor() {
        return this.role == Role.INSTRUCTOR;
    }
    
    public boolean isStudent() {
        return this.role == Role.STUDENT;
    }
    
    public boolean hasAdminPrivileges() {
        return this.role == Role.SYSTEM_ADMIN || this.role == Role.TENANT_ADMIN;
    }
    
    public boolean canManageContent() {
        return this.role == Role.SYSTEM_ADMIN || this.role == Role.TENANT_ADMIN || 
               this.role == Role.CONTENT_MANAGER;
    }
    
    public boolean canManageUsers() {
        return this.role == Role.SYSTEM_ADMIN || this.role == Role.TENANT_ADMIN;
    }
    
    public boolean canViewAnalytics() {
        return this.role == Role.SYSTEM_ADMIN || this.role == Role.TENANT_ADMIN || 
               this.role == Role.CONTENT_MANAGER || this.role == Role.INSTRUCTOR ||
               this.role == Role.SUPPORT_STAFF;
    }
    
    public boolean canAccessCourses() {
        return this.role == Role.STUDENT || this.role == Role.INSTRUCTOR || 
               this.role == Role.TENANT_ADMIN || this.role == Role.CONTENT_MANAGER ||
               this.role == Role.SYSTEM_ADMIN || this.role == Role.SUPPORT_STAFF;
    }
    
    public boolean canViewOnly() {
        return this.role == Role.INSTRUCTOR || this.role == Role.SUPPORT_STAFF;
    }
    
    public boolean canViewStudentProgress() {
        return this.role == Role.SYSTEM_ADMIN || this.role == Role.TENANT_ADMIN || 
               this.role == Role.INSTRUCTOR;
    }
}

