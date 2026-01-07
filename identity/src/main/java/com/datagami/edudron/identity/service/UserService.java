package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.dto.CreateUserRequest;
import com.datagami.edudron.identity.dto.UserDTO;
import com.datagami.edudron.identity.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        String clientIdStr = TenantContext.getClientId();
        
        // SYSTEM_ADMIN can see all users
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            log.info("Fetching all users (SYSTEM_ADMIN)");
            return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        }
        
        // Tenant-scoped users see only their tenant's users
        UUID clientId = UUID.fromString(clientIdStr);
        log.info("Fetching users for tenant: {}", clientId);
        return userRepository.findByClientId(clientId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public UserDTO getUserById(String id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        
        // Check tenant access
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            UUID clientId = UUID.fromString(clientIdStr);
            if (user.getClientId() != null && !user.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("User not found: " + id);
            }
        }
        
        return toDTO(user);
    }
    
    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        String clientIdStr = TenantContext.getClientId();
        User.Role role = User.Role.valueOf(request.getRole().toUpperCase());
        
        // SECURITY: Prevent SYSTEM_ADMIN creation through API
        if (role == User.Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("SYSTEM_ADMIN users cannot be created through this endpoint. Use the manual script instead.");
        }
        
        UUID clientId = null;
        if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            clientId = UUID.fromString(clientIdStr);
        } else {
            throw new IllegalArgumentException("Tenant context required for user creation");
        }
        
        // Check if user already exists
        if (userRepository.existsByEmailAndClientId(request.getEmail(), clientId)) {
            throw new IllegalArgumentException("User already exists with this email in this tenant");
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
                    request.getEmail(),
                    passwordEncoder.encode(request.getPassword()),
                    request.getName(),
                    request.getPhone(),
                    role
                );
                user.setActive(request.getActive() != null ? request.getActive() : true);
                user.setCreatedAt(OffsetDateTime.now());
                
                user = userRepository.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Handle duplicate key violation (ID collision)
                attempts++;
                if (attempts >= maxRetries) {
                    // Check if user was actually created (by email)
                    var existingUser = userRepository.findByEmailAndClientId(request.getEmail(), clientId);
                    if (existingUser.isPresent()) {
                        user = existingUser.get();
                        break; // User already exists, use it
                    }
                    throw new IllegalArgumentException("Failed to create user after multiple attempts. Please try again.", e);
                }
                // Retry with a new ULID
                user = null;
            }
        }
        
        if (user == null) {
            throw new IllegalArgumentException("Failed to create user. Please try again.");
        }
        return toDTO(user);
    }
    
    private UserDTO toDTO(User user) {
        return new UserDTO(
            user.getId(),
            user.getClientId(),
            user.getEmail(),
            user.getName(),
            user.getPhone(),
            user.getRole().name(),
            user.getActive(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}

