package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.dto.AuthRequest;
import com.datagami.edudron.identity.dto.AuthResponse;
import com.datagami.edudron.identity.dto.RegisterRequest;
import com.datagami.edudron.identity.repo.UserRepository;
import com.datagami.edudron.identity.repo.ClientRepository;
import com.datagami.edudron.identity.security.JwtUtil;
import com.datagami.edudron.identity.service.CommonEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private CommonEventService eventService;

    public AuthResponse login(AuthRequest request) {
        // First try to find SYSTEM_ADMIN user (no tenant context required)
        if (userRepository.existsByEmailAndRole(request.email(), User.Role.SYSTEM_ADMIN)) {
            User user = userRepository.findByEmailAndRoleAndActiveTrue(request.email(), User.Role.SYSTEM_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));

            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new RuntimeException("Invalid credentials");
            }

            // Update last login
            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);

            // Get all available tenants for SYSTEM_ADMIN
            List<AuthResponse.TenantInfo> tenants = clientRepository.findAll().stream()
                    .map(client -> new AuthResponse.TenantInfo(
                            client.getId().toString(),
                            client.getName(),
                            client.getSlug(),
                            client.getIsActive()
                    ))
                    .toList();

            // If no tenants available, don't require tenant selection
            boolean needsTenantSelection = !tenants.isEmpty();

            // Generate tokens
            String tenantId = needsTenantSelection ? "PENDING_TENANT_SELECTION" : "SYSTEM";
            
            // Log login event (after variables are defined)
            String sessionId = java.util.UUID.randomUUID().toString();
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();
            Map<String, Object> loginData = Map.of(
                "role", user.getRole().name(),
                "tenantId", tenantId,
                "needsTenantSelection", needsTenantSelection
            );
            eventService.logLogin(user.getId(), user.getEmail(), ipAddress, userAgent, sessionId, loginData);
            String token = jwtUtil.generateToken(user.getEmail(), tenantId, user.getRole().name());
            String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), tenantId, user.getRole().name());

            return new AuthResponse(
                    token,
                    refreshToken,
                    "Bearer",
                    86400L, // 24 hours
                    new AuthResponse.UserInfo(
                            user.getId(),
                            user.getEmail(),
                            user.getName(),
                            user.getRole().name(),
                            tenantId,
                            "System",
                            "system",
                            user.getCreatedAt(),
                            user.getPasswordResetRequired() != null ? user.getPasswordResetRequired() : false
                    ),
                    needsTenantSelection,
                    tenants
            );
        }

        // Regular tenant-scoped user authentication
        List<User> users = userRepository.findByEmailAndActiveTrue(request.email());
        if (users.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // Find all users with matching password (same email can exist across tenants)
        List<User> matchingUsers = new ArrayList<>();
        for (User u : users) {
            if (passwordEncoder.matches(request.password(), u.getPassword())) {
                matchingUsers.add(u);
            }
        }

        if (matchingUsers.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }

        // If the same credentials match multiple tenants, require tenant selection
        if (matchingUsers.size() > 1) {
            log.info("Multi-tenant login detected: email={}, matches={}", request.email(), matchingUsers.size());
            // If roles differ across matched accounts, fail safely (ambiguous permissions)
            String role = matchingUsers.get(0).getRole().name();
            for (User u : matchingUsers) {
                if (u.getRole() == null || !role.equals(u.getRole().name())) {
                    throw new RuntimeException("Multiple accounts found with different roles. Please contact support.");
                }
            }

            Set<UUID> clientIds = new HashSet<>();
            for (User u : matchingUsers) {
                if (u.getClientId() != null) {
                    clientIds.add(u.getClientId());
                }
            }
            log.info("Multi-tenant login candidate clientIds: email={}, clientIds={}", request.email(), clientIds);

            List<AuthResponse.TenantInfo> tenants = clientIds.stream()
                    .map(clientId -> clientRepository.findById(clientId).orElse(null))
                    .filter(client -> client != null)
                    .map(client -> new AuthResponse.TenantInfo(
                            client.getId().toString(),
                            client.getName(),
                            client.getSlug(),
                            client.getIsActive()
                    ))
                    .toList();
            log.info("Multi-tenant login available tenants: email={}, tenantsCount={}", request.email(), tenants.size());

            // Issue placeholder-tenant tokens; services will rely on X-Client-Id after selection
            String tenantId = "PENDING_TENANT_SELECTION";
            User representativeUser = matchingUsers.get(0);
            String token = jwtUtil.generateToken(representativeUser.getEmail(), tenantId, role);
            String refreshToken = jwtUtil.generateRefreshToken(representativeUser.getEmail(), tenantId, role);

            return new AuthResponse(
                    token,
                    refreshToken,
                    "Bearer",
                    86400L, // 24 hours
                    new AuthResponse.UserInfo(
                            representativeUser.getId(),
                            representativeUser.getEmail(),
                            representativeUser.getName(),
                            role,
                            tenantId,
                            "Select Tenant",
                            "select-tenant",
                            representativeUser.getCreatedAt(),
                            null
                    ),
                    true,
                    tenants
            );
        }

        // Single-tenant match: proceed as before
        User user = matchingUsers.get(0);
        
        // Set the tenant context based on the user's tenant
        if (user.getClientId() != null) {
            TenantContext.setClientId(user.getClientId().toString());
        } else {
            throw new RuntimeException("User is not associated with any tenant");
        }

        // Update last login
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        String tenantId = user.getClientId() != null ? user.getClientId().toString() : "UNKNOWN";
        
        // Fetch client information for tenant details
        String tenantName = "Unknown Tenant";
        String tenantSlug = "unknown-tenant";
        if (user.getClientId() != null) {
            try {
                var client = clientRepository.findById(user.getClientId());
                if (client.isPresent()) {
                    tenantName = client.get().getName();
                    tenantSlug = client.get().getSlug();
                }
            } catch (Exception e) {
                // Log warning but continue
            }
        }

        // Update last login
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        
        // Log login event
        String sessionId = java.util.UUID.randomUUID().toString();
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        Map<String, Object> loginData = Map.of(
            "role", user.getRole().name(),
            "tenantId", tenantId,
            "tenantName", tenantName
        );
        eventService.logLogin(user.getId(), user.getEmail(), ipAddress, userAgent, sessionId, loginData);
        
        // Generate tokens
        String token = jwtUtil.generateToken(user.getEmail(), tenantId, user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), tenantId, user.getRole().name());

        return new AuthResponse(
                token,
                refreshToken,
                "Bearer",
                86400L, // 24 hours
                new AuthResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getRole().name(),
                        tenantId,
                        tenantName,
                        tenantSlug,
                        user.getCreatedAt(),
                        user.getPasswordResetRequired() != null ? user.getPasswordResetRequired() : false
                ),
                false,
                List.of()
        );
    }

    public AuthResponse register(RegisterRequest request) {
        User.Role role = User.Role.valueOf(request.role().toUpperCase());
        
        // SECURITY: Prevent SYSTEM_ADMIN registration through public API
        if (role == User.Role.SYSTEM_ADMIN) {
            throw new RuntimeException("SYSTEM_ADMIN users cannot be created through this endpoint. Use the manual script instead.");
        }
        
        // Regular tenant-scoped registration only
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null || clientIdStr.isBlank()) {
            throw new RuntimeException("Tenant context required for user registration");
        }
        
        UUID clientId = UUID.fromString(clientIdStr);
        String tenantId = clientId.toString();
        
        if (userRepository.existsByEmailAndClientId(request.email(), clientId)) {
            throw new RuntimeException("User already exists with this email in this tenant");
        }

        User user = null;
        int maxRetries = 3;
        int attempts = 0;
        
        while (user == null && attempts < maxRetries) {
            try {
                String userId = UlidGenerator.nextUlid();
                user = new User(
                        userId,
                        clientId,
                        request.email(),
                        passwordEncoder.encode(request.password()),
                        request.name(),
                        request.phone(),
                        role
                );
                
                user = userRepository.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Handle duplicate key violation (ID collision)
                attempts++;
                if (attempts >= maxRetries) {
                    // Check if user was actually created (by email)
                    var existingUser = userRepository.findByEmailAndClientId(request.email(), clientId);
                    if (existingUser.isPresent()) {
                        user = existingUser.get();
                        break; // User already exists, use it
                    }
                    throw new RuntimeException("Failed to create user after multiple attempts. Please try again.", e);
                }
                // Retry with a new ULID
                user = null;
            }
        }
        
        if (user == null) {
            throw new RuntimeException("Failed to create user. Please try again.");
        }
        
        // Generate tokens (tenantId already defined above)
        String token = jwtUtil.generateToken(user.getEmail(), tenantId, user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), tenantId, user.getRole().name());

        // Fetch client information
        String tenantName = "Unknown Tenant";
        String tenantSlug = "unknown-tenant";
        try {
            var client = clientRepository.findById(clientId);
            if (client.isPresent()) {
                tenantName = client.get().getName();
                tenantSlug = client.get().getSlug();
            }
        } catch (Exception e) {
            // Log warning but continue
        }
        
        // Log registration event (after variables are defined)
        Map<String, Object> registrationData = Map.of(
            "role", user.getRole().name(),
            "tenantId", tenantId,
            "tenantName", tenantName
        );
        eventService.logUserAction("USER_REGISTERED", user.getId(), user.getEmail(), "/auth/register", registrationData);

        return new AuthResponse(
                token,
                refreshToken,
                "Bearer",
                86400L,
                new AuthResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getRole().name(),
                        tenantId,
                        tenantName,
                        tenantSlug,
                        user.getCreatedAt(),
                        user.getPasswordResetRequired() != null ? user.getPasswordResetRequired() : false
                ),
                false,
                List.of()
        );
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        Claims claims = jwtUtil.extractClaimsIgnoringExpiration(refreshToken);
        String email = claims.getSubject();
        String tenantId = claims.get("tenant", String.class);
        String role = claims.get("role", String.class);

        // Generate new tokens
        String newToken = jwtUtil.generateToken(email, tenantId, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(email, tenantId, role);

        // Fetch user info
        User user = userRepository.findByEmailAndActiveTrue(email).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found"));

        String tenantName = "Unknown Tenant";
        String tenantSlug = "unknown-tenant";
        if (user.getClientId() != null) {
            try {
                var client = clientRepository.findById(user.getClientId());
                if (client.isPresent()) {
                    tenantName = client.get().getName();
                    tenantSlug = client.get().getSlug();
                }
            } catch (Exception e) {
                // Log warning but continue
            }
        }

        return new AuthResponse(
                newToken,
                newRefreshToken,
                "Bearer",
                86400L,
                new AuthResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getRole().name(),
                        tenantId,
                        tenantName,
                        tenantSlug,
                        user.getCreatedAt(),
                        user.getPasswordResetRequired() != null ? user.getPasswordResetRequired() : false
                ),
                false,
                List.of(                )
        );
    }
    
    /**
     * Get client IP address from request.
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("Could not get client IP address: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get user agent from request.
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get user agent: {}", e.getMessage());
        }
        return null;
    }
}

