package com.datagami.edudron.identity.dto;

import java.time.OffsetDateTime;

public record AuthResponse(
    String token,
    String refreshToken,
    String type,
    Long expiresIn,
    UserInfo user,
    Boolean needsTenantSelection,
    java.util.List<TenantInfo> availableTenants
) {
    public record UserInfo(
        String id,
        String email,
        String name,
        String role,
        String tenantId,
        String tenantName,
        String tenantSlug,
        OffsetDateTime createdAt,
        Boolean passwordResetRequired
    ) {}
    
    public record TenantInfo(
        String id,
        String name,
        String slug,
        Boolean isActive
    ) {}
}


