package com.datagami.edudron.identity.web;

import com.datagami.edudron.identity.dto.ChangePasswordRequest;
import com.datagami.edudron.identity.dto.CreateUserRequest;
import com.datagami.edudron.identity.dto.PasswordResetResponse;
import com.datagami.edudron.identity.dto.UpdateUserRequest;
import com.datagami.edudron.identity.dto.UserDTO;
import com.datagami.edudron.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/idp/users")
@Tag(name = "User Management", description = "User management endpoints")
public class UserController {
    
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @GetMapping
    @Operation(summary = "List users", description = "Get all users. SYSTEM_ADMIN sees all users, others see only their tenant's users.")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/paginated")
    @Operation(summary = "List users (paginated)", description = "Get paginated users. SYSTEM_ADMIN sees all users, others see only their tenant's users.")
    public ResponseEntity<Page<UserDTO>> getAllUsersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size);
        log.info("GET /idp/users/paginated - Filters: role={}, email={}, search={}, page={}, size={}", 
            role, email, search, page, size);
        Page<UserDTO> users = userService.getAllUsersPaginated(pageable, role, email, search);
        log.info("GET /idp/users/paginated - Returning {} users (total: {}, pages: {})", 
            users.getNumberOfElements(), users.getTotalElements(), users.getTotalPages());
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user", description = "Get user details by ID")
    public ResponseEntity<UserDTO> getUser(@PathVariable String id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/role/{role}")
    @Operation(summary = "List users by role", description = "Get all users with a specific role for the current tenant")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable String role) {
        List<UserDTO> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/role/{role}/paginated")
    @Operation(summary = "List users by role (paginated)", description = "Get paginated users with a specific role for the current tenant")
    public ResponseEntity<Page<UserDTO>> getUsersByRolePaginated(
            @PathVariable String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size);
        log.info("GET /idp/users/role/{}/paginated - Filters: email={}, search={}, page={}, size={}", 
            role, email, search, page, size);
        Page<UserDTO> users = userService.getUsersByRolePaginated(role, pageable, email, search);
        log.info("GET /idp/users/role/{}/paginated - Returning {} users (total: {}, pages: {})", 
            role, users.getNumberOfElements(), users.getTotalElements(), users.getTotalPages());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/role/{role}/count")
    @Operation(summary = "Count users by role", description = "Get user count by role. Defaults to counting only active users.")
    public ResponseEntity<Long> countUsersByRole(
            @PathVariable String role,
            @RequestParam(name = "active", defaultValue = "true") boolean activeOnly
    ) {
        long count = userService.countUsersByRole(role, activeOnly);
        return ResponseEntity.ok(count);
    }
    
    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user. SYSTEM_ADMIN can only be created by existing SYSTEM_ADMIN users. Non-SYSTEM_ADMIN users must have at least one institute assigned.")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update user details. SYSTEM_ADMIN users can only be modified by existing SYSTEM_ADMIN users. Non-SYSTEM_ADMIN users must have at least one institute assigned.")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserDTO user = userService.updateUser(id, request);
        return ResponseEntity.ok(user);
    }
    
    @PutMapping("/{id}/institutes")
    @Operation(summary = "Update user institutes", description = "Update institute associations for a user")
    public ResponseEntity<UserDTO> updateUserInstitutes(
            @PathVariable String id,
            @RequestBody List<String> instituteIds) {
        userService.assignInstitutesToUser(id, instituteIds);
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/by-email")
    @Operation(summary = "Find user by email", description = "Find a user by email address within the current tenant context")
    public ResponseEntity<UserDTO> getUserByEmail(@RequestParam String email) {
        log.debug("GET /by-email - Received email parameter: '{}' (length: {})", email, email != null ? email.length() : 0);
        UserDTO user = userService.getUserByEmail(email);
        if (user == null) {
            log.debug("GET /by-email - User not found for email: '{}'", email);
            return ResponseEntity.notFound().build();
        }
        log.debug("GET /by-email - Found user with id: {} for email: '{}'", user.getId(), email);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Get the profile of the currently authenticated user")
    public ResponseEntity<UserDTO> getCurrentUserProfile() {
        UserDTO user = userService.getCurrentUserProfile();
        return ResponseEntity.ok(user);
    }
    
    @PutMapping("/me/password")
    @Operation(summary = "Change password", description = "Change the password of the currently authenticated user")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reset user password (admin)", description = "Reset a user's password. Only SYSTEM_ADMIN and TENANT_ADMIN can perform this action. Returns a temporary password that the admin should share with the user. The user will be required to change their password on next login.")
    public ResponseEntity<PasswordResetResponse> resetPassword(@PathVariable String id) {
        log.info("POST /idp/users/{}/reset-password - Admin password reset requested", id);
        PasswordResetResponse response = userService.adminResetPassword(id);
        log.info("POST /idp/users/{}/reset-password - Password reset successful", id);
        return ResponseEntity.ok(response);
    }
}

