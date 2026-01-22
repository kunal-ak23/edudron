package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import com.datagami.edudron.identity.domain.User;
import com.datagami.edudron.identity.domain.UserInstitute;
import com.datagami.edudron.identity.dto.CreateUserRequest;
import com.datagami.edudron.identity.dto.UpdateUserRequest;
import com.datagami.edudron.identity.dto.UserDTO;
import com.datagami.edudron.identity.repo.UserInstituteRepository;
import com.datagami.edudron.identity.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private UserInstituteRepository userInstituteRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
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
    
    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByRole(String roleStr) {
        String clientIdStr = TenantContext.getClientId();
        
        User.Role role;
        try {
            role = User.Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + roleStr);
        }
        
        // SYSTEM_ADMIN can see all users
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            log.info("Fetching all users with role {} (SYSTEM_ADMIN)", role);
            return userRepository.findAll().stream()
                .filter(user -> user.getRole() == role)
                .map(this::toDTO)
                .collect(Collectors.toList());
        }
        
        // Tenant-scoped users see only their tenant's users
        UUID clientId = UUID.fromString(clientIdStr);
        log.info("Fetching users with role {} for tenant: {}", role, clientId);
        return userRepository.findByClientIdAndRole(clientId, role).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Page<UserDTO> getUsersByRolePaginated(String roleStr, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        
        User.Role role;
        try {
            role = User.Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + roleStr);
        }
        
        Page<User> userPage;
        
        // SYSTEM_ADMIN can see all users
        if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            log.info("Fetching paginated users with role {} (SYSTEM_ADMIN), page: {}, size: {}", 
                role, pageable.getPageNumber(), pageable.getPageSize());
            userPage = userRepository.findByRole(role, pageable);
        } else {
            // Tenant-scoped users see only their tenant's users
            UUID clientId = UUID.fromString(clientIdStr);
            log.info("Fetching paginated users with role {} for tenant: {}, page: {}, size: {}", 
                role, clientId, pageable.getPageNumber(), pageable.getPageSize());
            userPage = userRepository.findByClientIdAndRole(clientId, role, pageable);
        }
        
        return userPage.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public long countUsersByRole(String roleStr, boolean activeOnly) {
        String clientIdStr = TenantContext.getClientId();

        User.Role role;
        try {
            role = User.Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + roleStr);
        }

        boolean isSystemContext = (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr));
        if (isSystemContext) {
            return activeOnly
                ? userRepository.countByRoleAndActiveTrue(role)
                : userRepository.countByRole(role);
        }

        UUID clientId = UUID.fromString(clientIdStr);
        return activeOnly
            ? userRepository.countByClientIdAndRoleAndActiveTrue(clientId, role)
            : userRepository.countByClientIdAndRole(clientId, role);
    }
    
    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        String clientIdStr = TenantContext.getClientId();
        User.Role role = User.Role.valueOf(request.getRole().toUpperCase());
        
        // Get current user to check permissions
        User currentUser = getCurrentUser();
        boolean isCurrentUserSystemAdmin = currentUser != null && currentUser.getRole() == User.Role.SYSTEM_ADMIN;
        
        // SECURITY: Only SYSTEM_ADMIN can create SYSTEM_ADMIN users
        if (role == User.Role.SYSTEM_ADMIN) {
            if (!isCurrentUserSystemAdmin) {
                throw new IllegalArgumentException("SYSTEM_ADMIN users can only be created by existing SYSTEM_ADMIN users.");
            }
            // SYSTEM_ADMIN users have no tenant and no institutes
            return createSystemAdminUser(request);
        }
        
        // For non-SYSTEM_ADMIN users, validate tenant context and institutes
        UUID clientId = null;
        if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            clientId = UUID.fromString(clientIdStr);
        } else {
            throw new IllegalArgumentException("Tenant context required for user creation");
        }
        
        // Validate that instituteIds are provided for non-SYSTEM_ADMIN users
        if (request.getInstituteIds() == null || request.getInstituteIds().isEmpty()) {
            throw new IllegalArgumentException("At least one institute must be assigned to the user");
        }
        
        // Check if user already exists
        if (userRepository.existsByEmailAndClientId(request.getEmail(), clientId)) {
            throw new IllegalArgumentException("User already exists with this email in this tenant");
        }
        
        // Handle password generation
        String password = request.getPassword();
        boolean passwordResetRequired = false;
        if (request.getAutoGeneratePassword() != null && request.getAutoGeneratePassword()) {
            password = generatePasswordFromUserInfo(request.getName(), request.getEmail());
            passwordResetRequired = true;
        } else if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required when autoGeneratePassword is false");
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
                    passwordEncoder.encode(password),
                    request.getName(),
                    request.getPhone(),
                    role
                );
                user.setActive(request.getActive() != null ? request.getActive() : true);
                user.setPasswordResetRequired(passwordResetRequired);
                user.setCreatedAt(OffsetDateTime.now());
                
                user = userRepository.save(user);
                
                // Create institute associations
                assignInstitutesToUser(user.getId(), request.getInstituteIds());
                
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Handle duplicate key violation (ID collision)
                attempts++;
                if (attempts >= maxRetries) {
                    // Check if user was actually created (by email)
                    var existingUser = userRepository.findByEmailAndClientId(request.getEmail(), clientId);
                    if (existingUser.isPresent()) {
                        user = existingUser.get();
                        // Ensure institutes are assigned
                        assignInstitutesToUser(user.getId(), request.getInstituteIds());
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
    
    @Transactional
    public UserDTO updateUser(String id, UpdateUserRequest request) {
        // Load user and validate access
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
        
        // Get current user to check permissions
        User currentUser = getCurrentUser();
        boolean isCurrentUserSystemAdmin = currentUser != null && currentUser.getRole() == User.Role.SYSTEM_ADMIN;
        
        User.Role newRole = User.Role.valueOf(request.getRole().toUpperCase());
        
        // SECURITY: Only SYSTEM_ADMIN can change role to SYSTEM_ADMIN
        if (newRole == User.Role.SYSTEM_ADMIN && user.getRole() != User.Role.SYSTEM_ADMIN) {
            if (!isCurrentUserSystemAdmin) {
                throw new IllegalArgumentException("SYSTEM_ADMIN role can only be assigned by existing SYSTEM_ADMIN users.");
            }
        }
        
        // SECURITY: Only SYSTEM_ADMIN can change SYSTEM_ADMIN users
        if (user.getRole() == User.Role.SYSTEM_ADMIN && !isCurrentUserSystemAdmin) {
            throw new IllegalArgumentException("SYSTEM_ADMIN users can only be modified by existing SYSTEM_ADMIN users.");
        }
        
        // Validate email uniqueness if email is being changed
        if (!user.getEmail().equals(request.getEmail())) {
            UUID clientId = user.getClientId();
            if (clientId != null) {
                // For tenant users, check uniqueness within tenant
                if (userRepository.existsByEmailAndClientId(request.getEmail(), clientId) &&
                    !userRepository.findByEmailAndClientId(request.getEmail(), clientId).get().getId().equals(id)) {
                    throw new IllegalArgumentException("User already exists with this email in this tenant");
                }
            } else {
                // For SYSTEM_ADMIN users, check global uniqueness
                var existingUser = userRepository.findByEmailAndRoleAndActiveTrue(request.getEmail(), User.Role.SYSTEM_ADMIN);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(id)) {
                    throw new IllegalArgumentException("SYSTEM_ADMIN user already exists with this email");
                }
            }
        }
        
        // Update user fields
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setRole(newRole);
        user.setActive(request.getActive() != null ? request.getActive() : true);
        
        // Handle role change from SYSTEM_ADMIN to non-SYSTEM_ADMIN
        if (user.getRole() != User.Role.SYSTEM_ADMIN && user.getClientId() == null) {
            // If changing from SYSTEM_ADMIN, we need a tenant context
            if (clientIdStr == null || "SYSTEM".equals(clientIdStr) || "PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                throw new IllegalArgumentException("Cannot change SYSTEM_ADMIN to non-SYSTEM_ADMIN without tenant context");
            }
            UUID clientId = UUID.fromString(clientIdStr);
            user.setClientId(clientId);
        }
        
        // Handle role change to SYSTEM_ADMIN
        if (user.getRole() == User.Role.SYSTEM_ADMIN) {
            user.setClientId(null);
            // Clear institute associations for SYSTEM_ADMIN
            userInstituteRepository.deleteByUserId(user.getId());
        } else {
            // For non-SYSTEM_ADMIN users, validate and update institutes
            if (request.getInstituteIds() == null || request.getInstituteIds().isEmpty()) {
                throw new IllegalArgumentException("At least one institute must be assigned to the user");
            }
            assignInstitutesToUser(user.getId(), request.getInstituteIds());
        }
        
        user = userRepository.save(user);
        return toDTO(user);
    }
    
    private UserDTO createSystemAdminUser(CreateUserRequest request) {
        // Check if user already exists (SYSTEM_ADMIN users have unique email globally)
        var existingUser = userRepository.findByEmailAndRoleAndActiveTrue(request.getEmail(), User.Role.SYSTEM_ADMIN);
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("SYSTEM_ADMIN user already exists with this email");
        }
        
        User user = null;
        int maxRetries = 3;
        int attempts = 0;
        
        // Handle password generation for SYSTEM_ADMIN
        String password = request.getPassword();
        boolean passwordResetRequired = false;
        if (request.getAutoGeneratePassword() != null && request.getAutoGeneratePassword()) {
            password = generatePasswordFromUserInfo(request.getName(), request.getEmail());
            passwordResetRequired = true;
        } else if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required when autoGeneratePassword is false");
        }
        
        while (user == null && attempts < maxRetries) {
            try {
                String userId = UlidGenerator.nextUlid();
                user = new User(
                    userId,
                    null, // SYSTEM_ADMIN has no clientId
                    request.getEmail(),
                    passwordEncoder.encode(password),
                    request.getName(),
                    request.getPhone(),
                    User.Role.SYSTEM_ADMIN
                );
                user.setActive(request.getActive() != null ? request.getActive() : true);
                user.setPasswordResetRequired(passwordResetRequired);
                user.setCreatedAt(OffsetDateTime.now());
                
                user = userRepository.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                attempts++;
                if (attempts >= maxRetries) {
                    var existing = userRepository.findByEmailAndRoleAndActiveTrue(request.getEmail(), User.Role.SYSTEM_ADMIN);
                    if (existing.isPresent()) {
                        user = existing.get();
                        break;
                    }
                    throw new IllegalArgumentException("Failed to create SYSTEM_ADMIN user after multiple attempts. Please try again.", e);
                }
                user = null;
            }
        }
        
        if (user == null) {
            throw new IllegalArgumentException("Failed to create SYSTEM_ADMIN user. Please try again.");
        }
        return toDTO(user);
    }
    
    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                log.warn("No authentication found in SecurityContext");
                return null;
            }
            
            String email = authentication.getName();
            if (email == null || email.isBlank()) {
                log.warn("Email is null or blank in authentication");
                return null;
            }
            
            String clientIdStr = TenantContext.getClientId();
            
            // Try to find user by email and tenant
            if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
                try {
                    UUID clientId = UUID.fromString(clientIdStr);
                    var user = userRepository.findByEmailAndClientId(email, clientId);
                    if (user.isPresent()) {
                        return user.get();
                    }
                    log.warn("User not found with email: {} and clientId: {}", email, clientId);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid clientId format: {}", clientIdStr);
                }
            }
            
            // Try SYSTEM_ADMIN lookup
            var systemAdmin = userRepository.findByEmailAndRoleAndActiveTrue(email, User.Role.SYSTEM_ADMIN);
            if (systemAdmin.isPresent()) {
                log.debug("Found SYSTEM_ADMIN user with email: {}", email);
                return systemAdmin.get();
            }
            
            // Log detailed information for debugging
            var usersByEmail = userRepository.findByEmailAndActiveTrue(email);
            if (!usersByEmail.isEmpty()) {
                log.warn("User with email {} exists but not found for tenant {} or as SYSTEM_ADMIN. Found {} user(s) with this email in other tenants.", 
                    email, clientIdStr, usersByEmail.size());
            } else {
                log.warn("User not found with email: {} (checked tenant: {}, SYSTEM_ADMIN). User may not exist or be inactive.", email, clientIdStr);
            }
            
        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage(), e);
        }
        return null;
    }
    
    @Transactional
    public void assignInstitutesToUser(String userId, List<String> instituteIds) {
        // Remove existing associations
        userInstituteRepository.deleteByUserId(userId);
        
        // Create new associations
        for (String instituteId : instituteIds) {
            if (instituteId != null && !instituteId.trim().isEmpty()) {
                UserInstitute userInstitute = new UserInstitute(userId, instituteId.trim());
                userInstituteRepository.save(userInstitute);
            }
        }
    }
    
    @Transactional(readOnly = true)
    public List<String> getUserInstitutes(String userId) {
        return userInstituteRepository.findByUserId(userId).stream()
            .map(UserInstitute::getInstituteId)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public UserDTO getCurrentUserProfile() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication != null ? authentication.getName() : "unknown";
            String clientIdStr = TenantContext.getClientId();
            throw new IllegalArgumentException(
                String.format("User not found. Email: %s, Tenant: %s. Please ensure you are authenticated with a valid token.", 
                    email, clientIdStr != null ? clientIdStr : "not set"));
        }
        return toDTO(currentUser);
    }
    
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new IllegalArgumentException("User not found");
        }
        
        // Verify current password
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        // Update password and clear password reset requirement
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        currentUser.setPasswordResetRequired(false);
        userRepository.save(currentUser);
    }
    
    /**
     * Generates a predictable password from email:
     * - First 3 characters of email
     * - @ symbol
     * - Last 5 characters of email
     * Format: abc@xyz.com (first 3 chars + @ + last 5 chars)
     */
    private String generatePasswordFromUserInfo(String name, String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required for password generation");
        }
        
        String trimmedEmail = email.trim();
        StringBuilder password = new StringBuilder();
        
        // First 3 characters of email
        if (trimmedEmail.length() >= 3) {
            password.append(trimmedEmail.substring(0, 3));
        } else {
            // If email is shorter than 3 characters, use the entire email
            password.append(trimmedEmail);
        }
        
        // Add @ symbol
        password.append("@");
        
        // Last 5 characters of email
        if (trimmedEmail.length() >= 5) {
            password.append(trimmedEmail.substring(trimmedEmail.length() - 5));
        } else {
            // If email is shorter than 5 characters, use the entire email
            password.append(trimmedEmail);
        }
        
        return password.toString();
    }
    
    private UserDTO toDTO(User user) {
        List<String> instituteIds = getUserInstitutes(user.getId());
        UserDTO dto = new UserDTO(
            user.getId(),
            user.getClientId(),
            user.getEmail(),
            user.getName(),
            user.getPhone(),
            user.getRole().name(),
            user.getActive(),
            user.getPasswordResetRequired() != null ? user.getPasswordResetRequired() : false,
            instituteIds,
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
        
        // If user is a STUDENT, fetch class and section info
        if (user.getRole() == User.Role.STUDENT && user.getClientId() != null) {
            try {
                StudentClassSectionInfo info = getStudentClassSectionInfo(user.getId());
                if (info != null) {
                    dto.setClassId(info.getClassId());
                    dto.setClassName(info.getClassName());
                    dto.setSectionId(info.getSectionId());
                    dto.setSectionName(info.getSectionName());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch class/section info for student {}: {}", user.getId(), e.getMessage());
                // Continue without class/section info rather than failing
            }
        }
        
        return dto;
    }
    
    /**
     * Inner class to deserialize student class/section info from student service
     */
    private static class StudentClassSectionInfo {
        private String classId;
        private String className;
        private String sectionId;
        private String sectionName;
        
        public String getClassId() { return classId; }
        public void setClassId(String classId) { this.classId = classId; }
        
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        
        public String getSectionId() { return sectionId; }
        public void setSectionId(String sectionId) { this.sectionId = sectionId; }
        
        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    }
    
    /**
     * Fetch student's class and section info from student service
     */
    private StudentClassSectionInfo getStudentClassSectionInfo(String studentId) {
        try {
            String url = gatewayUrl + "/api/students/" + studentId + "/class-section";
            log.debug("Fetching class/section info for student {} from URL: {}", studentId, url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Forward the Authorization header from the current request
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    headers.set("Authorization", authHeader);
                    log.debug("Forwarding Authorization header to student service");
                } else {
                    log.warn("No Authorization header found in current request, student service call may fail");
                }
            } else {
                log.warn("No request context available, cannot forward Authorization header");
            }
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<StudentClassSectionInfo> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                StudentClassSectionInfo.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                if (response.getStatusCode().value() == 204 || response.getBody() == null) {
                    log.debug("Student {} has no class/section association (204 No Content)", studentId);
                    return null;
                }
                log.debug("Successfully fetched class/section info for student {}: class={}, section={}", 
                    studentId, response.getBody().getClassName(), response.getBody().getSectionName());
                return response.getBody();
            } else {
                log.warn("Unexpected status code {} when fetching class/section info for student {}", 
                    response.getStatusCode(), studentId);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 204) {
                log.debug("Student {} has no class/section association (204 No Content)", studentId);
            } else {
                log.warn("HTTP error fetching class/section info for student {}: {} - {}", 
                    studentId, e.getStatusCode(), e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Error fetching class/section info for student {}: {}", studentId, e.getMessage(), e);
        }
        return null;
    }
}

