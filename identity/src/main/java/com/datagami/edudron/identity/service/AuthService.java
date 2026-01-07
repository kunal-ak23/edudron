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
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

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
                            user.getCreatedAt()
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
        
        // Find the user with matching password
        User user = null;
        for (User u : users) {
            if (passwordEncoder.matches(request.password(), u.getPassword())) {
                user = u;
                break;
            }
        }
        
        if (user == null) {
            throw new RuntimeException("Invalid credentials");
        }
        
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
                        user.getCreatedAt()
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

        // Generate tokens
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
                        user.getCreatedAt()
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
                        user.getCreatedAt()
                ),
                false,
                List.of()
        );
    }
}

